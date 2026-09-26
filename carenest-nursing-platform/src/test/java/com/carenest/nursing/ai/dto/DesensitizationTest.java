package com.carenest.nursing.ai.dto;

import com.alibaba.fastjson2.JSON;
import com.carenest.nursing.domain.Elder;
import com.carenest.nursing.domain.HealthAssessment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 脱敏 DTO 单测：这是护理助手的<b>安全红线回归测试</b>。
 * <p>
 * 一旦有人往 {@link ElderBriefDto} / {@link HealthAssessmentBriefDto} 里加了身份证、手机号、住址、
 * 证件照、体检报告原件等敏感字段，序列化后的 JSON 就会带上它们，本测试立即失败。
 * 断言分两层：既断言 JSON <b>不含敏感字段名</b>，也断言 <b>不含敏感字段的具体值</b>。
 *
 * @author Homie
 */
class DesensitizationTest {

    @Test
    @DisplayName("ElderBriefDto 序列化后不得泄露身份证/手机号/住址/证件照/出生日期")
    void elderBriefDto_mustNotLeakSensitiveFields() {
        Elder elder = new Elder();
        elder.setId(10L);
        elder.setName("张三");
        elder.setSex(1);                 // 1=男
        elder.setStatus(4);              // 4=入住中
        elder.setBedNumber("104-1");
        // 以下为敏感字段，均不得出现在工具返回里
        elder.setIdCardNo("110101195006150011");
        elder.setPhone("13800001111");
        elder.setAddress("北京市朝阳区某街道某小区1号楼101");
        elder.setIdCardPortraitImg("http://oss/idcard-portrait.jpg");
        elder.setIdCardNationalEmblemImg("http://oss/idcard-emblem.jpg");
        elder.setBirthday("1950-06-15");
        elder.setImage("http://oss/avatar.jpg");

        String json = JSON.toJSONString(ElderBriefDto.from(elder));

        // 不含敏感字段名
        assertThat(json)
                .doesNotContain("idCardNo")
                .doesNotContain("phone")
                .doesNotContain("address")
                .doesNotContain("idCardPortraitImg")
                .doesNotContain("idCardNationalEmblemImg")
                .doesNotContain("birthday")
                .doesNotContain("image");
        // 不含敏感字段值
        assertThat(json)
                .doesNotContain("110101195006150011")
                .doesNotContain("13800001111")
                .doesNotContain("北京市朝阳区")
                .doesNotContain("idcard-portrait.jpg")
                .doesNotContain("idcard-emblem.jpg")
                .doesNotContain("1950-06-15")
                .doesNotContain("avatar.jpg");
        // 允许的字段正常保留，且已转成文案
        assertThat(json).contains("张三").contains("104-1").contains("男").contains("入住中");
    }

    @Test
    @DisplayName("ElderBriefDto 用换算后的年龄替代出生日期")
    void elderBriefDto_exposesAgeNotBirthday() {
        Elder elder = new Elder();
        elder.setId(11L);
        elder.setName("李四");
        elder.setBirthday("1950-06-15");

        ElderBriefDto dto = ElderBriefDto.from(elder);

        assertThat(dto.getAge()).isNotNull().isGreaterThan(60);
        assertThat(JSON.toJSONString(dto)).doesNotContain("1950");
    }

    @Test
    @DisplayName("HealthAssessmentBriefDto 序列化后不得泄露身份证/出生日期/体检报告原件链接")
    void healthAssessmentBriefDto_mustNotLeakSensitiveFields() {
        HealthAssessment ha = new HealthAssessment();
        ha.setElderName("王五");
        ha.setAge(78);
        ha.setGender(0);                 // 0=男
        ha.setHealthScore("82");
        ha.setRiskLevel("风险");
        ha.setNursingLevelName("二级护理");
        ha.setSuggestionForAdmission(0); // 0=建议
        ha.setAdmissionStatus(0);        // 0=已入住
        ha.setTotalCheckDate("2024-04-20");
        ha.setPhysicalExamInstitution("市第一人民医院");
        ha.setAssessmentTime(LocalDateTime.of(2024, 5, 1, 10, 30));
        ha.setReportSummary("血压偏高");
        // 以下为敏感字段
        ha.setIdCard("110101194601010022");
        ha.setBirthDate(LocalDateTime.of(1946, 1, 1, 0, 0));
        ha.setPhysicalReportUrl("http://oss/report-original.pdf");
        ha.setSystemScore("{\"cardio\":70}");

        String json = JSON.toJSONString(HealthAssessmentBriefDto.from(ha));

        assertThat(json)
                .doesNotContain("idCard")
                .doesNotContain("birthDate")
                .doesNotContain("physicalReportUrl")
                .doesNotContain("systemScore");
        assertThat(json)
                .doesNotContain("110101194601010022")
                .doesNotContain("report-original.pdf")
                .doesNotContain("1946-01-01");
        assertThat(json)
                .contains("王五")
                .contains("风险")
                .contains("二级护理")
                .contains("2024-05-01 10:30");
    }
}
