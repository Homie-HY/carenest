package com.carenest.ai.tool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工具执行装饰器：在真实执行前后串入审计轨迹与进度事件。
 * <p>
 * 用装饰器而不是让每个 Tool 类自己回调，是因为 langchain4j 0.35.0 的 {@code TokenStream}
 * 不提供工具执行事件，而 {@code AiServices.tools(Map<ToolSpecification, ToolExecutor>)}
 * 恰好开放了这个注入点——统一在一处拦截，业务 Tool 类保持纯净。
 *
 * @author qoder
 */
@Slf4j
public class InstrumentedToolExecutor implements ToolExecutor {

    /** 入参落库的最大长度，防止模型给出超长 JSON 撑爆审计字段 */
    private static final int MAX_ARGUMENT_LENGTH = 2000;

    /** 返回值落库的最大长度 */
    private static final int MAX_RESULT_LENGTH = 4000;

    private final ToolExecutor delegate;

    private final ToolCallSink sink;

    private final String toolDescription;

    public InstrumentedToolExecutor(ToolExecutor delegate, ToolCallSink sink, String toolDescription) {
        this.delegate = delegate;
        this.sink = sink;
        this.toolDescription = toolDescription;
    }

    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        ToolCallRecord record = new ToolCallRecord();
        record.setToolName(request.name());
        record.setToolDescription(toolDescription);
        record.setArguments(truncate(request.arguments(), MAX_ARGUMENT_LENGTH));
        record.setStartTime(System.currentTimeMillis());

        sink.onToolStart(record);

        String result;
        try {
            result = delegate.execute(request, memoryId);
            record.setSuccess(true);
        } catch (Exception e) {
            // 工具异常不能把整轮对话打断，把错误信息交回模型，由它向用户解释
            log.error("工具执行异常，toolName={}, arguments={}", request.name(), request.arguments(), e);
            result = "工具调用失败：" + e.getMessage();
            record.setSuccess(false);
        }

        record.setCostMs(System.currentTimeMillis() - record.getStartTime());
        record.setResult(truncate(result, MAX_RESULT_LENGTH));
        sink.record(record);
        sink.onToolComplete(record);
        return result;
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...(已截断，原长 " + text.length() + " 字符)";
    }
}
