package com.carenest.nursing.ai.dto;

import lombok.Data;

import java.util.List;

/**
 * 列表类工具的统一分页返回结构。
 * <p>
 * 所有列表工具都强制分页（单次最多 {@code MAX_ITEMS} 条），并在 {@code note} 里附
 * “共 N 条，已显示前 M 条”提示，既控制 token 消耗，也让模型能如实告知用户结果被截断。
 *
 * @param <T> 列表元素类型，必须是脱敏 DTO
 * @author Homie
 */
@Data
public class ToolPageDto<T> {

    /** 满足条件的总条数 */
    private long total;

    /** 本次实际返回的条数 */
    private int shown;

    /** 截断提示，未截断时为 null */
    private String note;

    private List<T> items;
}
