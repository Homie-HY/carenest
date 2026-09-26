package com.carenest.nursing.ai.dto;

import com.carenest.nursing.domain.Elder;
import lombok.Data;

/**
 * 老人精简信息（脱敏）。
 * <p>
 * <b>安全红线</b>：本 DTO 只允许出现 id、name、sex、age、bedNumber、status 六个字段，
 * 严禁加入 {@code idCardNo}、{@code phone}、{@code address}、{@code idCardPortraitImg}、
 * {@code idCardNationalEmblemImg}、{@code birthday}。任何工具都不得把 {@link Elder} 实体直接返回给模型。
 *
 * @author Homie
 */
@Data
public class ElderBriefDto {

    private Long id;

    private String name;

    /** 性别文案（男/女） */
    private String sex;

    /** 由出生日期换算的周岁年龄，出生日期本身不外泄 */
    private Integer age;

    private String bedNumber;

    /** 状态文案（启用/入住中/已退住等） */
    private String status;

    public static ElderBriefDto from(Elder elder) {
        ElderBriefDto dto = new ElderBriefDto();
        dto.setId(elder.getId());
        dto.setName(elder.getName());
        dto.setSex(AiText.elderSex(elder.getSex()));
        dto.setAge(AiText.ageFromBirthday(elder.getBirthday()));
        dto.setBedNumber(elder.getBedNumber());
        dto.setStatus(AiText.elderStatus(elder.getStatus()));
        return dto;
    }
}
