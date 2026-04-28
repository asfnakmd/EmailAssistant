package com.emailassistant.graph;

import com.emailassistant.graph.core.Graph;
import com.emailassistant.graph.core.GraphBuilder;
import com.emailassistant.memory.MemoryStoreService;
import com.emailassistant.prompts.PromptTemplates;
import com.emailassistant.state.AgentState;
import com.emailassistant.state.ChatMessage;
import com.emailassistant.state.RouterSchema;
import com.emailassistant.state.ToolCall;
import com.emailassistant.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MemoryEnabledGraph — 记忆增强工作流图。
 *
 * <p>在基础分类工作流的基础上增加记忆系统，使 AI 代理能够记住
 * 用户的历史偏好并在后续交互中应用。例如，用户曾表示"来自老板的
 * 邮件要立即回复"，则后续来自老板的邮件会自动优先处理。
 *
 * <h3>流程</h3>
 * <pre>
 * loadMemory → triage → llmCall → saveMemory → END
 *                                       ↑__________|
 *                                       （如有工具调用则循环）
 * </pre>
 *
 * <h3>新增节点</h3>
 * <ul>
 *   <li><b>loadMemory</b> — 工作流开始时加载用户的历史记忆</li>
 *   <li><b>saveMemory</b> — 工作流结束时从交互中提取并持久化新的偏好</li>
 * </ul>
 *
 * <p>映射关系：Python email_assistant_hitl_memory.py → Java MemoryEnabledGraph
 */
@Component
public class MemoryEnabledGraph {

    private static final Logger log = LoggerFactory.getLogger(MemoryEnabledGraph.class);

    private final ChatClient chatClient;
    private final ToolRegistry toolRegistry;
    private final MemoryStoreService memoryStore;

    /** 默认用户 ID，生产环境中应从认证信息中提取 */
    private static final String DEFAULT_USER_ID = "default_user";

    public MemoryEnabledGraph(ChatClient chatClient, ToolRegistry toolRegistry,
                              MemoryStoreService memoryStore) {
        this.chatClient = chatClient;
        this.toolRegistry = toolRegistry;
        this.memoryStore = memoryStore;
    }

    /**
     * 构建记忆增强工作流图。
     *
     * <p>在基础 triage→llmCall→toolCall 循环的基础上，前后增加了
     * loadMemory 和 saveMemory 节点。
     *
     * @return 配置完成的 Graph&lt;AgentState&gt;
     */
    public Graph<AgentState> buildMemoryEnabledGraph() {
        return GraphBuilder.<AgentState>create()
                .addNode("loadMemory", this::loadMemoryNode)
                .addNode("triage", this::triageNode)
                .addNode("llmCall", this::llmCallNode)
                .addNode("toolCall", this::toolCallNode)
                .addNode("saveMemory", this::saveMemoryNode)
                .addEdge("loadMemory", "triage")
                .addConditionalEdge("triage", this::routeFromTriage)
                .addConditionalEdge("llmCall", this::shouldContinue)
                .addEdge("toolCall", "llmCall")
                .addEdge("saveMemory", Graph.END)
                .setEntryPoint("loadMemory")
                .build();
    }

    // ==================== 节点方法 ====================

    /**
     * 加载记忆节点。
     *
     * <p>从 {@link MemoryStoreService} 中检索当前用户的历史偏好记忆，
     * 将检索到的记忆注入到 state 的 preferences 字段中，
     * 供后续节点（特别是 llmCall 节点）作为上下文参考。
     *
     * @param state 当前状态
     * @return 加载了用户偏好的新状态
     */
    AgentState loadMemoryNode(AgentState state) {
        log.info("▶ loadMemoryNode：加载用户记忆...");

        // 从邮件内容中提取用户标识（简化：使用默认用户）
        String userId = extractUserId(state.emailInput());

        // 搜索用户的所有偏好记忆
        List<String> memories = memoryStore.searchMemories(userId, "preference", "");

        if (!memories.isEmpty()) {
            log.info("  加载了 {} 条记忆", memories.size());

            // 将记忆信息作为 system 消息添加到对话历史
            StringBuilder memoryContext = new StringBuilder("以下是与该用户相关的历史偏好：\n");
            for (int i = 0; i < memories.size(); i++) {
                memoryContext.append(i + 1).append(". ").append(memories.get(i)).append("\n");
            }

            // 注入记忆到 state — 作为 system 消息方便 LLM 读取
            return state.addMessage(ChatMessage.system(memoryContext.toString()));
        }

        log.info("  未找到历史记忆");
        return state;
    }

