package com.emailassistant.evaluation;

import com.emailassistant.state.AgentState;
import com.emailassistant.state.ToolCall;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * EvaluationService — 评估与追踪服务。
 *
 * <p>负责两件事：
 * <ol>
 *   <li><b>LLM-as-Judge 评估</b>：使用 LLM 对 AI 生成的回复进行多维度质量评分</li>
 *   <li><b>OpenTelemetry 追踪</b>：记录工作流执行的链路追踪信息</li>
 * </ol>
 *
 * <p>映射关系：Python LangSmith + 自定义评估 → Java OpenTelemetry + EvaluationService
 */
@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

    private final ChatClient chatClient;
    private final Tracer tracer;

    /**
     * 构造器注入。
     *
     * @param chatClient Spring AI ChatClient（用于 LLM-as-Judge 评估）
     * @param tracer     OpenTelemetry Tracer（用于链路追踪）
     */
    public EvaluationService(ChatClient chatClient, Tracer tracer) {
        this.chatClient = chatClient;
        this.tracer = tracer;
    }

    // ==================== LLM-as-Judge 评估 ====================

    /**
     * 使用 LLM 对邮件回复进行质量评估。
     *
     * <p>评估维度：
     * <ul>
     *   <li>相关性 (1-5)：回复是否针对邮件内容</li>
     *   <li>专业性 (1-5)：语气和格式是否恰当</li>
     *   <li>完整性 (1-5)：是否回答了所有问题</li>
     * </ul>
     *
     * @param originalEmail 原始邮件内容
     * @param aiResponse    AI 生成的回复内容
     * @return 评估结果
     */
    public EvaluationResult evaluateResponse(String originalEmail, String aiResponse) {
        Span span = tracer.spanBuilder("evaluate_response")
                .setAttribute("assessment.type", "llm-as-judge")
                .startSpan();

        try {
            String evaluationPrompt = String.format("""
                    原始邮件：
                    ---
                    %s
                    ---

                    AI 回复：
                    ---
                    %s
                    ---

                    请从以下维度评分（1-5分）：
                    1. 相关性：回复是否针对邮件内容
                    2. 专业性：语气和格式是否专业得体
                    3. 完整性：是否回答了所有问题

                    请以 JSON 格式输出评分结果。
                    """, originalEmail, aiResponse);

            String resultJson = chatClient.prompt()
                    .system("你是一名邮件质量评审员。请对 AI 生成的邮件回复进行评分。评分标准：1 分最差，5 分最好。")
                    .user(evaluationPrompt)
                    .call()
                    .content();

            EvaluationResult result = parseEvaluation(resultJson);

            // 记录评估分数到 Span
            span.setAttribute("evaluation.relevance", result.relevance());
            span.setAttribute("evaluation.professionalism", result.professionalism());
            span.setAttribute("evaluation.completeness", result.completeness());
            span.setAttribute("evaluation.average", result.averageScore());

            log.info("评估完成：相关性={}，专业性={}，完整性={}，平均={}",
                    result.relevance(), result.professionalism(),
                    result.completeness(), result.averageScore());

            return result;

        } catch (Exception e) {
            span.recordException(e);
            log.error("评估过程异常", e);
            return EvaluationResult.failed("评估异常: " + e.getMessage());
        } finally {
            span.end();
        }
    }

    /**
     * 解析 LLM 返回的 JSON 字符串为 EvaluationResult。
     *
     * <p>使用简单的字符串解析（避免引入额外 JSON 库依赖），
     * 生产环境建议使用 Jackson ObjectMapper。
     */
    private EvaluationResult parseEvaluation(String json) {
        if (json == null || json.isBlank()) {
            return EvaluationResult.failed("LLM 返回为空");
        }

        try {
            // 简单解析：提取各字段值
            int relevance = extractInt(json, "relevance");
            int professionalism = extractInt(json, "professionalism");
            int completeness = extractInt(json, "completeness");
            String comments = extractString(json, "comments");

            return new EvaluationResult(
                    relevance, professionalism, completeness,
                    true, comments != null ? comments : "");
        } catch (Exception e) {
            log.warn("解析评估结果失败，原始内容: {}", json, e);
            return EvaluationResult.failed("解析失败: " + e.getMessage());
        }
    }

    /** 从 JSON 字符串中提取整型字段值 */
    private int extractInt(String json, String field) {
        String pattern = "\"" + field + "\"\\s*:\\s*(\\d+)";
        var matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 3; // 默认中等分数
    }

    /** 从 JSON 字符串中提取字符串字段值 */
    private String extractString(String json, String field) {
        String pattern = "\"" + field + "\"\\s*:\\s*\"([^\"]+)\"";
        var matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    // ==================== 工作流追踪 ====================

    /**
     * 记录 Agent 工作流的执行追踪。
     *
     * <p>创建 OpenTelemetry Span，记录以下信息：
     * <ul>
     *   <li>graph.name — 使用的图类型</li>
     *   <li>classification — 分类结果</li>
     *   <li>tool.calls.count — 工具调用次数</li>
     *   <li>message.count — 总消息数</li>
     * </ul>
     *
     * @param state     Agent 执行结束后的状态
     * @param graphName 图名称（如 "basic", "hitl", "memory"）
     */
    public void logAgentTrace(AgentState state, String graphName) {
        Span span = tracer.spanBuilder("agent_execution")
                .setAttribute("graph.name", graphName)
                .setAttribute("graph.type", "email_assistant")
                .startSpan();

        try {
            // ---- 记录分类决策 ----
            if (state.classificationDecision() != null) {
                span.setAttribute("classification.decision",
                        state.classificationDecision().name());
            }

            // ---- 统计工具调用 ----
            long toolCallCount = state.messages().stream()
                    .flatMap(m -> m.toolCalls().stream())
                    .count();
            span.setAttribute("tool.calls.count", toolCallCount);

            // ---- 记录对话轮次 ----
            span.setAttribute("message.count", state.messages().size());

            // ---- 记录工具调用详情 ----
            int toolIndex = 0;
            for (var msg : state.messages()) {
                for (ToolCall tc : msg.toolCalls()) {
                    span.addEvent("tool.executed", Attributes.builder()
                            .put("tool.name", tc.name())
                            .put("tool.call_id", tc.id())
                            .put("tool.index", (long) toolIndex++)
                            .build());
                }
            }

            span.addEvent("agent.execution.completed");

            log.debug("追踪记录完成：graph={}, classification={}, tools={}",
                    graphName, state.classificationDecision(), toolCallCount);

        } catch (Exception e) {
            span.recordException(e);
            log.error("记录追踪信息异常", e);
        } finally {
            span.end();
        }
    }
}
