package com.emailassistant.tools;

/*
 * ============================================================================
 * CalendarTools — 日历操作工具集
 * ============================================================================
 *
 * 功能描述:
 *   封装与日历相关的工具函数，用于查询日程、创建会议邀请等操作。
 *   当邮件涉及会议安排、日程查询时，LLM 会调用此工具集的函数。
 *
 *   这是可选模块，如果邮件助手不涉及日程管理，可以省略此文件。
 *
 * 编码建议:
 *   1. 使用 @Component 注解注册为 Spring Bean。
 *
 *   2. 建议实现的工具方法:
 *      a) scheduleMeeting(attendees, subject, dateTime, duration)
 *         - 创建会议并发送邀请
 *         - 参数: attendees (List<String>), subject, dateTime (ISO 8601),
 *           duration (分钟数)
 *         - 需要对接 Google Calendar / Outlook Calendar API
 *         - 同样需要考虑人工审批（安排在 HumanInLoopGraph 的白名单中）
 *
 *      b) checkAvailability(dateTime, duration)
 *         - 检查指定时间段的忙闲状态
 *         - 返回可用/冲突及冲突的日程详情
 *
 *      c) listTodayEvents()
 *         - 列出今日所有日程
 *         - 帮助 LLM 了解用户的当日安排
 *
 *   3. 日期时间处理:
 *      - 使用 java.time.LocalDateTime / ZonedDateTime
 *      - 工具参数中使用 String 接收日期，内部解析（LLM 传参是 JSON）
 *      - 建议在 @ToolParam description 中明确日期格式:
 *        "Date and time in ISO 8601 format, e.g. 2024-01-15T14:00:00"
 *
 *   4. 与 EmailTools 的协作:
 *      - LLM 可能在同一轮对话中先 checkAvailability 再 scheduleMeeting
 *      - 确保工具方法的返回值包含足够上下文供 LLM 决策
 *      - 例如: "Available. Next conflict at 16:00." 比 "OK" 更有用
 */
public class CalendarTools {
    // TODO: scheduleMeeting(List<String> attendees, String subject, String dateTime, int durationMinutes)

    // TODO: checkAvailability(String dateTime, int durationMinutes)

    // TODO: listTodayEvents()
}
