package com.emailassistant.tools;

import java.util.Map;

/**
 * Tool — AI Agent 工具接口。
 *
 * <p>每个工具封装一个可被 LLM 调用的操作（如写邮件、查日历等）。
 * LLM 通过 Function Calling 机制选择调用哪个工具、传入什么参数，
 * 然后由 {@link ToolRegistry} 查找对应的 Tool 实例并执行。
 *
 * <h3>与 Spring AI @Tool 注解的关系</h3>
 * Spring AI 的 {@code @Tool} 注解用于 LLM 调用时的工具定义（生成 JSON Schema），
 * 而本接口是 Agent 内部手动执行工具调用的抽象层。
 * 两者分工不同，但共享相同的业务逻辑实现。
 *
 * <h3>实现建议</h3>
 * <ul>
 *   <li>{@link #execute(Map)} 内部应捕获所有异常，返回错误描述而非抛出</li>
 *   <li>返回值是自然语言文本，LLM 会读取并据此决定下一步操作</li>
 *   <li>如果工具执行涉及外部 API 调用，建议添加日志记录以便审计追踪</li>
 * </ul>
 */
public interface Tool {

    /**
     * 工具名称，必须与 LLM function_call 中的函数名一致。
     * 例如: "write_email", "triage_email", "done"
     */
    String getName();

    /**
     * 工具描述，LLM 通过此描述判断何时调用该工具。
     * 描述应当清晰、准确，直接影响 LLM 的选择准确率。
     * 例如: "编写并发送邮件回复，包含收件人、主题和正文"
     */
    String getDescription();

    /**
     * 执行工具调用。
     *
     * @param args LLM 传入的参数，键值对形式。
     *             例如 write_email 工具可能收到:
     *             {"to": "user@example.com", "subject": "Re: Hello", "content": "..."}
     * @return 执行结果文本（自然语言），LLM 会读取此返回值
     */
    String execute(Map<String, Object> args);
}
