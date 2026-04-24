package com.emailassistant.tools;

/*
 * ============================================================================
 * EmailTools — 邮件操作工具集
 * ============================================================================
 *
 * 功能描述:
 *   封装所有与邮件处理相关的工具函数，供 LLM 通过 Function Calling 机制调用。
 *   LLM 会在认为需要时自主决定调用哪个工具（如写邮件、分类邮件、标记完成等）。
 *
 *   映射关系: Python tools/default/ 和 tools/gmail/ → Java @Tool 方法集合
 *
 * 编码建议:
 *   1. 使用 @Component 注解使其成为 Spring Bean，被 ToolRegistry 自动发现。
 *
 *   2. 每个工具方法使用 @Tool 注解声明:
 *        @Tool(name = "write_email", description = "Write and send an email reply")
 *        其中 name 对应 LLM function_call 中的函数名，
 *        description 是给 LLM 看的用途说明，直接影响 LLM 的选择准确性。
 *
 *   3. 方法参数使用 @ToolParam 注解:
 *        @ToolParam(description = "Recipient email address") String to
 *        description 会作为 JSON Schema 的一部分发给 LLM，帮助 LLM 正确填参。
 *
 *   4. 建议实现的工具方法:
 *      a) writeEmail(to, subject, content)
 *         - 编写并发送邮件
 *         - 实际调用邮件 API (Gmail/Outlook SMTP/SendGrid 等)
 *         - 返回值应描述操作结果，LLM 会看到这个返回值
 *         - 注意: 这是最敏感的操作，需要人工审批（在 HITL 版本中）
 *
 *      b) triageEmail(category)
 *         - 标记邮件分类
 *         - 参数: category 取 "ignore"/"respond"/"notify"
 *         - 可以在实际系统中移动邮件到对应文件夹或打标签
 *
 *      c) done(boolean done)
 *         - 向 LLM 发出"处理完成"信号
 *         - 工作流通过检测此工具是否被调用来判断是否结束
 *         - 这是 LangGraph 原项目中的设计模式，建议保留
 *
 *      d) checkCalendar(query, date) — 可选，查询日历
 *         - 如果有日程冲突，LLM 可以据此调整回复内容
 *
 *      e) searchPreviousEmails(query, sender) — 可选，搜索历史邮件
 *         - 提供上下文帮助 LLM 写出更连贯的回复
 *
 *   5. 工具方法的返回值类型:
 *      - 建议返回 String，包含操作结果的描述文本
 *      - LLM 会阅读此文本来决定下一步动作
 *      - 返回 JSON 字符串也可，但自然语言对 LLM 更友好
 *
 *   6. 异常处理:
 *      - 工具方法内部应捕获异常并返回错误描述字符串，而非抛出
 *      - 例如: return "Error: Failed to send email - SMTP connection timeout"
 *      - 这样 LLM 可以据此调整策略（如重试、告知用户失败原因等）
 *
 *   7. 日志记录:
 *      - 每个工具调用应记录 INFO 级别日志（哪个工具、参数、结果）
 *      - 这本身就是审计追踪的一部分
 */
public class EmailTools {
    // TODO: writeEmail(String to, String subject, String content) — 编写并发送邮件

    // TODO: triageEmail(String category) — 标记邮件分类

    // TODO: done(boolean done) — 发出处理完成信号

    // TODO: checkCalendar(String query, String date) — 查询日历（可选扩展）

    // TODO: searchPreviousEmails(String query, String sender) — 搜索历史邮件（可选扩展）
}
