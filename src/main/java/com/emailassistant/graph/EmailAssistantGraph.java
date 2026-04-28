package com.emailassistant.graph;

import com.emailassistant.graph.core.Graph;
import com.emailassistant.graph.core.GraphBuilder;
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

/**
 * EmailAssistantGraph — 基础邮件助手工作流图。
 *
 * <p>构建最基础的邮件处理工作流，包含三个核心节点：
 * <ol>
 *   <li><b>triage</b> — 邮件分类，判断应忽略/通知/回复</li>
 *   <li><b>llmCall</b> — LLM 调用，分析邮件内容并决定是否使用工具</li>
 *   <li><b>toolCall</b> — 工具执行，执行 LLM 请求的工具调用</li>
 * </ol>
 *
 * <h3>工作流流转</h3>
 * <pre>
 * triage ──IGNORE/NOTIFY──→ END
 *    │
 *    └──RESPOND──→ llmCall ──有 toolCall──→ toolCall
 *                           │                  │
 *                           └──无 toolCall      │
 *                               → END          │
 *                                              │
 *                              done 被调用 ────→ END
 *                              其他工具 ←──────→ llmCall (循环)
 * </pre>
 *
 * <p>映射关系：Python email_assistant.py → Java EmailAssistantGraph
 */
@Component
public class EmailAssistantGraph {

    private static final Logger log = LoggerFactory.getLogger(EmailAssistantGraph.class);

    /** Spring AI ChatClient，用于所有 LLM 调用 */
    private final ChatClient chatClient;

    /** 工具注册中心，用于在 toolCallNode 中查找并执行工具 */
    private final ToolRegistry toolRegistry;

