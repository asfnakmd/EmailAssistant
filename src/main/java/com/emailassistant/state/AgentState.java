package com.emailassistant.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.ArrayList;
import java.util.List;

/**
 * AgentState — 贯穿整个工作流的核心状态对象。
 *
 * <p>图节点之间不直接调用，而是通过传递不可变的 AgentState 来通信。
 * 每个节点读取当前 state，经过处理后通过 withXxx() 方法返回一个新实例。
 * 这种不可变模式保证了每一步的可追溯性和线程安全。
 *
 * <h3>状态字段</h3>
 * <ul>
 *   <li>{@link #emailInput} — 原始邮件内容</li>
 *   <li>{@link #classificationDecision} — 分类决策结果</li>
 *   <li>{@link #messages} — 完整的对话历史</li>
 *   <li>{@link #preferences} — 用户偏好配置</li>
 * </ul>
 */
public record AgentState(
        /** 原始邮件内容，用于 LLM 分析和分类 */
        String emailInput,

        /** 分类决策：IGNORE / RESPOND / NOTIFY，null 表示尚未分类 */
        Classification classificationDecision,

        /** 完整的对话历史消息列表 */
        List<ChatMessage> messages,

        /** 用户偏好配置 */
        UserPreferences preferences
) {
    /**
     * 紧凑构造器：对 messages 进行防御性拷贝，保证不可变性。
     */
    public AgentState {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    /**
     * 邮件分类枚举。
     *
     * <p>IGNORE — 营销/垃圾/无关邮件，直接忽略<br>
     * RESPOND — 需要回复的邮件<br>
     * NOTIFY — 重要但无需回复，仅通知
     */
    public enum Classification {
        IGNORE, RESPOND, NOTIFY;

        /**
         * 从字符串解析分类（大小写不敏感），用于 JSON 反序列化。
         *
         * @param value 分类字符串
         * @return Classification 枚举值
         */
        @JsonCreator
        public static Classification fromString(String value) {
            if (value == null) return null;
            return switch (value.strip().toLowerCase()) {
                case "ignore" -> IGNORE;
                case "respond" -> RESPOND;
                case "notify" -> NOTIFY;
                default -> {
                    // 尝试按枚举名直接匹配
                    try {
                        yield Classification.valueOf(value.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException(
                                "无效的分类值: '" + value + "', 有效值: ignore, respond, notify");
                    }
                }
            };
        }

        /**
         * 序列化为小写字符串，便于 LLM 处理。
         */
        @JsonValue
        public String toLowerCase() {
            return name().toLowerCase();
        }
    }

    // ==================== 不可变更新方法 ====================

    /** 设置邮件输入内容 */
    public AgentState withEmailInput(String emailInput) {
        return new AgentState(emailInput, classificationDecision, messages, preferences);
    }

    /** 设置分类决策 */
    public AgentState withClassificationDecision(Classification classificationDecision) {
        return new AgentState(emailInput, classificationDecision, messages, preferences);
    }

    /** 替换整个消息列表 */
    public AgentState withMessages(List<ChatMessage> messages) {
        return new AgentState(emailInput, classificationDecision, messages, preferences);
    }

    /** 设置用户偏好 */
    public AgentState withPreferences(UserPreferences preferences) {
        return new AgentState(emailInput, classificationDecision, messages, preferences);
    }

    /**
     * 追加单条消息到对话历史。
     *
     * @param message 要追加的消息
     * @return 新状态
     */
    public AgentState addMessage(ChatMessage message) {
        List<ChatMessage> newMessages = new ArrayList<>(messages.size() + 1);
        newMessages.addAll(messages);
        newMessages.add(message);
        return new AgentState(emailInput, classificationDecision, newMessages, preferences);
    }

    /**
     * 追加多条消息到对话历史。
     *
     * @param additionalMessages 要追加的消息列表
     * @return 新状态
     */
    public AgentState addMessages(List<ChatMessage> additionalMessages) {
        List<ChatMessage> newMessages = new ArrayList<>(messages.size() + additionalMessages.size());
        newMessages.addAll(messages);
        newMessages.addAll(additionalMessages);
        return new AgentState(emailInput, classificationDecision, newMessages, preferences);
    }
}
