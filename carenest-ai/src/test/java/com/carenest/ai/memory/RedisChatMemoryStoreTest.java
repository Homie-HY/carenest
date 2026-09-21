package com.carenest.ai.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RedisChatMemoryStore} 单测。
 * <p>
 * 用 Mockito 打桩 {@link StringRedisTemplate}，不依赖真实 Redis，聚焦四件事：
 * 存（带 TTL）、取（正常反序列化）、删、以及 Redis 异常 / 脏数据时的降级行为。
 *
 * @author qoder
 */
class RedisChatMemoryStoreTest {

    private static final String MEMORY_ID = "1:sess-abc";
    private static final String EXPECTED_KEY = RedisChatMemoryStore.KEY_PREFIX + MEMORY_ID;
    private static final Duration TTL = Duration.ofDays(7);

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private RedisChatMemoryStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        store = new RedisChatMemoryStore(redisTemplate, TTL);
    }

    @Test
    @DisplayName("写入：序列化为 JSON 并带 TTL 落到约定 key")
    void updateMessages_shouldSerializeWithTtl() {
        List<ChatMessage> messages = Arrays.asList(UserMessage.from("你好"), AiMessage.from("您好，有什么可以帮您"));

        store.updateMessages(MEMORY_ID, messages);

        String expectedJson = ChatMessageSerializer.messagesToJson(messages);
        verify(valueOps, times(1)).set(eq(EXPECTED_KEY), eq(expectedJson), eq(TTL));
        verify(redisTemplate, never()).delete(EXPECTED_KEY);
    }

    @Test
    @DisplayName("写入空列表：等价于删除 key，不留空值占位")
    void updateMessages_emptyShouldDelete() {
        store.updateMessages(MEMORY_ID, Collections.emptyList());

        verify(redisTemplate, times(1)).delete(EXPECTED_KEY);
        verify(valueOps, never()).set(eq(EXPECTED_KEY), org.mockito.ArgumentMatchers.anyString(), eq(TTL));
    }

    @Test
    @DisplayName("读取：能还原写入的消息内容与顺序")
    void getMessages_shouldRoundTrip() {
        List<ChatMessage> messages = Arrays.asList(UserMessage.from("3号楼有哪些老人"), AiMessage.from("共 5 位"));
        when(valueOps.get(EXPECTED_KEY)).thenReturn(ChatMessageSerializer.messagesToJson(messages));

        List<ChatMessage> loaded = store.getMessages(MEMORY_ID);

        assertThat(loaded).hasSize(2);
        assertThat(loaded.get(0)).isInstanceOf(UserMessage.class);
        assertThat(((UserMessage) loaded.get(0)).singleText()).isEqualTo("3号楼有哪些老人");
        assertThat(loaded.get(1)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) loaded.get(1)).text()).isEqualTo("共 5 位");
    }

    @Test
    @DisplayName("读取：key 不存在时返回空列表而非 null")
    void getMessages_missingKeyReturnsEmpty() {
        when(valueOps.get(EXPECTED_KEY)).thenReturn(null);

        List<ChatMessage> loaded = store.getMessages(MEMORY_ID);

        assertThat(loaded).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("读取：Redis 抛异常时降级为空上下文，不向上抛")
    void getMessages_redisErrorDegradesToEmpty() {
        when(valueOps.get(EXPECTED_KEY)).thenThrow(new RuntimeException("connection refused"));

        List<ChatMessage> loaded = store.getMessages(MEMORY_ID);

        assertThat(loaded).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("读取：脏数据反序列化失败时清空该会话记忆并返回空，避免会话永久卡死")
    void getMessages_corruptJsonClearsAndReturnsEmpty() {
        when(valueOps.get(EXPECTED_KEY)).thenReturn("{not-a-valid-chat-message-json");

        List<ChatMessage> loaded = store.getMessages(MEMORY_ID);

        assertThat(loaded).isNotNull().isEmpty();
        verify(redisTemplate, times(1)).delete(EXPECTED_KEY);
    }

    @Test
    @DisplayName("删除：按约定 key 删除")
    void deleteMessages_shouldDeleteKey() {
        store.deleteMessages(MEMORY_ID);

        verify(redisTemplate, times(1)).delete(EXPECTED_KEY);
    }

    @Test
    @DisplayName("key 规则：前缀 + memoryId")
    void buildKey_shouldUsePrefix() {
        assertThat(RedisChatMemoryStore.buildKey(MEMORY_ID)).isEqualTo("ai:memory:" + MEMORY_ID);
    }
}
