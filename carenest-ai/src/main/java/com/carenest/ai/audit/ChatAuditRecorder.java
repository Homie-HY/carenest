package com.carenest.ai.audit;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.carenest.ai.config.DeepSeekProperties;
import com.carenest.ai.domain.AiChatLog;
import com.carenest.ai.mapper.AiChatLogMapper;
import com.carenest.ai.tool.ToolCallRecord;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 对话审计记录器。
 * <p>
 * 医疗相关场景要求可追溯，所以审计与对话同轮落库，不做"先上线后补"。
 * 两条硬约束：
 * <ol>
 *   <li>审计写入失败绝不能让对话接口失败——异常在此吞掉并打 error 日志；</li>
 *   <li>审计写入可能发生在流式回调线程上（无请求上下文），因此本类不得依赖
 *       {@code SecurityUtils} / {@code HttpServletRequest}，所有身份信息都由调用方在请求线程上取好后传入。</li>
 * </ol>
 *
 * @author qoder
 */
@Slf4j
@Component
public class ChatAuditRecorder {

    private final AiChatLogMapper aiChatLogMapper;

    private final DeepSeekProperties properties;

    public ChatAuditRecorder(AiChatLogMapper aiChatLogMapper, DeepSeekProperties properties) {
        this.aiChatLogMapper = aiChatLogMapper;
        this.properties = properties;
    }

    /**
     * 开启一轮审计记录。必须在请求线程上调用，身份信息此刻才取得到。
     */
    public AiChatLog newRound(Long userId, Long deptId, String userRole, String sessionId, String userInput) {
        AiChatLog entity = new AiChatLog();
        entity.setSessionId(sessionId);
        entity.setUserId(userId);
        entity.setDeptId(deptId);
        entity.setUserRole(userRole);
        entity.setModel(properties.getModel());
        entity.setUserInput(userInput);
        entity.setCreateTime(new Date());
        return entity;
    }

    /**
     * 记录成功的一轮。
     *
     * @param entity    {@link #newRound} 产出的对象
     * @param startedAt 本轮开始的时间戳（毫秒）
     */
    public void recordSuccess(AiChatLog entity, String modelOutput, List<ToolCallRecord> toolCalls,
                              TokenUsage tokenUsage, long startedAt) {
        entity.setModelOutput(modelOutput);
        entity.setSuccess(1);
        fillCommon(entity, toolCalls, tokenUsage, startedAt);
        insert(entity);
    }

    /**
     * 记录失败的一轮。失败同样要留痕，否则出问题时无从复盘。
     */
    public void recordFailure(AiChatLog entity, List<ToolCallRecord> toolCalls, Throwable error, long startedAt) {
        entity.setSuccess(0);
        entity.setErrorMsg(briefMessage(error));
        fillCommon(entity, toolCalls, null, startedAt);
        insert(entity);
    }

    /**
     * 查询某会话的历史对话，用于前端刷新后回显。
     * <p>
     * 强制带上 userId 条件：即使调用方传错了 sessionId，也不会读到别人的对话。
     *
     * @param limit 最多返回的轮数
     * @return 按时间正序（便于前端直接渲染）
     */
    public List<AiChatLog> listBySession(Long userId, String sessionId, int limit) {
        try {
            LambdaQueryWrapper<AiChatLog> wrapper = new LambdaQueryWrapper<AiChatLog>()
                    .eq(AiChatLog::getUserId, userId)
                    .eq(AiChatLog::getSessionId, sessionId)
                    .orderByDesc(AiChatLog::getId)
                    .last("limit " + Math.max(1, limit));
            List<AiChatLog> list = aiChatLogMapper.selectList(wrapper);
            Collections.reverse(list);
            return list;
        } catch (Exception e) {
            log.error("查询对话历史失败，userId={}, sessionId={}", userId, sessionId, e);
            return Collections.emptyList();
        }
    }

    private void fillCommon(AiChatLog entity, List<ToolCallRecord> toolCalls,
                            TokenUsage tokenUsage, long startedAt) {
        entity.setCostMs(System.currentTimeMillis() - startedAt);
        entity.setToolCalls(toolCalls == null || toolCalls.isEmpty() ? null : JSON.toJSONString(toolCalls));
        if (tokenUsage != null) {
            entity.setInputTokens(tokenUsage.inputTokenCount());
            entity.setOutputTokens(tokenUsage.outputTokenCount());
            entity.setTotalTokens(tokenUsage.totalTokenCount());
        }
    }

    private void insert(AiChatLog entity) {
        try {
            aiChatLogMapper.insert(entity);
        } catch (Exception e) {
            // 审计失败不阻断对话，但必须留下足够定位的日志
            log.error("AI对话审计写入失败，sessionId={}, userId={}, userInput={}",
                    entity.getSessionId(), entity.getUserId(), entity.getUserInput(), e);
        }
    }

    /**
     * 异常摘要。堆栈不进库（太长且没有查询价值），只留类型与消息。
     */
    private static String briefMessage(Throwable error) {
        if (error == null) {
            return "unknown";
        }
        String message = error.getMessage() == null ? "" : error.getMessage();
        String brief = error.getClass().getSimpleName() + ": " + message;
        return brief.length() > 500 ? brief.substring(0, 500) : brief;
    }
}
