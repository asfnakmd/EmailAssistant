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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * HumanInLoopGraph — 人机交互工作流图。
 *
 * <p>在基础邮件助手图的基础上扩展人机交互（Human-in-the-Loop）能力。
 * 当 LLM 决定执行敏感操作（如发送邮件、安排会议）时，工作流会暂停
 * 并等待人工审批，审批通过后才继续执行。
 *
 * <h3>流程差异（相对于基础版）</h3>
 * <pre>
 * llmCall ──有敏感工具──→ humanReview ──通过──→ toolCall
 *    │                       │
 *    │                       └──拒绝──→ END（或重新生成）
 *    └──无敏感工具──→ toolCall（直接执行）
 * </pre>
 *
 * <p>审批流程：
 * <ol>
 *   <li>humanReviewNode 将待审批项通过 WebSocket 推送到前端</li>
 *   <li>前端展示审批界面，用户批准或拒绝</li>
 *   <li>审批结果通过 REST API 或 WebSocket 提交</li>
 *   <li>Controller 接收审批结果，恢复流程执行</li>
 * </ol>
 *
 * <p>映射关系：Python email_assistant_hitl.py → Java HumanInLoopGraph
 */
@Component
public class HumanInLoopGraph {

    private static final Logger log = LoggerFactory.getLogger(HumanInLoopGraph.class);

    private final ChatClient chatClient;
    private final ToolRegistry toolRegistry;

    /** Spring STOMP 消息推送模板，用于通过 WebSocket 推送审批请求 */
    private final SimpMessagingTemplate messagingTemplate;

    /** 需要人工审批的工具白名单，从 application.yml 的 app.approval-required-tools 读取 */
    private final Set<String> approvalRequiredTools;

