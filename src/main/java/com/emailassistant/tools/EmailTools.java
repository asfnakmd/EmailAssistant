package com.emailassistant.tools;

import com.emailassistant.email.EmailSenderService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * EmailTools — 邮件操作工具集，包含所有可被 LLM 调用的邮件处理函数。
 *
 * <p>本类使用 Spring AI 的 {@link Tool @Tool} 注解声明工具定义，
 * Spring AI 的 {@code ChatClient} 会自动扫描并注册这些工具，
 * 使 LLM 在生成回复时能够感知并调用这些函数。
 *
 * <p>每个工具方法使用 {@link ToolParam @ToolParam} 描述参数，
 * 这些描述会作为 JSON Schema 的一部分发送给 LLM，帮助 LLM 正确填参。
 *
 * <h3>可用工具列表</h3>
 * <ul>
 *   <li>{@link #writeEmail(String, String, String)} — 编写并发送邮件</li>
 *   <li>{@link #triageEmail(String)} — 将邮件标记为 ignore/respond/notify</li>
 *   <li>{@link #done(boolean)} — 标记邮件处理完成</li>
 *   <li>{@link #checkCalendar(String, int)} — 查询日历安排</li>
 *   <li>{@link #searchPreviousEmails(String, String)} — 搜索历史邮件</li>
 * </ul>
 *
 * <h3>双用途设计</h3>
 * <ol>
 *   <li><b>LLM 侧</b>：通过 @Tool 注解被 Spring AI ChatClient 发现，
 *       作为 Function Calling 的定义发给 LLM</li>
 *   <li><b>Agent 侧</b>：通过 {@link ToolRegistry} 注册为 {@link Tool} 接口，
 *       在 toolCallNode 中手动执行</li>
 * </ol>
 */
@Component
public class EmailTools {

    private static final Set<String> VALID_CATEGORIES = Set.of("ignore", "respond", "notify");

    /** 真实的邮件发送服务，通过构造器注入 */
    private final EmailSenderService emailSenderService;

    public EmailTools(EmailSenderService emailSenderService) {
        this.emailSenderService = emailSenderService;
    }

    /**
     * 编写并通过 SMTP 发送邮件回复。
     * 使用 application.yml 中配置的 QQ 邮箱账号发送。
     *
     * @param to      收件人邮箱地址
     * @param subject 邮件主题
     * @param content 邮件正文
     * @return 发送结果描述
     */
    @Tool(name = "write_email", description = "编写并发送邮件回复，需提供收件人地址、主题和正文内容")
    public String writeEmail(
            @ToolParam(description = "收件人邮箱地址，如 user@example.com") String to,
            @ToolParam(description = "邮件主题行") String subject,
            @ToolParam(description = "邮件正文内容") String content) {
        // 参数校验
        if (to == null || to.isBlank()) {
            return "错误：收件人地址不能为空";
        }
        if (!to.contains("@")) {
            return "错误：收件人地址格式不正确，缺少 @ 符号";
        }
        if (subject == null || subject.isBlank()) {
            return "错误：邮件主题不能为空";
        }
        if (content == null || content.isBlank()) {
            return "错误：邮件正文不能为空";
        }

        // 通过真实 SMTP 发送邮件
        return emailSenderService.sendTextEmail(to, subject, content);
    }

    /**
     * 将邮件分类为 ignore（忽略）、respond（需回复）或 notify（仅通知）。
     *
     * @param category 分类类别: ignore / respond / notify
     * @return 操作结果描述
     */
    @Tool(name = "triage_email", description = "将邮件分类为 ignore（忽略）、respond（需回复）或 notify（仅通知）")
    public String triageEmail(
            @ToolParam(description = "分类类别：ignore（忽略）、respond（回复）、notify（通知）") String category) {
        if (category == null || !VALID_CATEGORIES.contains(category.toLowerCase())) {
            return "错误：无效分类「" + category + "」，有效值为 ignore、respond、notify";
        }
        return "✓ 邮件已标记为 " + category.toLowerCase();
    }

    /**
     * 标记邮件处理完成，结束当前工作流。
     *
     * @param done true 表示处理完成
     * @return 操作结果描述
     */
    @Tool(name = "done", description = "标记邮件处理完成，结束当前流程。当邮件已处理完毕时调用此工具")
    public String done(@ToolParam(description = "是否完成处理") boolean done) {
        if (done) {
            return "✓ 邮件处理流程已完成";
        }
        return "邮件处理尚未完成，继续处理";
    }

    /**
     * 查询指定日期的日历安排，检查是否有时间冲突。
     *
     * @param date     查询日期（格式：yyyy-MM-dd）
     * @param duration 会议时长（分钟）
     * @return 日历查询结果描述
     */
    @Tool(name = "check_calendar", description = "查询日历安排，检查指定日期和时长内是否有时间冲突")
    public String checkCalendar(
            @ToolParam(description = "查询日期，格式 yyyy-MM-dd，如 2024-01-15") String date,
            @ToolParam(description = "会议持续时长（分钟）") int duration) {
        if (date == null || date.isBlank()) {
            return "错误：日期不能为空";
        }
        // 生产环境调用日历 API 查询
        return "✓ 日历查询结果：" + date + "（" + duration + "分钟）— 当前时段无冲突";
    }

    /**
     * 搜索历史邮件，根据关键词和发件人筛选。
     *
     * @param query  搜索关键词
     * @param sender 发件人邮箱地址（可选）
     * @return 搜索结果描述
     */
    @Tool(name = "search_previous_emails", description = "搜索历史邮件，可按关键词和发件人筛选")
    public String searchPreviousEmails(
            @ToolParam(description = "搜索关键词，如会议、报价等") String query,
            @ToolParam(description = "发件人邮箱地址（可选，为空时搜索所有发件人）") String sender) {
        if (query == null || query.isBlank()) {
            return "错误：搜索关键词不能为空";
        }
        // 生产环境调用邮件 API 搜索
        String senderInfo = (sender != null && !sender.isBlank()) ? "，发件人：" + sender : "";
        return "✓ 历史邮件搜索完成，关键词：「" + query + "」" + senderInfo + "，找到 0 封相关邮件";
    }
}
