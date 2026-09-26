package com.carenest.nursing.ai.dto;

import com.carenest.nursing.domain.NursingProject;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 护理项目精简信息。
 *
 * @author Homie
 */
@Data
public class NursingProjectBriefDto {

    private Long id;

    private String name;

    private String unit;

    private BigDecimal price;

    private String nursingRequirement;

    private String status;

    public static NursingProjectBriefDto from(NursingProject project) {
        NursingProjectBriefDto dto = new NursingProjectBriefDto();
        dto.setId(project.getId());
        dto.setName(project.getName());
        dto.setUnit(project.getUnit());
        dto.setPrice(project.getPrice());
        dto.setNursingRequirement(project.getNursingRequirement());
        dto.setStatus(AiText.enabledStatus(project.getStatus()));
        return dto;
    }
}
