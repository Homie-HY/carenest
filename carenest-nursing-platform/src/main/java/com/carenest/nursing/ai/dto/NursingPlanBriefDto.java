package com.carenest.nursing.ai.dto;

import com.carenest.nursing.domain.NursingPlan;
import lombok.Data;

/**
 * 护理计划精简信息。
 *
 * @author Homie
 */
@Data
public class NursingPlanBriefDto {

    private Long id;

    private String planName;

    private String status;

    private Integer sortNo;

    public static NursingPlanBriefDto from(NursingPlan plan) {
        NursingPlanBriefDto dto = new NursingPlanBriefDto();
        dto.setId(plan.getId());
        dto.setPlanName(plan.getPlanName());
        dto.setStatus(AiText.enabledStatus(plan.getStatus()));
        dto.setSortNo(plan.getSortNo());
        return dto;
    }
}
