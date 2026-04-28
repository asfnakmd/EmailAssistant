package com.emailassistant.state;

import java.util.Collections;
import java.util.Map;

/**
 * ToolCall — LLM 返回的 function_call 结构。
 */
public record ToolCall(
    String id,
    String name,
    Map<String, Object> args
) {
    public ToolCall {
        args = args == null ? Map.of() : Collections.unmodifiableMap(args);
    }
}