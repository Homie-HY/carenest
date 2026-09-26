package com.carenest.nursing.ai.assistant;

import com.carenest.ai.tool.ToolCallRecord;
import lombok.Data;

import java.util.List;

/**
 * 非流式对话结果。
 * <p>
 * 除最终答案外，一并返回本轮的工具调用轨迹，便于前端展示“查询了哪些数据”，
 * 也便于人工核对模型是否基于真实数据作答。
 *
 * @author Homie
 */
@Data
public class ChatResult {

    /** 会话 id */
    private String sessionId;

    /** 模型最终答案 */
    private String answer;

    /** 本轮工具调用轨迹（可能为空） */
    private List<ToolCallRecord> toolCalls;

    public ChatResult(String sessionId, String answer, List<ToolCallRecord> toolCalls) {
        this.sessionId = sessionId;
        this.answer = answer;
        this.toolCalls = toolCalls;
    }
}