    /**
     * 邮件分类节点。
     *
     * <p>使用带记忆增强的 System Prompt，让 LLM 在分类时参考用户偏好。
     * 如果 state 中有记忆相关的 system 消息，分类 prompt 会将其纳入考量。
     */
    AgentState triageNode(AgentState state) {
        log.info("▶ [MEM] triageNode：分类邮件（带记忆）...");
        try {
            // 提取记忆上下文（如果有）
            String memoryContext = state.messages().stream()
                    .filter(m -> "system".equals(m.role()))
                    .map(ChatMessage::content)
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");

            String systemPrompt = memoryContext.isBlank()
                    ? PromptTemplates.TRIAGE_SYSTEM
                    : PromptTemplates.TRIAGE_WITH_MEMORY_SYSTEM
                            .replace("{user_preferences}", memoryContext);

            RouterSchema result = chatClient.prompt()
                    .system(systemPrompt)
                    .user("请对以下邮件进行分类：\n\n" + state.emailInput())
                    .call()
                    .entity(RouterSchema.class);

            log.info("  triage 完成：{}", result.classification());
            return state.withClassificationDecision(result.classification());
        } catch (Exception e) {
            log.error("  triage 失败", e);
            return state.withClassificationDecision(AgentState.Classification.NOTIFY);
        }
    }

    /**
     * LLM 调用节点。
     *
     * <p>与基础版的 llmCallNode 逻辑相同，但由于 state 中可能包含
     * 记忆相关的 system 消息和历史偏好，LLM 的决策会受到记忆影响。
     */
    AgentState llmCallNode(AgentState state) {
        log.info("▶ [MEM] llmCallNode：调用 LLM（带记忆上下文）...");
        try {
            var chatResponse = chatClient.prompt()
                    .system(PromptTemplates.EMAIL_WRITER_SYSTEM)
                    .messages(state.messages().stream()
                            .map(this::toSpringAiMessage)
                            .toList())
                    .call()
                    .chatResponse();

            String content = "";
            List<ToolCall> toolCalls = List.of();

            if (chatResponse != null) {
                var generation = chatResponse.getResult();
                if (generation != null) {
                    AssistantMessage output = generation.getOutput();
                    content = output.getText() != null ? output.getText() : "";

                    var aiToolCalls = output.getToolCalls();
                    if (aiToolCalls != null) {
                        toolCalls = aiToolCalls.stream()
                                .map(tc -> new ToolCall(
                                        tc.id() != null ? tc.id() : "tc_" + System.nanoTime(),
                                        tc.name(),
                                        ToolCallUtils.argsFromJson(tc.arguments())))
                                .toList();
                    }
                }
            }

            return state.addMessage(ChatMessage.assistant(content, toolCalls));
        } catch (Exception e) {
            log.error("  llmCallNode 失败", e);
            return state.addMessage(ChatMessage.assistant("处理错误：" + e.getMessage()));
        }
    }

    /**
     * 工具执行节点。逻辑与基础版相同。
     */
    AgentState toolCallNode(AgentState state) {
        log.info("▶ [MEM] toolCallNode：执行工具...");
        List<ChatMessage> messages = state.messages();
        if (messages.isEmpty()) return state;

        ChatMessage lastMessage = messages.get(messages.size() - 1);
        List<ToolCall> toolCalls = lastMessage.toolCalls();

        if (toolCalls.isEmpty()) return state;

        AgentState currentState = state;
        for (ToolCall tc : toolCalls) {
            try {
                var tool = toolRegistry.getTool(tc.name());
                String result = tool.execute(tc.args());
                currentState = currentState.addMessage(ChatMessage.tool(result, tc));
            } catch (Exception e) {
                currentState = currentState.addMessage(
                        ChatMessage.tool("错误：" + e.getMessage(), tc));
            }
        }
        return currentState;
    }

