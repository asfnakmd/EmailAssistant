package com.emailassistant.tools;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.function.Function;

/**
 * ToolConfig — 工具注册配置类。
 *
 * <p>将业务组件（{@link EmailTools}、{@link CalendarTools}）中的方法
 * 包装为 {@link Tool} 接口实现并注册为 Spring Bean。
 *
 * <p>{@link ToolRegistry} 会自动收集所有 {@link Tool} Bean，
 * 供图工作流中的 toolCallNode 手动执行工具调用。
 *
 * <h3>设计说明</h3>
 * EmailTools 和 CalendarTools 使用 Spring AI 的 {@code @Tool} 注解
 * 定义工具（供 ChatClient 自动发现），而本类创建 {@link Tool} 接口适配器
 * （供 Agent 手动执行）。两者共享相同的业务方法，避免重复实现。
 */
@Configuration
public class ToolConfig {

    // ==================== 工具 Bean 注册 ====================

    @Bean
    public Tool writeEmailTool(EmailTools emailTools) {
        return createTool("write_email", "编写并发送邮件回复，需提供收件人地址、主题和正文内容",
                args -> emailTools.writeEmail(
                        strArg(args, "to"),
                        strArg(args, "subject"),
                        strArg(args, "content")));
    }

    @Bean
    public Tool triageEmailTool(EmailTools emailTools) {
        return createTool("triage_email", "将邮件分类为 ignore（忽略）、respond（需回复）或 notify（仅通知）",
                args -> emailTools.triageEmail(strArg(args, "category")));
    }

    @Bean
    public Tool doneTool(EmailTools emailTools) {
        return createTool("done", "标记邮件处理完成，结束当前流程",
                args -> emailTools.done(boolArg(args, "done", true)));
    }

    @Bean
    public Tool checkCalendarTool(EmailTools emailTools) {
        return createTool("check_calendar", "查询日历安排，检查指定日期是否有时间冲突",
                args -> emailTools.checkCalendar(strArg(args, "date"), intArg(args, "duration", 30)));
    }

    @Bean
    public Tool searchPreviousEmailsTool(EmailTools emailTools) {
        return createTool("search_previous_emails", "搜索历史邮件，可按关键词和发件人筛选",
                args -> emailTools.searchPreviousEmails(
                        strArg(args, "query"), strArg(args, "sender")));
    }

    @Bean
    public Tool scheduleMeetingTool(CalendarTools calendarTools) {
        return createTool("schedule_meeting", "安排会议并发送邀请，需提供会议主题、参会人列表和时间",
                args -> calendarTools.scheduleMeeting(
                        strArg(args, "subject"),
                        strArg(args, "attendees"),
                        strArg(args, "dateTime")));
    }

    @Bean
    public Tool checkAvailabilityTool(CalendarTools calendarTools) {
        return createTool("check_availability", "检查指定时间段是否空闲",
                args -> calendarTools.checkAvailability(strArg(args, "dateTime"), intArg(args, "duration", 60)));
    }

    @Bean
    public Tool listTodayEventsTool(CalendarTools calendarTools) {
        return createTool("list_today_events", "列出今日所有日程安排",
                args -> calendarTools.listTodayEvents());
    }

    // ==================== 工具工厂方法 ====================

    /**
     * 创建 Tool 接口实例的工厂方法。
     *
     * @param name        工具名称
     * @param description 工具描述
     * @param executor    执行函数（接收 Map 参数，返回结果字符串）
     * @return Tool 实例
     */
    private static Tool createTool(String name, String description,
                                    Function<Map<String, Object>, String> executor) {
        return new Tool() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public String execute(Map<String, Object> args) {
                try {
                    return executor.apply(args);
                } catch (Exception e) {
                    return "错误：工具「" + name + "」执行异常 — " + e.getMessage();
                }
            }
        };
    }

    // ==================== 参数提取工具方法 ====================

    /** 从 Map 中提取字符串参数，null 时返回默认值 */
    private static String strArg(Map<String, Object> args, String key) {
        Object val = args.get(key);
        return val != null ? val.toString() : null;
    }

    /** 从 Map 中提取整数参数，null 时返回默认值 */
    private static int intArg(Map<String, Object> args, String key, int defaultValue) {
        Object val = args.get(key);
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            try { return Integer.parseInt(s); }
            catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }

    /** 从 Map 中提取布尔参数，null 时返回默认值 */
    private static boolean boolArg(Map<String, Object> args, String key, boolean defaultValue) {
        Object val = args.get(key);
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return Boolean.parseBoolean(s);
        return defaultValue;
    }
}