    public HumanInLoopGraph(ChatClient chatClient, ToolRegistry toolRegistry,
                            SimpMessagingTemplate messagingTemplate,
                            @Value("${app.approval-required-tools:write_email,schedule_meeting}") String approvalTools) {
        this.chatClient = chatClient;
        this.toolRegistry = toolRegistry;
        this.messagingTemplate = messagingTemplate;
        this.approvalRequiredTools = Arrays.stream(approvalTools.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * 构建人机交互工作流图。
     *
     * <p>与基础版的区别：
     * <ul>
     *   <li>在 llmCall 和 toolCall 之间插入了 humanReview 节点</li>
     *   <li>llmCall 节点后使用 {@link #needsHumanReview} 判断是否需要审批</li>
     *   <li>引入敏感工具白名单 {@link #requiresHumanApproval(String)}</li>
     * </ul>
     *
     * @return 配置完成的 Graph&lt;AgentState&gt;
     */
    public Graph<AgentState> buildHumanInLoopGraph() {
        return GraphBuilder.<AgentState>create()
                .addNode("triage", this::triageNode)
                .addNode("llmCall", this::llmCallNode)
                .addNode("humanReview", this::humanReviewNode)
                .addNode("toolCall", this::toolCallNode)
                .addConditionalEdge("triage", this::routeFromTriage)
                .addConditionalEdge("llmCall", this::needsHumanReview)
                .addEdge("humanReview", "toolCall")
                .addConditionalEdge("toolCall", this::shouldContinueAfterTool)
                .setEntryPoint("triage")
                .build();
    }

    // ==================== 节点方法 ====================

    /**
     * 邮件分类节点。逻辑与 {@link EmailAssistantGraph#triageNode} 相同。
     */
    AgentState triageNode(AgentState state) {
        log.info("▶ [HITL] triageNode：正在分类邮件...");
        try {
            var result = chatClient.prompt()
                    .system(PromptTemplates.TRIAGE_SYSTEM)
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
     * LLM 调用节点。逻辑与 {@link EmailAssistantGraph#llmCallNode} 相同。
     */
    AgentState llmCallNode(AgentState state) {
        log.info("▶ [HITL] llmCallNode：调用 LLM...");
        try {
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
     * 人工审批节点。
     *
     * <p>拦截需要审批的工具调用，通过 WebSocket 通知前端等待用户决策。
     *
     * <p>实现方式：将 pending 中的工具调用通过 SimpMessagingTemplate
     * 推送到 /topic/agent/awaiting-approval 主题，前端收到后展示审批界面。
     * 审批结果通过 Controller 的 REST 端点提交。
     *
     * @param state 当前状态（最后一条消息应包含待审批的 toolCalls）
     * @return 添加了审批提示消息的新状态
     */
    AgentState humanReviewNode(AgentState state) {
        log.info("▶ [HITL] humanReviewNode：等待人工审批...");

        List<ChatMessage> messages = state.messages();
        if (messages.isEmpty()) {
            return state;
        }

        ChatMessage lastMessage = messages.get(messages.size() - 1);

        // 找出需要审批的工具调用
        List<ToolCall> pendingTools = lastMessage.toolCalls().stream()
                .filter(tc -> requiresHumanApproval(tc.name()))
                .toList();

        if (pendingTools.isEmpty()) {
            log.info("  无待审批工具");
            return state.addMessage(ChatMessage.system("无需人工审批，继续执行。"));
        }

        log.info("  待审批工具：{}", pendingTools.stream().map(ToolCall::name).toList());

        // 通过 WebSocket 推送待审批项到前端
        try {
            messagingTemplate.convertAndSend("/topic/agent/awaiting-approval",
                    Map.of(
                            "type", "approval_request",
                            "timestamp", System.currentTimeMillis(),
                            "pendingTools", pendingTools.stream()
                                    .map(tc -> Map.of(
                                            "id", tc.id(),
                                            "name", tc.name(),
                                            "args", tc.args()))
                                    .toList()));
            log.info("  已推送审批请求到 WebSocket");
        } catch (Exception e) {
            log.warn("  WebSocket 推送失败（前端可能未连接）", e);
        }

        // 添加提示消息
        return state.addMessage(ChatMessage.system(
                "以下操作需要人工审批：" + pendingTools.stream()
                        .map(tc -> tc.name() + "(" + tc.args() + ")")
                        .toList()));
    }

    /**
     * 工具执行节点。逻辑与 {@link EmailAssistantGraph#toolCallNode} 相同。
     */
    AgentState toolCallNode(AgentState state) {
        log.info("▶ [HITL] toolCallNode：执行工具...");
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

    // ==================== 路由方法 ====================

    /**
     * triage 节点后的条件路由。
     */
    String routeFromTriage(AgentState state) {
        if (state.classificationDecision() == AgentState.Classification.RESPOND) {
            return "llmCall";
        }
        return Graph.END;
    }

    /**
     * 判断 LLM 调用后是否需要人工审批。
     *
     * <p>检查最后一条消息中的工具调用，如果包含白名单中的敏感工具，
     * 则路由到 humanReview 节点，否则直接进入 toolCall 节点。
     *
     * @param state 当前状态
     * @return "humanReview" / "toolCall" / Graph.END
     */
    String needsHumanReview(AgentState state) {
        List<ChatMessage> messages = state.messages();
        if (messages.isEmpty()) return Graph.END;

        ChatMessage lastMessage = messages.get(messages.size() - 1);
        List<ToolCall> toolCalls = lastMessage.toolCalls();

        if (toolCalls == null || toolCalls.isEmpty()) {
            return Graph.END;
        }

        // 检查是否有需要审批的工具
        boolean needsReview = toolCalls.stream()
                .anyMatch(tc -> requiresHumanApproval(tc.name()));

        if (needsReview) {
            log.info("  需要人工审批 → humanReview");
            return "humanReview";
        }

        log.info("  无需审批 → toolCall");
        return "toolCall";
    }

    /**
     * toolCall 节点后的条件路由（复用基础版逻辑）。
     */
    String shouldContinueAfterTool(AgentState state) {
        List<ChatMessage> messages = state.messages();
        if (messages.isEmpty()) return Graph.END;

        ChatMessage lastMessage = messages.get(messages.size() - 1);
        List<ToolCall> toolCalls = lastMessage.toolCalls();

        if (toolCalls == null || toolCalls.isEmpty()) {
            // 检查上一轮 LLM 是否还有更多工具需要执行
            if (messages.size() >= 2) {
                ChatMessage prevMsg = messages.get(messages.size() - 2);
                if (prevMsg.hasToolCalls()) {
                    // 可能还有更多工具要执行，返回 LLM 继续
                    return "llmCall";
                }
            }
            return Graph.END;
        }

        // 检查 done 工具
        boolean hasDone = toolCalls.stream().anyMatch(tc -> "done".equals(tc.name()));
        if (hasDone) return Graph.END;

        return "llmCall";
    }

    // ==================== 工具审批 ====================

    /**
     * 检查指定工具是否需要人工审批。
     *
     * @param toolName 工具名
     * @return true 表示需要人工审批
     */
    boolean requiresHumanApproval(String toolName) {
        return approvalRequiredTools.contains(toolName);
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
