package com.carenest.nursing.ai.tool;

import com.alibaba.fastjson2.JSON;
import com.carenest.nursing.ai.dto.ToolPageDto;

import java.util.List;

/**
 * 工具返回值的统一构造器。
 * <p>
 * 工具方法一律返回 {@code String}（结构化数据序列化成 JSON，无结果时返回自然语言提示），
 * 这样既能把截断信息一并带给模型，也避免把领域实体对象直接暴露出去。
 * <p>
 * <b>Token 控制</b>：所有列表工具强制走 {@link #page}，单次最多返回 {@link #MAX_ITEMS} 条。
 *
 * @author Homie
 */
public final class ToolResults {

    /** 列表类工具单次最多返回条数 */
    public static final int MAX_ITEMS = 20;

    private ToolResults() {
    }

    /**
     * 构造分页结果 JSON。
     *
     * @param total 满足条件的总条数（可能大于 items.size()）
     * @param items 本次返回的条目（调用方应已在 DB 层 limit 到 {@link #MAX_ITEMS}）
     */
    public static <T> String page(long total, List<T> items) {
        ToolPageDto<T> page = new ToolPageDto<>();
        int shown = items == null ? 0 : items.size();
        page.setTotal(total);
        page.setShown(shown);
        page.setItems(items);
        if (total > shown) {
            page.setNote("共 " + total + " 条，已显示前 " + shown + " 条");
        }
        return JSON.toJSONString(page);
    }

    /**
     * 单个对象结果 JSON。
     */
    public static String single(Object dto) {
        return JSON.toJSONString(dto);
    }

    /**
     * 无结果 / 提示类文本，直接返回自然语言，模型据此如实答复用户。
     */
    public static String text(String message) {
        return message;
    }
}
