package com.carenest.ai.tool;

/**
 * 工具调用事件监听器。
 * <p>
 * langchain4j 0.35.0 的 {@code TokenStream} 只有 {@code onNext / onComplete / onError}，
 * 没有暴露工具执行事件（{@code onToolExecuted} 是更高版本才加的）。
 * 因此前端"正在查询老人档案..."这类进度提示改由本监听器驱动，
 * 流式接口把它接到 SseEmitter 上，非流式接口用 {@link #NOOP}。
 * <p>
 * 实现类的回调运行在工具执行线程上，必须自己保证线程安全，且不得抛出异常。
 *
 * @author qoder
 */
public interface ToolCallListener {

    /** 空实现，非流式场景使用 */
    ToolCallListener NOOP = new ToolCallListener() {
        @Override
        public void onToolStart(ToolCallRecord record) {
            // 无需处理
        }

        @Override
        public void onToolComplete(ToolCallRecord record) {
            // 无需处理
        }
    };

    /**
     * 工具开始执行。
     */
    void onToolStart(ToolCallRecord record);

    /**
     * 工具执行结束（无论成功失败）。
     */
    void onToolComplete(ToolCallRecord record);
}
