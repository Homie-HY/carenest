package com.carenest.ai.memory;

import lombok.Data;

import java.io.Serializable;

/**
 * 会话元信息，存于 Redis Hash {@code ai:sessions:{userId}} 的 value 中。
 * <p>
 * 只放展示与排序需要的字段，不放对话内容——对话内容在 {@link RedisChatMemoryStore} 里。
 *
 * @author qoder
 */
@Data
public class ChatSessionMeta implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话 id，服务端生成的 UUID */
    private String sessionId;

    /** 归属用户 id，冗余存放便于排查 */
    private Long userId;

    /** 会话标题，默认取首条用户消息的前 20 字 */
    private String title;

    /** 创建时间，毫秒时间戳 */
    private Long createTime;

    /** 最近一次对话时间，毫秒时间戳，用于会话列表排序 */
    private Long lastActiveTime;

    /** 累计对话轮数 */
    private Integer roundCount;
}