    /**
     * 保存记忆节点。
     *
     * <p>工作流结束时调用 LLM 从本次对话中提取用户的新偏好，
     * 并将提取到的偏好保存到 {@link MemoryStoreService}。
     *
     * <p>使用 LLM 提取偏好的原因：
     * <ul>
     *   <li>比规则引擎更准确，能理解上下文中的隐含偏好</li>
     *   <li>可以提取复杂的条件规则（如"来自财务的邮件抄送主管"）</li>
     * </ul>
     *
     * @param state 当前状态
     * @return 原状态（不修改）
     */
    AgentState saveMemoryNode(AgentState state) {
        log.info("▶ saveMemoryNode：提取并保存记忆...");

        try {
            // 将对话历史整理成 LLM 可分析的文本
            StringBuilder conversationLog = new StringBuilder();
            for (ChatMessage msg : state.messages()) {
                conversationLog.append("[").append(msg.role()).append("] ")
                        .append(msg.content()).append("\n");
                if (msg.hasToolCalls()) {
                    for (ToolCall tc : msg.toolCalls()) {
                        conversationLog.append("  → 工具调用: ").append(tc.name())
                                .append("(").append(tc.args()).append(")\n");
                    }
                }
            }

            // 调用 LLM 提取偏好
            String preferenceResult = chatClient.prompt()
                    .system(PromptTemplates.PREFERENCE_EXTRACTION)
                    .user("以下是与用户的交互记录，请提取偏好信息：\n\n" + conversationLog.toString())
                    .call()
                    .content();

            // 解析并保存偏好
            if (preferenceResult != null && !preferenceResult.isBlank()
                    && !preferenceResult.contains("\"preferences\": []")
                    && !preferenceResult.equals("{}")) {

                String userId = extractUserId(state.emailInput());

                // 保存提取到的偏好（简化处理：直接保存原始文本）
                memoryStore.saveMemory(userId, "preference",
                        "pref_" + System.currentTimeMillis(), preferenceResult);

                log.info("  已保存新的偏好记忆");
            } else {
                log.info("  未发现新的偏好");
            }

        } catch (Exception e) {
            log.warn("  保存记忆异常（非关键错误）", e);
        }

        // 在所有节点处理完后，跳转到 END 前，需要确保有路由出口
        return state;
    }

    // ==================== 路由方法 ====================

    /**
     * triage 节点后的条件路由。
     */
    String routeFromTriage(AgentState state) {
        if (state.classificationDecision() == AgentState.Classification.RESPOND) {
            return "llmCall";
        }
        // 对于 IGNORE/NOTIFY，仍然经过 saveMemory 再结束
        return "saveMemory";
    }

    /**
     * llmCall 节点后的条件路由。
     */
    String shouldContinue(AgentState state) {
        List<ChatMessage> messages = state.messages();
        if (messages.isEmpty()) return "saveMemory";

        ChatMessage lastMessage = messages.get(messages.size() - 1);
        List<ToolCall> toolCalls = lastMessage.toolCalls();

        if (toolCalls == null || toolCalls.isEmpty()) {
            return "saveMemory";
        }

        boolean hasDone = toolCalls.stream().anyMatch(tc -> "done".equals(tc.name()));
        if (hasDone) return "saveMemory";

        return "toolCall";
    }

    // ==================== 工具方法 ====================

    /**
     * 从邮件输入中提取用户标识。
     *
     * <p>尝试从邮件头部提取收件人地址作为用户标识。
     * 如果无法提取，返回默认用户 ID。
     *
     * @param emailInput 原始邮件输入
     * @return 用户标识
     */
    private String extractUserId(String emailInput) {
        if (emailInput == null || emailInput.isBlank()) {
            return DEFAULT_USER_ID;
        }
        // 尝试提取 To: 字段
        var matcher = java.util.regex.Pattern.compile(
                "(?m)^To:\\s*(.+)$").matcher(emailInput);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return DEFAULT_USER_ID;
    }

    // ==================== 消息转换 ====================

    private org.springframework.ai.chat.messages.Message toSpringAiMessage(ChatMessage msg) {
        return switch (msg.role()) {
            case "user" -> new org.springframework.ai.chat.messages.UserMessage(msg.content());
            case "system" -> new org.springframework.ai.chat.messages.SystemMessage(msg.content());
            case "assistant" -> {
                if (msg.hasToolCalls()) {
                    var toolCalls = msg.toolCalls().stream()
                            .map(tc -> new AssistantMessage.ToolCall(
                                    tc.id(), "function", tc.name(),
                                    ToolCallUtils.argsToJson(tc.args())))
                            .toList();
                    yield new AssistantMessage(
                            msg.content(), java.util.Map.of(), toolCalls);
                }
                yield new AssistantMessage(msg.content());
            }
            case "tool" -> {
                var tc = msg.toolCalls().isEmpty() ? null : msg.toolCalls().get(0);
                if (tc != null) {
                    yield new org.springframework.ai.chat.messages.ToolResponseMessage(
                            java.util.List.of(
                                    new org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse(
                                            tc.id(), tc.name(), msg.content())));
                }
                yield new org.springframework.ai.chat.messages.ToolResponseMessage(
                        java.util.List.of());
            }
            default -> new org.springframework.ai.chat.messages.UserMessage(msg.content());
        };
    }
}
