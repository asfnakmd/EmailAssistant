package com.emailassistant.state;

import java.util.Collections;
import java.util.Map;

/**
 * UserPreferences — 用户偏好配置，用于记忆系统中的个性化定制。
 * preferences 为 null 表示尚无偏好数据。
 */
public record UserPreferences(
    String preferredLanguage,//首选语言
    String preferredTone,//首选语气
    Map<String, String> customPreferences//自定义偏好项
) {
    public UserPreferences {
        customPreferences = customPreferences == null
                ? Map.of() : Collections.unmodifiableMap(customPreferences);
    }

    public static UserPreferences empty() {
        return new UserPreferences(null, null, Map.of());
    }
}