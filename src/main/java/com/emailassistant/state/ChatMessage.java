package com.emailassistant.state;

import java.util.Collections;
import java.util.List;

/**
 * ChatMessage — 对话历史中的单条消息，与 OpenAI 消息模型对齐。
 */
public record ChatMessage(
    String role,      // "user" | "assistant" | "tool" | "system"
    String content,
    List<ToolCall> toolCalls
) {
    public ChatMessage {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    // ---- 静态工厂方法：语义化构造 ----

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, List.of());
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content, List.of());
    }

    public static ChatMessage assistant(String content, List<ToolCall> toolCalls) {
        return new ChatMessage("assistant", content, toolCalls);
    }

    public static ChatMessage tool(String content, ToolCall toolCall) {
        return new ChatMessage("tool", content, List.of(toolCall));
    }

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, List.of());
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}