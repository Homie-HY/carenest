package com.carenest.ai.memory;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

/**
 * 会话记忆工厂，同时实现 langchain4j 的 {@link ChatMemoryProvider}，
 * 可直接传给 {@code AiServices.builder().chatMemoryProvider(...)}。
 * <p>
 * memoryId 规则固定为 {@code {userId}:{sessionId}}：
 * 带上 userId 是为了让 Redis key 天然按用户隔离，
 * 即使前端伪造了别人的 sessionId 也读不到对方的上下文。
 *
 * @author qoder
 */
public class ChatMemoryFactory implements ChatMemoryProvider {

    private final RedisChatMemoryStore chatMemoryStore;

    private final int maxMessages;

    public ChatMemoryFactory(RedisChatMemoryStore chatMemoryStore, int maxMessages) {
        this.chatMemoryStore = chatMemoryStore;
        this.maxMessages = maxMessages;
    }

    @Override
    public ChatMemory get(Object memoryId) {
        return MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(maxMessages)
                .chatMemoryStore(chatMemoryStore)
                .build();
    }

    /**
     * 生成 memoryId。
     *
     * @param userId    登录用户 id，不可为空
     * @param sessionId 会话 id，由服务端生成，不可由前端自由指定后直接使用
     */
    public static String memoryId(Long userId, String sessionId) {
        return userId + ":" + sessionId;
    }

    /**
     * 清空某个会话的上下文，但保留会话本身（会话列表里仍可见）。
     */
    public void clear(Long userId, String sessionId) {
        chatMemoryStore.deleteMessages(memoryId(userId, sessionId));
    }

    public RedisChatMemoryStore getChatMemoryStore() {
        return chatMemoryStore;
    }

    public int getMaxMessages() {
        return maxMessages;
    }
}