    /**
     * 构造器注入。
     *
     * @param chatClient   Spring AI ChatClient
     * @param toolRegistry 工具注册中心
     */
    public EmailAssistantGraph(ChatClient chatClient, ToolRegistry toolRegistry) {
        this.chatClient = chatClient;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 构建并返回基础邮件助手工作流图。
     *
     * <p>每次调用返回新构建的 Graph 实例。
     *
     * @return 配置完成的 Graph&lt;AgentState&gt;
     */
    public Graph<AgentState> buildEmailAssistant() {
        return GraphBuilder.<AgentState>create()
                .addNode("triage", this::triageNode)
                .addNode("llmCall", this::llmCallNode)
                .addNode("toolCall", this::toolCallNode)
                .addConditionalEdge("triage", this::routeFromTriage)
                .addConditionalEdge("llmCall", this::shouldContinue)
                .addEdge("toolCall", "llmCall")
                .setEntryPoint("triage")
                .build();
    }

    // ==================== 节点方法 ====================

    /**
     * 邮件分类节点。
     *
     * <p>使用 LLM 对邮件内容进行三分类（ignore/respond/notify）。
     * 从 state 中读取邮件输入，构造分类 prompt，调用 LLM 获取结构化输出，
     * 将分类结果写入新的 state。
     *
     * @param state 当前状态（含 emailInput）
     * @return 更新了 classificationDecision 的新状态
     */
    AgentState triageNode(AgentState state) {
        log.info("▶ triageNode：正在分类邮件...");

        // 构造分类 prompt，让 LLM 返回结构化的分类结果
        String userPrompt = "请对以下邮件进行分类：\n\n" + state.emailInput();

        try {
            // 调用 LLM 获取结构化输出（自动反序列化为 RouterSchema）
            RouterSchema result = chatClient.prompt()
                    .system(PromptTemplates.TRIAGE_SYSTEM)
                    .user(userPrompt)
                    .call()
                    .entity(RouterSchema.class);

            log.info("  triage 完成：classification={}, reasoning={}",
                    result.classification(), result.reasoning());

            return state.withClassificationDecision(result.classification());

        } catch (Exception e) {
            log.error("  triage 调用 LLM 失败", e);
            // 出错时默认标记为 NOTIFY，避免遗漏重要邮件
            return state.withClassificationDecision(AgentState.Classification.NOTIFY);
        }
    }

    /**
     * LLM 调用节点。
     *
     * <p>将当前对话历史（含工具执行结果）发送给 LLM，
     * LLM 会思考下一步操作——是调用工具还是结束流程。
     *
     * <p>Spring AI 的 ChatClient 会自动将 {@code toolRegistry} 中的
     * 工具定义通过 Function Calling 机制发送给 LLM。
     *
     * @param state 当前状态（含 messages 历史）
     * @return 追加了 LLM 回复消息的新状态
     */
    AgentState llmCallNode(AgentState state) {
        log.info("▶ llmCallNode：调用 LLM...");

        try {
            // 将当前消息历史发送给 LLM，并绑定可用工具
            var chatResponse = chatClient.prompt()
                    .system(PromptTemplates.EMAIL_WRITER_SYSTEM)
                    .messages(state.messages().stream()
                            .map(this::toSpringAiMessage)
                            .toList())
                    .call()
                    .chatResponse();

            String content = "";
            java.util.List<ToolCall> toolCalls = java.util.List.of();

            if (chatResponse != null) {
                var generation = chatResponse.getResult();
                if (generation != null) {
                    AssistantMessage output = generation.getOutput();
                    content = output.getText() != null ? output.getText() : "";

                    // 提取 LLM 返回中的工具调用，将 JSON 参数字符串转为 Map
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

            log.info("  LLM 回复完成，toolCalls={}", toolCalls.size());

            // 将 LLM 回复包装为 ChatMessage 并追加到 state
            ChatMessage assistantMsg = ChatMessage.assistant(content, toolCalls);
            return state.addMessage(assistantMsg);

        } catch (Exception e) {
            log.error("  llmCallNode 调用 LLM 失败", e);
            // 出错时添加错误消息，让流程自然结束
            return state.addMessage(ChatMessage.assistant(
                    "抱歉，处理时遇到错误：" + e.getMessage()));
        }
    }

    /**
     * 工具执行节点。
     *
     * <p>从最后一条 LLM 消息中提取工具调用列表，
     * 通过 {@link ToolRegistry} 查找对应的 Tool 并执行，
     * 将每个工具的执行结果包装为 tool 角色的 ChatMessage 追加到 state。
     *
     * @param state 当前状态（最后一条消息应包含 toolCalls）
     * @return 追加了工具执行结果的新状态
     */
    AgentState toolCallNode(AgentState state) {
        log.info("▶ toolCallNode：执行工具调用...");

        // 获取最后一条消息（LLM 的回复，其中包含 toolCalls）
        java.util.List<ChatMessage> messages = state.messages();
        if (messages.isEmpty()) {
            log.warn("  messages 为空，跳过 toolCallNode");
            return state;
        }

        ChatMessage lastMessage = messages.get(messages.size() - 1);
        java.util.List<ToolCall> toolCalls = lastMessage.toolCalls();

        if (toolCalls.isEmpty()) {
            log.info("  没有工具调用");
            return state;
        }

        // 逐一执行每个工具调用
        AgentState currentState = state;
        for (ToolCall tc : toolCalls) {
            log.info("  执行工具：{} (id={})", tc.name(), tc.id());

            try {
                // 从注册中心查找工具
                var tool = toolRegistry.getTool(tc.name());
                String result = tool.execute(tc.args());

                log.info("  工具执行结果：{}", result);

                // 将工具执行结果包装为 tool 角色的消息
                currentState = currentState.addMessage(ChatMessage.tool(result, tc));

            } catch (Exception e) {
                log.error("  工具执行异常：{}", tc.name(), e);
                String errorMsg = "错误：工具「" + tc.name() + "」执行失败 — " + e.getMessage();
                currentState = currentState.addMessage(ChatMessage.tool(errorMsg, tc));
            }
        }

        return currentState;
    }

    // ==================== 路由方法 ====================

    /**
     * triage 节点后的条件路由。
     *
     * <p>根据分类结果决定下一步：
     * <ul>
     *   <li>RESPOND → 进入 llmCall 节点（生成回复）</li>
     *   <li>IGNORE / NOTIFY → 结束工作流</li>
     * </ul>
     *
     * @param state 当前状态（含 classificationDecision）
     * @return 下一个节点名或 Graph.END
     */
    String routeFromTriage(AgentState state) {
        AgentState.Classification decision = state.classificationDecision();

        if (decision == AgentState.Classification.RESPOND) {
            log.info("  triage → RESPOND，进入 llmCall");
            return "llmCall";
        }

        log.info("  triage → {}，结束流程", decision);
        return Graph.END;
    }

    /**
     * llmCall 节点后的条件路由。
     *
     * <p>根据 LLM 是否调用了工具来决定：
     * <ul>
     *   <li>有 toolCall 且不含 done → 进入 toolCall 节点</li>
     *   <li>有 toolCall 且含 done → 结束流程</li>
     *   <li>无 toolCall → 结束流程</li>
     * </ul>
     *
     * @param state 当前状态
     * @return 下一个节点名或 Graph.END
     */
    String shouldContinue(AgentState state) {
        java.util.List<ChatMessage> messages = state.messages();
        if (messages.isEmpty()) {
            return Graph.END;
        }

        ChatMessage lastMessage = messages.get(messages.size() - 1);
        java.util.List<ToolCall> toolCalls = lastMessage.toolCalls();

        if (toolCalls == null || toolCalls.isEmpty()) {
            // LLM 没有调用任何工具 → 流程结束
            log.info("  LLM 未调用工具，流程结束");
            return Graph.END;
        }

        // 检查是否调用了 done 工具
        boolean hasDone = toolCalls.stream().anyMatch(tc -> "done".equals(tc.name()));
        if (hasDone) {
            log.info("  done 工具被调用，流程结束");
            return Graph.END;
        }

        // 还有工具需要执行 → 进入 toolCall 节点
        log.info("  LLM 调用了 {} 个工具，进入 toolCall", toolCalls.size());
        return "toolCall";
    }

    // ==================== 消息转换 ====================

    /**
     * 将项目的 ChatMessage 转换为 Spring AI 的 Message 对象。
     *
     * <p>Spring AI ChatClient 的 .messages() 方法接受 Spring AI
     * 的 Message 类型，因此需要在两者之间做适配转换。
     */
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
                yield new org.springframework.ai.chat.messages.AssistantMessage(
                        msg.content());
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
