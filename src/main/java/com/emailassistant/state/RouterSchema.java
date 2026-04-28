package com.emailassistant.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * RouterSchema — LLM 分类返回的结构化输出模型。
 *
 * <p>LLM 在 triage 阶段返回的 JSON 通过 Spring AI 的 {@code .entity()} 方法
 * 自动反序列化为本 Record 实例。包含推理过程和最终分类决策。
 *
 * <p>JSON 示例（LLM 返回格式）：
 * <pre>{@code
 * {
 *   "reasoning": "该邮件是客户咨询报价，需要回复",
 *   "classification": "RESPOND"
 * }
 * }</pre>
 */
public record RouterSchema(
        @NotBlank @JsonProperty("reasoning") String reasoning,
        @NotNull @JsonProperty("classification") AgentState.Classification classification
) {
    /**
     * 从字符串解析为 RouterSchema（兼容单字符串输入）。
     *
     * @param value 分类字符串（大小写不敏感）
     * @return RouterSchema 实例
     */
    public static RouterSchema fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        AgentState.Classification classification = switch (value.strip().toLowerCase()) {
            case "ignore" -> AgentState.Classification.IGNORE;
            case "respond" -> AgentState.Classification.RESPOND;
            case "notify" -> AgentState.Classification.NOTIFY;
            default -> throw new IllegalArgumentException(
                    "Unknown classification: '" + value + "'. Expected: ignore, respond, notify");
        };
        return new RouterSchema("Parsed from string: " + value, classification);
    }
}
