package com.carenest.nursing.ai.dto;

import com.carenest.nursing.domain.HealthAssessment;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 健康评估精简信息（脱敏）。
 * <p>
 * <b>安全红线</b>：严禁包含 {@code idCard}（身份证号）与 {@code physicalReportUrl}（体检报告原件链接），
 * 也不含 {@code birthDate}（出生日期）——只暴露换算后的 {@code age}。
 * 不得把 {@link HealthAssessment} 实体直接返回给模型。
 *
 * @author qoder
 */
@Data
public class HealthAssessmentBriefDto {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private String elderName;

    private Integer age;

    private String gender;

    private String healthScore;

    /** 危险等级（健康/提示/风险/危险/严重危险） */
    private String riskLevel;

    private String nursingLevelName;

    private String suggestionForAdmission;

    private String admissionStatus;

    private String totalCheckDate;

    private String physicalExamInstitution;

    private String assessmentTime;

    private String reportSummary;

    private String diseaseRisk;

    private String abnormalAnalysis;

    public static HealthAssessmentBriefDto from(HealthAssessment ha) {
        HealthAssessmentBriefDto dto = new HealthAssessmentBriefDto();
        dto.setElderName(ha.getElderName());
        dto.setAge(ha.getAge());
        dto.setGender(AiText.assessmentGender(ha.getGender()));
        dto.setHealthScore(ha.getHealthScore());
        dto.setRiskLevel(ha.getRiskLevel());
        dto.setNursingLevelName(ha.getNursingLevelName());
        dto.setSuggestionForAdmission(AiText.suggestionForAdmission(ha.getSuggestionForAdmission()));
        dto.setAdmissionStatus(AiText.admissionStatus(ha.getAdmissionStatus()));
        dto.setTotalCheckDate(ha.getTotalCheckDate());
        dto.setPhysicalExamInstitution(ha.getPhysicalExamInstitution());
        dto.setAssessmentTime(formatTime(ha.getAssessmentTime()));
        dto.setReportSummary(ha.getReportSummary());
        dto.setDiseaseRisk(ha.getDiseaseRisk());
        dto.setAbnormalAnalysis(ha.getAbnormalAnalysis());
        return dto;
    }

    private static String formatTime(LocalDateTime time) {
        return time == null ? null : time.format(TIME_FORMATTER);
    }
}
