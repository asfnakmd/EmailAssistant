package com.emailassistant.state;

/*
 * ============================================================================
 * EmailData — 邮件数据结构
 * ============================================================================
 *
 * 功能描述:
 *   封装一封邮件的完整数据，作为 API 请求体和内部处理的标准化数据对象。
 *   与 Python 原项目中解析后的 email 字典对应。
 *
 * 编码建议:
 *   1. 字段设计（根据实际邮件 API 调整）:
 *      - id: String — 邮件唯一标识
 *      - from: String — 发件人地址
 *      - to: List<String> — 收件人列表
 *      - cc: List<String> — 抄送列表
 *      - subject: String — 邮件主题
 *      - body: String — 邮件正文（纯文本）
 *      - htmlBody: String — 邮件正文（HTML 格式，可选）
 *      - receivedAt: LocalDateTime — 接收时间
 *      - attachments: List<Attachment> — 附件列表
 *      - threadId: String — 邮件线程 ID（用于上下文关联）
 *      - priority: Priority 枚举 — 紧急程度
 *   2. 使用 Jakarta Validation 注解进行入参校验:
 *      - @NotBlank 用于必填字符串字段
 *      - @Email 用于邮件地址字段
 *      - @NotEmpty 用于集合字段
 *   3. 将 EmailData 与 AgentState 解耦 — EmailData 是原始输入，
 *      AgentState 是运行时状态。在 API 层将 EmailData 转换为 AgentState。
 *   4. 建议提供 toPromptString() 方法，将邮件格式化为适合注入 LLM
 *      prompt 的字符串格式。
 *   5. 附件可以定义为内部 Record:
 *        public record Attachment(String filename, String mimeType, long size) {}
 *   6. 如果对接 Gmail/Outlook API，考虑添加原始 MIME 内容和标签字段。
 *   7. 对于日期字段，使用 LocalDateTime 并配置 Jackson 的日期序列化格式。
 */
public record EmailData(
    // TODO: 定义邮件各字段（id, from, to, subject, body 等）
) {
    // TODO: Priority 枚举 — HIGH, NORMAL, LOW

    // TODO: Attachment Record — 附件元数据

    // TODO: toPromptString() 方法 — 将邮件转为 LLM prompt 可用的字符串
}
