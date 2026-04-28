package com.emailassistant.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class MemoryStoreService {

    private static final Logger log = LoggerFactory.getLogger(MemoryStoreService.class);
    private static final String KEY_PREFIX = "memory:";

    /** Redis 模板，当 Redis 不可用时为 null */
    private final StringRedisTemplate redisTemplate;

    /** 内存存储后备方案 */
    private final Map<String, String> fallbackStore = new ConcurrentHashMap<>();

    public MemoryStoreService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        if (redisTemplate != null) {
            try {
                redisTemplate.getConnectionFactory().getConnection().ping();
                log.info("Redis 连接成功，使用 Redis 存储记忆");
            } catch (Exception e) {
                log.warn("Redis 不可用，回退到内存存储: {}", e.getMessage());
            }
        } else {
            log.info("RedisTemplate 未注入，使用内存存储记忆");
        }
    }

    private boolean isRedisAvailable() {
        try {
            return redisTemplate != null
                    && redisTemplate.getConnectionFactory() != null
                    && redisTemplate.getConnectionFactory().getConnection() != null
                    && "PONG".equalsIgnoreCase(redisTemplate.getConnectionFactory().getConnection().ping());
        } catch (Exception e) {
            return false;
        }
    }

    public void saveMemory(String userId, String category, String key, Object value, int ttlDays) {
        String storeKey = buildKey(userId, category, key);
        String storeValue = value != null ? value.toString() : "";
        if (isRedisAvailable()) {
            redisTemplate.opsForValue().set(storeKey, storeValue, Duration.ofDays(ttlDays));
        } else {
            fallbackStore.put(storeKey, storeValue);
        }
    }

    public void saveMemory(String userId, String category, String key, Object value) {
        saveMemory(userId, category, key, value, 30);
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> getMemory(String userId, String key, Class<T> type) {
        String storeKey = userId + ":" + key;
        String value;
        if (isRedisAvailable()) {
            value = redisTemplate.opsForValue().get(storeKey);
        } else {
            value = fallbackStore.get(storeKey);
        }
        if (value == null) return Optional.empty();
        if (type == String.class) return Optional.of((T) value);
        return Optional.of((T) value);
    }

    public List<String> searchMemories(String userId, String category, String query) {
        String prefix = buildKeyPrefix(userId, category);
        String lowerQuery = (query != null) ? query.toLowerCase() : "";

        if (isRedisAvailable()) {
            Set<String> keys = redisTemplate.keys(prefix + "*");
            if (keys == null || keys.isEmpty()) return List.of();
            List<String> results = new ArrayList<>();
            for (String key : keys) {
                String val = redisTemplate.opsForValue().get(key);
                if (val != null && (lowerQuery.isEmpty() || val.toLowerCase().contains(lowerQuery))) {
                    results.add(val);
                }
            }
            return results;
        } else {
            return fallbackStore.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(prefix))
                    .filter(e -> lowerQuery.isEmpty()
                            || e.getValue().toLowerCase().contains(lowerQuery))
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toList());
        }
    }

    public List<String> searchMemories(String userId, String query) {
        return searchMemories(userId, "*", query);
    }

    public void deleteMemory(String userId, String key) {
        String storeKey = userId + ":" + key;
        if (isRedisAvailable()) {
            redisTemplate.delete(storeKey);
        } else {
            fallbackStore.remove(storeKey);
        }
    }

    public List<String> listMemories(String userId, String category) {
        String prefix = buildKeyPrefix(userId, category);
        if (isRedisAvailable()) {
            Set<String> keys = redisTemplate.keys(prefix + "*");
            if (keys == null || keys.isEmpty()) return List.of();
            return keys.stream()
                    .map(k -> redisTemplate.opsForValue().get(k))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } else {
            return fallbackStore.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(prefix))
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toList());
        }
    }

    public void clearUserMemories(String userId) {
        String prefix = userId + ":";
        if (isRedisAvailable()) {
            Set<String> keys = redisTemplate.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } else {
            fallbackStore.keySet().removeIf(k -> k.startsWith(prefix));
        }
    }

    public long totalMemories() {
        if (isRedisAvailable()) {
            Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
            return keys != null ? keys.size() : 0;
        }
        return fallbackStore.size();
    }

    public long userMemoryCount(String userId) {
        String prefix = userId + ":";
        if (isRedisAvailable()) {
            Set<String> keys = redisTemplate.keys(prefix + "*");
            return keys != null ? keys.size() : 0;
        }
        return fallbackStore.keySet().stream().filter(k -> k.startsWith(prefix)).count();
    }

    private String buildKey(String userId, String category, String key) {
        return KEY_PREFIX + userId + ":" + category + ":" + key;
    }

    private String buildKeyPrefix(String userId, String category) {
        String u = (userId == null || "*".equals(userId)) ? "" : userId;
        String c = (category == null || "*".equals(category)) ? "" : category;
        return KEY_PREFIX + u + ":" + c + ":";
    }
}
