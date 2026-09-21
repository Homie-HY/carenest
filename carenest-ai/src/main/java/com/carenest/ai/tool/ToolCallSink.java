package com.carenest.ai.tool;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 会话级工具调用汇集点。
 * <p>
 * AiServices 实例按会话缓存，而 {@link ToolCallListener} 需要按请求切换
 * （流式请求要推 SSE，非流式请求什么都不用推），所以把"轨迹收集"与"事件下发"合到这里，
 * 由 AssistantService 在每次请求前后 attach / drain / detach。
 * <p>
 * 线程安全前提：同一会话的请求由 AssistantService 串行化（每会话一把锁），
 * 因此 trace 队列在一次请求内只会有一个生产者链路。
 *
 * @author qoder
 */
@Slf4j
public class ToolCallSink implements ToolCallListener {

    private final Queue<ToolCallRecord> trace = new ConcurrentLinkedQueue<>();

    private final AtomicReference<ToolCallListener> downstream =
            new AtomicReference<>(ToolCallListener.NOOP);

    /**
     * 绑定本次请求的事件下发目标。
     */
    public void attach(ToolCallListener listener) {
        downstream.set(listener == null ? ToolCallListener.NOOP : listener);
    }

    /**
     * 解绑，避免请求结束后仍向已关闭的 SseEmitter 推送。
     */
    public void detach() {
        downstream.set(ToolCallListener.NOOP);
    }

    /**
     * 取出并清空已累计的工具轨迹。
     */
    public List<ToolCallRecord> drain() {
        List<ToolCallRecord> snapshot = new ArrayList<>(trace);
        trace.clear();
        return snapshot;
    }

    public void record(ToolCallRecord record) {
        trace.add(record);
    }

    @Override
    public void onToolStart(ToolCallRecord record) {
        dispatch(true, record);
    }

    @Override
    public void onToolComplete(ToolCallRecord record) {
        dispatch(false, record);
    }

    /**
     * 下发事件。监听器抛异常不能影响工具执行本身，吞掉并记日志。
     */
    private void dispatch(boolean start, ToolCallRecord record) {
        ToolCallListener listener = downstream.get();
        try {
            if (start) {
                listener.onToolStart(record);
            } else {
                listener.onToolComplete(record);
            }
        } catch (Exception e) {
            log.warn("工具调用事件下发失败，toolName={}", record.getToolName(), e);
        }
    }
}
