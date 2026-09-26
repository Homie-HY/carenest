package com.carenest.ai.memory;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 会话注册表：维护"某个用户有哪些会话"的索引。
 * <p>
 * 之所以要单独建索引，是因为 Redis 里会话记忆是 {@code ai:memory:{userId}:{sessionId}} 一个个独立 key，
 * 想列出某用户的全部会话只能靠 {@code KEYS} 模式匹配——那是 O(N) 阻塞命令，生产环境不能用。
 * 这里改用每个用户一个 Hash（{@code ai:sessions:{userId}}），列举变成一次 HGETALL。
 * <p>
 * Hash 的 key 里带 userId，天然做到跨用户不可见。
 *
 * @author Homie
 */
@Slf4j
public class ChatSessionRegistry {

    /** 会话索引 key 前缀 */
    public static final String INDEX_PREFIX = "ai:sessions:";

    /** 单个用户最多保留的会话数，超出后淘汰最久未活跃的 */
    private static final int MAX_SESSIONS_PER_USER = 50;

    private final StringRedisTemplate redisTemplate;

    private final Duration ttl;

    public ChatSessionRegistry(StringRedisTemplate redisTemplate, Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    /**
     * 登记一个新会话。
     */
    public ChatSessionMeta create(Long userId, String sessionId, String title) {
        long now = System.currentTimeMillis();
        ChatSessionMeta meta = new ChatSessionMeta();
        meta.setSessionId(sessionId);
        meta.setUserId(userId);
        meta.setTitle(title);
        meta.setCreateTime(now);
        meta.setLastActiveTime(now);
        meta.setRoundCount(0);
        save(userId, meta);
        evictIfNeeded(userId);
        return meta;
    }

    /**
     * 一次对话结束后刷新活跃时间与轮数。
     *
     * @param fallbackTitle 会话还没有标题时用它兜底（取首条用户消息）
     */
    public ChatSessionMeta touch(Long userId, String sessionId, String fallbackTitle) {
        ChatSessionMeta meta = get(userId, sessionId);
        if (meta == null) {
            return create(userId, sessionId, fallbackTitle);
        }
        meta.setLastActiveTime(System.currentTimeMillis());
        meta.setRoundCount((meta.getRoundCount() == null ? 0 : meta.getRoundCount()) + 1);
        if (isBlank(meta.getTitle()) && !isBlank(fallbackTitle)) {
            meta.setTitle(fallbackTitle);
        }
        save(userId, meta);
        return meta;
    }

    public ChatSessionMeta get(Long userId, String sessionId) {
        Object raw = redisTemplate.opsForHash().get(indexKey(userId), sessionId);
        if (raw == null) {
            return null;
        }
        try {
            return JSON.parseObject(raw.toString(), ChatSessionMeta.class);
        } catch (Exception e) {
            log.error("会话元信息解析失败，userId={}, sessionId={}", userId, sessionId, e);
            return null;
        }
    }

    /**
     * 会话归属校验：确认 sessionId 确实属于该 userId。
     * <p>
     * 记忆 key 本身已按 userId 隔离，伪造 sessionId 读不到别人的上下文；
     * 这里再校验一次是为了避免把伪造的 sessionId 写进审计日志、污染会话列表。
     */
    public boolean owns(Long userId, String sessionId) {
        return get(userId, sessionId) != null;
    }

    /**
     * 按最近活跃时间倒序列出用户的全部会话。
     */
    public List<ChatSessionMeta> list(Long userId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(indexKey(userId));
        List<ChatSessionMeta> result = new ArrayList<>(entries.size());
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            try {
                ChatSessionMeta meta = JSON.parseObject(String.valueOf(entry.getValue()), ChatSessionMeta.class);
                if (meta != null) {
                    if (isBlank(meta.getSessionId())) {
                        meta.setSessionId(String.valueOf(entry.getKey()));
                    }
                    result.add(meta);
                }
            } catch (Exception e) {
                log.warn("跳过无法解析的会话元信息，userId={}, field={}", userId, entry.getKey());
            }
        }
        result.sort(Comparator.comparing(
                (ChatSessionMeta m) -> m.getLastActiveTime() == null ? 0L : m.getLastActiveTime()).reversed());
        return result;
    }

    public void rename(Long userId, String sessionId, String title) {
        ChatSessionMeta meta = get(userId, sessionId);
        if (meta == null) {
            return;
        }
        meta.setTitle(title);
        save(userId, meta);
    }

    /**
     * 删除会话索引。会话记忆由调用方通过 {@link ChatMemoryFactory#clear} 一并删除。
     */
    public boolean remove(Long userId, String sessionId) {
        Long removed = redisTemplate.opsForHash().delete(indexKey(userId), sessionId);
        return removed != null && removed > 0;
    }

    private void save(Long userId, ChatSessionMeta meta) {
        String key = indexKey(userId);
        redisTemplate.opsForHash().put(key, meta.getSessionId(), JSON.toJSONString(meta));
        // 索引 TTL 与记忆 TTL 保持一致，避免出现"会话还在列表里、上下文已过期"的悬空项
        redisTemplate.expire(key, ttl);
    }

    /**
     * 超出上限时淘汰最久未活跃的会话，防止单个用户的 Hash 无限膨胀。
     */
    private void evictIfNeeded(Long userId) {
        List<ChatSessionMeta> all = list(userId);
        if (all.size() <= MAX_SESSIONS_PER_USER) {
            return;
        }
        for (ChatSessionMeta meta : all.subList(MAX_SESSIONS_PER_USER, all.size())) {
            redisTemplate.opsForHash().delete(indexKey(userId), meta.getSessionId());
        }
    }

    private static String indexKey(Long userId) {
        return INDEX_PREFIX + userId;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
