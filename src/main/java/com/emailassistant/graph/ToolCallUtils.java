package com.emailassistant.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.Collections;
import java.util.Map;

/**
 * ToolCallUtils — ToolCall JSON 转换工具。
 *
 * <p>Spring AI 的 {@link AssistantMessage.ToolCall#arguments()} 返回的是 JSON 字符串，
 * 而项目的 {@link com.emailassistant.state.ToolCall#args()} 使用的是 {@code Map<String, Object>}。
 * 本工具类提供两者之间的互转方法。
 */
public final class ToolCallUtils {

    /** 共享的 ObjectMapper 实例（线程安全） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ToolCallUtils() {}

    /**
     * 将 Spring AI 的 ToolCall JSON 参数字符串转换为 Map。
     *
     * @param json JSON 字符串，如 {"to": "user@example.com", "subject": "Hello"}
     * @return 参数 Map，解析失败时返回空 Map
     */
    public static Map<String, Object> argsFromJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<?, ?> result = MAPPER.readValue(json, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> typedResult = (Map<String, Object>) result;
            return typedResult;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    /**
     * 将项目的 ToolCall args Map 序列化为 JSON 字符串。
     *
     * @param args 参数 Map
     * @return JSON 字符串，序列化失败时返回 "{}"
     */
    @SuppressWarnings("unchecked")
    public static String argsToJson(Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return "{}";
        }
        try {
            return MAPPER.writeValueAsString(args);
        } catch (Exception e) {
            return "{}";
        }
    }
}
