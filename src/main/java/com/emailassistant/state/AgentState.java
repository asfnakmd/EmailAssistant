package com.emailassistant.state;

/*
 * ============================================================================
 * AgentState — 代理状态（核心状态对象）
 * ============================================================================
 *
 * 功能描述:
 *   这是贯穿整个工作流的状态对象，类似于 Python 原项目中使用 TypedDict
 *   定义的 State 类。它承载了邮件处理的完整上下文：输入邮件、分类决策、
 *   对话历史、用户偏好等。Graph Core 框架在每个节点之间传递此对象。
 *
 *   映射关系: Python schemas.py 中的 State TypedDict → Java Record
 *
 * 编码建议:
 *   1. 使用 Java 17+ 的 Record 类型实现不可变状态，保持与 LangGraph 的
 *      不可变状态模式一致。Record 自动生成构造器、equals/hashCode/toString。
 *   2. 每个"修改"操作应返回新实例（通过 with 方法或 Builder 模式）:
 *        public AgentState withClassification(String decision) {
 *            return new AgentState(emailInput, decision, messages, preferences);
 *        }
 *   3. 字段类型设计:
 *      - emailInput: String — 原始邮件文本
 *      - classificationDecision: String (或自定义枚举 Classification)
 *        枚举建议: IGNORE / RESPOND / NOTIFY，与 Python 端保持一致
 *      - messages: List<ChatMessage> — 对话历史，包含 user/assistant/tool 角色
 *      - preferences: UserPreferences (可为 null) — 用户偏好配置
 *   4. 如果需要框架级别的状态支持，可实现 GraphState 接口（由 Graph Core 提供）。
 *   5. 建议将 ChatMessage 也定义为 Record，包含 role(String)、content(String)、
 *      toolCalls(List<ToolCall>) 三个字段。
 *   6. 对于可选字段，使用 Optional 或允许 null，但 Record 中建议通过工厂方法
 *      提供默认值重载。
 *   7. 考虑添加 jakarta.validation 注解（@NotBlank 等）用于 API 入参校验，
 *      但注意 Graph 内部传递的状态可能不需要校验。
 */
public record AgentState(
    // TODO: 定义状态字段
    // - emailInput: 原始邮件文本
    // - classificationDecision: 分类决策结果 (IGNORE / RESPOND / NOTIFY)
    // - messages: 对话消息列表
    // - preferences: 用户偏好设置（可为 null）
) {
    /*
     * 分类决策枚举 — 定义邮件处理的三条路径
     *
     * IGNORE:  垃圾邮件、营销邮件，直接丢弃
     * RESPOND: 需要回复的邮件，进入 LLM 写作流程
     * NOTIFY:  重要但无需回复的邮件（如通知、告警），可推送提醒
     */
    // TODO: 定义 Classification 枚举

    /*
     * withXxx 方法 — 创建修改后的新实例
     *
     * 编码建议:
     *   - 每个字段提供一个 with 方法，返回替换该字段后的新 Record
     *   - 例如: public AgentState withMessages(List<ChatMessage> newMessages) {...}
     *   - 对于列表类字段，建议额外提供 addMessage(ChatMessage) 方法，
     *     内部使用 Stream.concat 或 new ArrayList + Collections.unmodifiableList
     */
}

/*
 * ChatMessage — 单条聊天消息
 *
 * 编码建议:
 *   - 字段: role (String: "user"/"assistant"/"tool"/"system"),
 *          content (String),
 *          toolCalls (List<ToolCall>, 可为空列表)
 *   - 提供静态工厂方法: ChatMessage.user(String content), ChatMessage.assistant(...)
 */
// TODO: 定义 ChatMessage Record

/*
 * ToolCall — 工具调用记录
 *
 * 编码建议:
 *   - 字段: id (String), name (String), args (Map<String, Object>)
 *   - 对应 LLM 返回的 function_call 结构
 */
// TODO: 定义 ToolCall Record
