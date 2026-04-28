package com.emailassistant.memory;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * MemoryStoreService — 记忆存储服务。
 *
 * <p>为 AI 代理提供持久化的记忆能力，存储和检索用户的历史偏好、
 * 行为模式、重要交互记录等。类似于 LangGraph 的 Store 机制。
 *
 * <p>默认使用内存存储（{@link ConcurrentHashMap}），适合开发和测试。
 * 生产环境应切换为 Redis 存储（通过修改实现或添加 Profile 配置）。
 *
 * <h3>Redis Key 设计</h3>
 * 模式：{@code memory:{userId}:{category}:{memoryId}}
 * 示例：{@code memory:user123:preference:sender_priority}
 *
 * <h3>核心功能</h3>
 * <ul>
 *   <li>{@link #saveMemory(String, String, String, Object, int)} — 存储记忆</li>
 *   <li>{@link #getMemory(String, String, Class)} — 读取单条记忆</li>
 *   <li>{@link #searchMemories(String, String, String)} — 关键词搜索</li>
 *   <li>{@link #deleteMemory(String, String)} — 删除记忆</li>
 *   <li>{@link #listMemories(String, String)} — 列出分类下的所有记忆</li>
 * </ul>
 */
@Service
public class MemoryStoreService {

    /**
     * 内存存储实现。Key 格式："{userId}:{category}:{memoryId}"，
     * Value 为 JSON 序列化后的字符串。
     *
     * <p>生产环境应替换为 RedisTemplate<String, String>，
     * 通过 application.yml 中的 spring.data.redis 配置连接。
     */
    private final Map<String, String> store = new ConcurrentHashMap<>();

    // ==================== 核心 CRUD 操作 ====================

    /**
     * 存储一条记忆。
     *
     * @param userId   用户标识
     * @param category 记忆分类（如 preference、interaction）
     * @param key      记忆键名（在同一分类下唯一）
     * @param value    记忆内容（自动 toString）
     * @param ttlDays  过期天数（内存模式下暂不支持自动过期）
     */
    public void saveMemory(String userId, String category, String key, Object value, int ttlDays) {
        String storeKey = buildKey(userId, category, key);
        String storeValue = value != null ? value.toString() : "";
        store.put(storeKey, storeValue);
    }

    /**
     * 存储一条记忆，使用默认过期时间 30 天。
     *
     * @param userId   用户标识
     * @param category 记忆分类
     * @param key      记忆键名
     * @param value    记忆内容
     */
    public void saveMemory(String userId, String category, String key, Object value) {
        saveMemory(userId, category, key, value, 30);
    }

    /**
     * 读取单条记忆。
     *
     * @param userId 用户标识
     * @param key    记忆键名（完整键名，包含分类）
     * @param type   期望返回的类型
     * @param <T>    泛型类型
     * @return Optional 包裹的记忆内容
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getMemory(String userId, String key, Class<T> type) {
        String storeKey = userId + ":" + key;
        String value = store.get(storeKey);
        if (value == null) {
            return Optional.empty();
        }
        // 简单类型转换，生产环境应使用 Jackson 反序列化
        if (type == String.class) {
            return Optional.of((T) value);
        }
        return Optional.of((T) value);
    }

    /**
     * 关键词搜索记忆。
     *
     * <p>在内存存储中遍历所有匹配用户和分类的记忆，
     * 检查内容是否包含查询关键词（大小写不敏感）。
     *
     * @param userId   用户标识（支持 * 通配）
     * @param category 记忆分类（支持 * 通配）
     * @param query    搜索关键词
     * @return 匹配的记忆内容列表
     */
    public List<String> searchMemories(String userId, String category, String query) {
        String prefix = buildKeyPrefix(userId, category);
        String lowerQuery = (query != null) ? query.toLowerCase() : "";

        return store.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .filter(e -> lowerQuery.isEmpty()
                        || e.getValue().toLowerCase().contains(lowerQuery))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    /**
     * 搜索指定用户所有分类下的记忆。
     *
     * @param userId 用户标识
     * @param query  搜索关键词
     * @return 匹配的记忆内容列表
     */
    public List<String> searchMemories(String userId, String query) {
        return searchMemories(userId, "*", query);
    }

    /**
     * 删除单条记忆。
     *
     * @param userId 用户标识
     * @param key    记忆键名
     */
    public void deleteMemory(String userId, String key) {
        String storeKey = userId + ":" + key;
        store.remove(storeKey);
    }

    /**
     * 列出某分类下的所有记忆。
     *
     * @param userId   用户标识
     * @param category 记忆分类（null 或 * 表示所有分类）
     * @return 记忆内容列表
     */
    public List<String> listMemories(String userId, String category) {
        String prefix = buildKeyPrefix(userId, category);
        return store.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    /**
     * 清除用户的所有记忆。
     *
     * @param userId 用户标识
     */
    public void clearUserMemories(String userId) {
        String prefix = userId + ":";
        store.keySet().removeIf(k -> k.startsWith(prefix));
    }

    // ==================== 统计与管理 ====================

    /**
     * 获取存储系统中的记忆总数。
     *
     * @return 记忆条数
     */
    public long totalMemories() {
        return store.size();
    }

    /**
     * 获取指定用户的记忆数量。
     *
     * @param userId 用户标识
     * @return 该用户的记忆条数
     */
    public long userMemoryCount(String userId) {
        String prefix = userId + ":";
        return store.keySet().stream().filter(k -> k.startsWith(prefix)).count();
    }

    // ==================== 内部工具方法 ====================

    /**
     * 构建存储 Key：{userId}:{category}:{key}
     */
    private String buildKey(String userId, String category, String key) {
        return userId + ":" + category + ":" + key;
    }

    /**
     * 构建存储 Key 前缀：{userId}:{category}:
     * 支持通配符 * 表示匹配所有
     */
    private String buildKeyPrefix(String userId, String category) {
        String u = (userId == null || "*".equals(userId)) ? "" : userId;
        String c = (category == null || "*".equals(category)) ? "" : category;
        return u + ":" + c + ":";
    }
}
