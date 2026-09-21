package com.carenest.nursing.ai.assistant;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 护理助手对话请求体。
 *
 * @author qoder
 */
@Data
@ApiModel("护理助手对话请求")
public class ChatRequest {

    /** 会话 id，由后端创建后下发；同一会话内保持上下文 */
    @ApiModelProperty("会话id")
    private String sessionId;

    /** 用户本轮输入 */
    @ApiModelProperty("用户输入内容")
    private String message;
}
