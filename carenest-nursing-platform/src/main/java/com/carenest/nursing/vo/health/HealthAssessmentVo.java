package com.carenest.nursing.vo.health;

import lombok.Data;

/**
 *  健康评估类
 * @author Homie
 */
@Data
public class HealthAssessmentVo {
    /**
     * 健康风险等级
     */
    private String riskLevel;
    /**
     * 健康指数
     */
    private double healthIndex;

}