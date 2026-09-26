package com.carenest.nursing.ai.tool;

import com.carenest.nursing.ai.context.AccessScope;
import com.carenest.nursing.ai.dto.HealthAssessmentBriefDto;
import com.carenest.nursing.domain.Elder;
import com.carenest.nursing.domain.HealthAssessment;
import com.carenest.nursing.domain.NursingElder;
import com.carenest.nursing.service.IElderService;
import com.carenest.nursing.service.IHealthAssessmentService;
import com.carenest.nursing.service.INursingElderService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 健康评估查询工具（只读）。
 * <p>
 * <b>脱敏</b>：只返回 {@link HealthAssessmentBriefDto}，剔除 {@code idCard} 与 {@code physicalReportUrl}。
 * <b>数据权限</b>：health_assessment 无护理员外键，只能按老人姓名回连 elder 做归属校验；
 * 非管理员查某位老人评估前，先确认该老人在其负责范围内。风险等级分布属于全院聚合视图，仅管理员可用。
 *
 * @author Homie
 */
public class HealthAssessmentTools {

    /** 危险等级枚举，与 HealthAssessment.riskLevel 取值一致 */
    private static final String[] RISK_LEVELS = {"健康", "提示", "风险", "危险", "严重危险"};

    private final IHealthAssessmentService healthAssessmentService;
    private final IElderService elderService;
    private final INursingElderService nursingElderService;
    private final AccessScope scope;

    public HealthAssessmentTools(IHealthAssessmentService healthAssessmentService, IElderService elderService,
                                 INursingElderService nursingElderService, AccessScope scope) {
        this.healthAssessmentService = healthAssessmentService;
        this.elderService = elderService;
        this.nursingElderService = nursingElderService;
        this.scope = scope;
    }

    @Tool("查询某位老人最近一次的健康评估结论，包含健康评分、风险等级、推荐护理等级、报告总结、疾病风险等。不返回身份证号与体检报告原件链接。")
    public String getLatestAssessment(@P("老人姓名，需较完整，例如 李天龙") String elderName) {
        if (isBlank(elderName)) {
            return ToolResults.text("请提供要查询的老人姓名");
        }
        String name = elderName.trim();
        // 归属校验：非管理员必须能在自己负责范围内匹配到该姓名对应的老人
        Elder matched = resolveAccessibleElder(name);
        if (matched == null) {
            return scope.isAdmin()
                    ? ToolResults.text("未找到姓名为 " + name + " 的老人档案")
                    : ToolResults.text("未找到姓名为 " + name + " 的老人，或该老人不在你的负责范围内");
        }
        HealthAssessment latest = healthAssessmentService.lambdaQuery()
                .eq(HealthAssessment::getElderName, matched.getName())
                .orderByDesc(HealthAssessment::getAssessmentTime)
                .last("limit 1")
                .one();
        if (latest == null) {
            return ToolResults.text(matched.getName() + " 暂无健康评估记录");
        }
        return ToolResults.single(HealthAssessmentBriefDto.from(latest));
    }

    @Tool("统计全体老人各健康风险等级（健康/提示/风险/危险/严重危险）的人数分布。属于全院聚合视图，仅管理岗可用。")
    public String getRiskLevelDistribution() {
        if (!scope.isAdmin()) {
            return ToolResults.text("风险等级分布是全院聚合数据，仅管理岗可查询");
        }
        Map<String, Long> distribution = new LinkedHashMap<>();
        long accounted = 0L;
        for (String level : RISK_LEVELS) {
            long count = healthAssessmentService.lambdaQuery()
                    .eq(HealthAssessment::getRiskLevel, level)
                    .count();
            distribution.put(level, count);
            accounted += count;
        }
        long total = healthAssessmentService.lambdaQuery().count();
        long other = total - accounted;
        if (other > 0) {
            distribution.put("其他/未分级", other);
        }
        return ToolResults.single(distribution);
    }

    /**
     * 按姓名在当前用户可见范围内解析老人。管理员可匹配全部，非管理员限定 nursing_elder 绑定范围。
     */
    private Elder resolveAccessibleElder(String name) {
        if (scope.isAdmin()) {
            return elderService.lambdaQuery().like(Elder::getName, name).last("limit 1").one();
        }
        Set<Long> allowedIds = nursingElderService.lambdaQuery()
                .eq(NursingElder::getNursingId, scope.getUserId())
                .list().stream().map(NursingElder::getElderId).collect(Collectors.toSet());
        if (allowedIds.isEmpty()) {
            return null;
        }
        List<Elder> elders = elderService.lambdaQuery()
                .like(Elder::getName, name)
                .in(Elder::getId, allowedIds)
                .last("limit 1")
                .list();
        return elders.isEmpty() ? null : elders.get(0);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
