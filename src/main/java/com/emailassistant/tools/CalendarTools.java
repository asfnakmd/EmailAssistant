package com.emailassistant.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * CalendarTools — 日历操作工具集。
 *
 * <p>封装与日历相关的工具函数，用于查询日程、创建会议邀请等操作。
 * 当 LLM 在处理邮件时发现涉及会议安排、日程查询等内容时，
 * 可以调用此工具集中的函数。
 *
 * <p>与 {@link EmailTools} 的协作：
 * <ul>
 *   <li>LLM 可能在同一轮对话中先调用 {@link #checkAvailability(String, int)}
 *       再调用 {@link #scheduleMeeting(String, String, String)}</li>
 *   <li>工具方法的返回值应包含足够的上下文供 LLM 做下一步决策</li>
 * </ul>
 */
@Component
public class CalendarTools {

    /**
     * 安排会议并发送邀请。
     *
     * @param subject   会议主题
     * @param attendees 参会人列表（多个地址用逗号分隔）
     * @param dateTime  会议时间（ISO 8601 格式，如 2024-01-15T14:00:00）
     * @return 操作结果描述
     */
    @Tool(name = "schedule_meeting",
          description = "安排会议并发送邀请，需提供会议主题、参会人列表和时间")
    public String scheduleMeeting(
            @ToolParam(description = "会议主题") String subject,
            @ToolParam(description = "参会人邮箱地址列表，多个用逗号分隔") String attendees,
            @ToolParam(description = "会议时间，ISO 8601 格式，如 2024-01-15T14:00:00") String dateTime) {
        if (subject == null || subject.isBlank()) {
            return "错误：会议主题不能为空";
        }
        if (attendees == null || attendees.isBlank()) {
            return "错误：参会人列表不能为空";
        }
        if (dateTime == null || dateTime.isBlank()) {
            return "错误：会议时间不能为空";
        }
        // 生产环境调用日历 API 创建会议
        return String.format("✓ 会议已创建：%s\n  时间：%s\n  参会人：%s", subject, dateTime, attendees);
    }

    /**
     * 检查指定时间段是否空闲。
     *
     * @param dateTime 起始时间（ISO 8601 格式）
     * @param duration 持续时长（分钟）
     * @return 忙闲状态描述
     */
    @Tool(name = "check_availability",
          description = "检查指定时间段是否空闲，返回忙闲状态")
    public String checkAvailability(
            @ToolParam(description = "起始时间，ISO 8601 格式，如 2024-01-15T14:00:00") String dateTime,
            @ToolParam(description = "持续时长（分钟），如 60") int duration) {
        if (dateTime == null || dateTime.isBlank()) {
            return "错误：时间不能为空";
        }
        // 生产环境调用日历 API 查询
        return String.format("✓ 时间可用：%s（持续 %d 分钟）— 当前无冲突安排", dateTime, duration);
    }

    /**
     * 列出今日所有日程。
     *
     * @return 今日日程列表描述
     */
    @Tool(name = "list_today_events",
          description = "列出今日所有日程安排")
    public String listTodayEvents() {
        // 生产环境调用日历 API 获取今日日程
        return "✓ 今日日程：暂无安排";
    }
}
