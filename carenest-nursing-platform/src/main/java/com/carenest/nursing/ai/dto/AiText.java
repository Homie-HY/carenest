package com.carenest.nursing.ai.dto;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;

/**
 * AI 工具层的字段文案转换工具。
 * <p>
 * 领域实体里大量用 int 编码状态（性别、入住状态、床位状态等），直接丢给模型可读性差、
 * 还容易被误解。这里统一转成中文文案，顺带把出生日期换算成年龄——
 * 年龄可以暴露，出生日期本身不进 DTO。
 *
 * @author qoder
 */
public final class AiText {

    private static final DateTimeFormatter[] BIRTHDAY_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyyMMdd")
    };

    private AiText() {
    }

    /** 性别（0:女 1:男） */
    public static String elderSex(Integer sex) {
        if (sex == null) {
            return "未知";
        }
        return sex == 1 ? "男" : "女";
    }

    /** 老人状态（0禁用 1启用 2请假 3退住中 4入住中 5已退住） */
    public static String elderStatus(Integer status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case 0: return "禁用";
            case 1: return "启用";
            case 2: return "请假";
            case 3: return "退住中";
            case 4: return "入住中";
            case 5: return "已退住";
            default: return "未知";
        }
    }

    /** 床位状态（0未入住 1已入住 2入住申请中） */
    public static String bedStatus(Integer status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case 0: return "未入住";
            case 1: return "已入住";
            case 2: return "入住申请中";
            default: return "未知";
        }
    }

    /** 健康评估性别（0:男 1:女），注意与 elder 的编码相反 */
    public static String assessmentGender(Integer gender) {
        if (gender == null) {
            return "未知";
        }
        return gender == 0 ? "男" : "女";
    }

    /** 是否建议入住（0:建议 1:不建议） */
    public static String suggestionForAdmission(Integer v) {
        if (v == null) {
            return "未知";
        }
        return v == 0 ? "建议入住" : "不建议入住";
    }

    /** 入住情况（0:已入住 1:未入住） */
    public static String admissionStatus(Integer v) {
        if (v == null) {
            return "未知";
        }
        return v == 0 ? "已入住" : "未入住";
    }

    /** 通用启用/禁用（0禁用 1启用） */
    public static String enabledStatus(Integer status) {
        if (status == null) {
            return "未知";
        }
        return status == 1 ? "启用" : "禁用";
    }

    /**
     * 由出生日期字符串推算周岁年龄。解析不出返回 null，不阻塞主流程。
     */
    public static Integer ageFromBirthday(String birthday) {
        if (birthday == null || birthday.trim().isEmpty()) {
            return null;
        }
        String text = birthday.trim();
        for (DateTimeFormatter formatter : BIRTHDAY_FORMATS) {
            try {
                LocalDate birth = LocalDate.parse(text, formatter);
                return Period.between(birth, LocalDate.now()).getYears();
            } catch (Exception ignored) {
                // 尝试下一种格式
            }
        }
        return null;
    }
}
