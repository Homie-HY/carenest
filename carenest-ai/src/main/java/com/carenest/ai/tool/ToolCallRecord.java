package com.carenest.ai.tool;

import lombok.Data;

import java.io.Serializable;

/**
 * 单次工具调用的轨迹记录，用于审计留痕与前端"正在查询..."提示。
 *
 * @author Homie
 */
@Data
public class ToolCallRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 工具方法名，即 {@code @Tool} 标注的方法名 */
    private String toolName;

    /** 面向前端的中文说明，取自 {@code @Tool} 的 value */
    private String toolDescription;

    /** 模型给出的入参 JSON */
    private String arguments;

    /** 工具返回值，已截断 */
    private String result;

    /** 是否执行成功 */
    private boolean success;

    /** 开始时间戳（毫秒） */
    private long startTime;

    /** 耗时（毫秒） */
    private long costMs;
}
