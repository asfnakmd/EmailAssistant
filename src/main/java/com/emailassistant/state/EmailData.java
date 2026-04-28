package com.emailassistant.state;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * EmailData — 邮件完整数据结构，作为 API 请求体和内部处理的标准化数据对象。
 *
 * <p>与 AgentState 解耦：EmailData 是原始输入，AgentState 是运行时状态。
 * 在 API 层将 EmailData 转换为初始 AgentState。
 */
public record EmailData(
    @NotBlank String id,
    @NotBlank @Email String from,
    @NotNull List<@Email String> to,
    List<String> cc,
    @NotBlank String subject,
    @NotBlank String body,
    String htmlBody,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime receivedAt,
    List<Attachment> attachments,
    String threadId,
    @NotNull Priority priority
) {
    public enum Priority {
        HIGH, NORMAL, LOW
    }

    public record Attachment(
        String filename,
        String mimeType,
        long size
    ) {}

    public EmailData {
        to = to == null ? List.of() : List.copyOf(to);
        cc = cc == null ? List.of() : List.copyOf(cc);
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    /**
     * 将邮件格式化为适合注入 LLM prompt 的字符串，包含所有关键字段。
     */
    public String toPromptString() {
        StringBuilder sb = new StringBuilder();
        sb.append("From: ").append(from).append("\n");
        sb.append("To: ").append(String.join(", ", to)).append("\n");
        if (!cc.isEmpty()) {
            sb.append("Cc: ").append(String.join(", ", cc)).append("\n");
        }
        sb.append("Subject: ").append(subject).append("\n");
        sb.append("Priority: ").append(priority).append("\n");
        if (receivedAt != null) {
            sb.append("Received: ").append(receivedAt).append("\n");
        }
        if (threadId != null && !threadId.isBlank()) {
            sb.append("ThreadId: ").append(threadId).append("\n");
        }
        sb.append("\n").append(body);
        return sb.toString();
    }

    /** 将 EmailData 转换为工作流的初始 AgentState。 */
    public AgentState toAgentState() {
        return new AgentState(
            toPromptString(),
            null,   // 分类尚未执行
            List.of(ChatMessage.user(toPromptString())),
            null    // 尚无偏好数据
        );
    }
}