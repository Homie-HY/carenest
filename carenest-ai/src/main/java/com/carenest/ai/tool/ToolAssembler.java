package com.carenest.ai.tool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把带 {@code @Tool} 注解的业务对象装配成 langchain4j 可识别的执行器映射，
 * 并在装配过程中统一套上 {@link InstrumentedToolExecutor}。
 * <p>
 * 之所以不用 {@code AiServices.tools(Object...)}：那条路径不开放 ToolExecutor 注入点，
 * 拿不到工具调用的入参与返回值，审计与前端进度提示都无从做起。
 *
 * @author qoder
 */
@Slf4j
public final class ToolAssembler {

    private ToolAssembler() {
    }

    /**
     * 装配工具。
     *
     * @param toolObjects 业务工具对象，通常已按会话注入了 userId / deptId
     * @param sink        轨迹汇集点
     * @return 可直接传给 {@code AiServices.builder().tools(...)} 的映射
     */
    public static Map<ToolSpecification, ToolExecutor> assemble(List<Object> toolObjects, ToolCallSink sink) {
        Map<ToolSpecification, ToolExecutor> result = new LinkedHashMap<>();
        for (Object toolObject : toolObjects) {
            for (Method method : toolObject.getClass().getDeclaredMethods()) {
                if (!method.isAnnotationPresent(Tool.class)) {
                    continue;
                }
                // 工具方法允许是 package-private / protected，反射调用前放开可见性
                method.setAccessible(true);
                ToolSpecification specification = ToolSpecifications.toolSpecificationFrom(method);
                ToolExecutor delegate = new DefaultToolExecutor(toolObject, method);
                result.put(specification,
                        new InstrumentedToolExecutor(delegate, sink, specification.description()));
            }
        }
        if (result.isEmpty()) {
            log.warn("未装配到任何 @Tool 方法，助手将退化为无工具的普通问答");
        }
        return result;
    }
}
