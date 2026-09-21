package com.carenest.ai.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 Redis 的会话记忆存储。
 * <p>
 * 取代 langchain4j 默认的 InMemoryChatMemoryStore：默认实现进程重启即丢、多实例之间不共享，
 * 护理场景的对话上下文必须跨重启可续。
 * <p>
 * 这里注入 {@link StringRedisTemplate} 而不是项目通用的 {@code RedisTemplate<Object,Object>}：
 * 后者的 value 序列化器是 FastJson2JsonRedisSerializer，会把已经是 JSON 的消息串再包一层引号，
 * 读写虽然对称但排查问题时 redis-cli 里看到的不是原始 JSON。
 *
 * @author qoder
 */
@Slf4j
public class RedisChatMemoryStore implements ChatMemoryStore {

    /** Redis key 前缀，形如 ai:memory:1:sess-xxxx */
    public static final String KEY_PREFIX = "ai:memory:";

    private final StringRedisTemplate redisTemplate;

    private final Duration ttl;

    public RedisChatMemoryStore(StringRedisTemplate redisTemplate, Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = buildKey(memoryId);
        String json;
        try {
            json = redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            // Redis 不可用时降级为空上下文，宁可丢记忆也不能让对话接口整体不可用
            log.error("读取会话记忆失败，降级为空上下文，memoryId={}", memoryId, e);
            return new ArrayList<>();
        }
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return ChatMessageDeserializer.messagesFromJson(json);
        } catch (Exception e) {
            // 历史数据格式不兼容（例如 langchain4j 升级后序列化结构变化）时清空重建，避免会话永久卡死
            log.error("会话记忆反序列化失败，已清空该会话记忆，memoryId={}", memoryId, e);
            deleteMessages(memoryId);
            return new ArrayList<>();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = buildKey(memoryId);
        // messages 为空表示会话已被重置，直接删 key，不留空值占位
        if (messages == null || messages.isEmpty()) {
            redisTemplate.delete(key);
            return;
        }
        String json = ChatMessageSerializer.messagesToJson(messages);
        try {
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (Exception e) {
            log.error("写入会话记忆失败，memoryId={}", memoryId, e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        try {
            redisTemplate.delete(buildKey(memoryId));
        } catch (Exception e) {
            log.error("删除会话记忆失败，memoryId={}", memoryId, e);
        }
    }

    /**
     * 构造 Redis key。memoryId 约定为 {@code {userId}:{sessionId}}，
     * 由 {@link ChatMemoryFactory#memoryId(Long, String)} 生成。
     */
    public static String buildKey(Object memoryId) {
        return KEY_PREFIX + memoryId;
    }
}
