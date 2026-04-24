package com.emailassistant.state;

/*
 * ============================================================================
 * RouterSchema — 路由决策的数据模型
 * ============================================================================
 *
 * 功能描述:
 *   定义 LLM 进行邮件分类时返回的结构化输出格式。LLM 调用时会根据此 Schema
 *   返回 classification 推理结果和分类决策。这是 LangGraph 中结构化输出
 *   (structured output / function calling) 的 Java 对应实现。
 *
 *   映射关系: Python schemas.py 中的 Router 模型 → Java Record
 *
 * 编码建议:
 *   1. 使用 Java Record + Jackson 注解实现序列化:
 *        @JsonCreator 和 @JsonProperty 注解让 Jackson 正确反序列化 Record
 *   2. 字段设计:
 *      - reasoning: @NotBlank String — LLM 的分类推理过程（用于可解释性）
 *      - classification: @NotNull Classification — 最终的分类决策
 *   3. 定义 Classification 枚举（同 AgentState 中的一致或引用其枚举）:
 *        IGNORE, RESPOND, NOTIFY
 *   4. 提供一个静态 fromString 方法来解析 LLM 返回的字符串:
 *        public static Classification fromString(String value) {
 *            return switch (value.toLowerCase()) {
 *                case "ignore" -> IGNORE;
 *                case "respond" -> RESPOND;
 *                case "notify" -> NOTIFY;
 *                default -> throw new IllegalArgumentException(...);
 *            };
 *        }
 *   5. 对于 Jackson 反序列化 Record 的兼容性问题，有两种方案:
 *        a) 使用 @JsonCreator + @JsonProperty 显式标注构造器参数
 *        b) 使用 @JsonDeserialize 并自定义反序列化器
 *        推荐方案 (a)，更简洁
 *   6. 如果使用 Spring AI 的结构化输出功能（AiClient.call(type)），
 *      确保 RouterSchema 是标准的 Java Bean 或 Record，能被框架自动映射。
 */
public record RouterSchema(
    // TODO: reasoning 字段 — LLM 给出分类的理由
    // TODO: classification 字段 — 分类决策
) {
    // TODO: Classification 枚举（或引用 AgentState.Classification）

    // TODO: fromString(String) 静态方法 — 将 LLM 返回的字符串转为枚举
}
