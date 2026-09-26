package com.carenest.nursing.ai.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.carenest.nursing.ai.dto.NursingPlanBriefDto;
import com.carenest.nursing.ai.dto.NursingProjectBriefDto;
import com.carenest.nursing.domain.NursingPlan;
import com.carenest.nursing.domain.NursingProject;
import com.carenest.nursing.domain.NursingProjectPlan;
import com.carenest.nursing.mapper.NursingProjectPlanMapper;
import com.carenest.nursing.service.INursingPlanService;
import com.carenest.nursing.service.INursingProjectService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 护理计划与护理项目查询工具（只读）。
 * <p>
 * 说明：本项目中 nursing_plan 是全院通用的计划目录（非按老人维度存储），
 * 因此这里提供“计划清单 / 计划详情（含项目）/ 项目清单”三类目录查询。
 * 目录数据不含老人隐私，所有已登录护理助手用户均可查询。
 *
 * @author Homie
 */
public class NursingPlanTools {

    private final INursingPlanService nursingPlanService;
    private final INursingProjectService nursingProjectService;
    private final NursingProjectPlanMapper nursingProjectPlanMapper;

    public NursingPlanTools(INursingPlanService nursingPlanService, INursingProjectService nursingProjectService,
                            NursingProjectPlanMapper nursingProjectPlanMapper) {
        this.nursingPlanService = nursingPlanService;
        this.nursingProjectService = nursingProjectService;
        this.nursingProjectPlanMapper = nursingProjectPlanMapper;
    }

    @Tool("查询启用中的护理计划清单（护理计划是全院通用目录）。返回计划名称、状态等基本信息。")
    public String listNursingPlans() {
        long total = nursingPlanService.lambdaQuery().eq(NursingPlan::getStatus, 1).count();
        List<NursingPlan> plans = nursingPlanService.lambdaQuery()
                .eq(NursingPlan::getStatus, 1)
                .orderByAsc(NursingPlan::getSortNo)
                .last("limit " + ToolResults.MAX_ITEMS)
                .list();
        if (total == 0) {
            return ToolResults.text("暂无启用中的护理计划");
        }
        List<NursingPlanBriefDto> items = plans.stream()
                .map(NursingPlanBriefDto::from).collect(Collectors.toList());
        return ToolResults.page(total, items);
    }

    @Tool("根据护理计划 id 查询该计划的详情，包含计划基本信息与其包含的护理项目清单。")
    public String getNursingPlanDetail(@P("护理计划 id") Long planId) {
        if (planId == null) {
            return ToolResults.text("请提供护理计划 id");
        }
        NursingPlan plan = nursingPlanService.lambdaQuery().eq(NursingPlan::getId, planId).one();
        if (plan == null) {
            return ToolResults.text("未找到 id 为 " + planId + " 的护理计划");
        }
        List<Long> projectIds = nursingProjectPlanMapper.selectList(
                        new LambdaQueryWrapper<NursingProjectPlan>().eq(NursingProjectPlan::getPlanId, planId))
                .stream().map(NursingProjectPlan::getProjectId).distinct().collect(Collectors.toList());
        List<NursingProjectBriefDto> projects = new ArrayList<>();
        if (!projectIds.isEmpty()) {
            projects = nursingProjectService.lambdaQuery().in(NursingProject::getId, projectIds).list()
                    .stream().map(NursingProjectBriefDto::from).collect(Collectors.toList());
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("plan", NursingPlanBriefDto.from(plan));
        detail.put("projects", projects);
        return ToolResults.single(detail);
    }

    @Tool("查询启用中的护理项目清单，返回项目名称、单位、价格、护理要求等。")
    public String listNursingProjects() {
        long total = nursingProjectService.lambdaQuery().eq(NursingProject::getStatus, 1).count();
        List<NursingProject> projects = nursingProjectService.lambdaQuery()
                .eq(NursingProject::getStatus, 1)
                .orderByAsc(NursingProject::getOrderNo)
                .last("limit " + ToolResults.MAX_ITEMS)
                .list();
        if (total == 0) {
            return ToolResults.text("暂无启用中的护理项目");
        }
        List<NursingProjectBriefDto> items = projects.stream()
                .map(NursingProjectBriefDto::from).collect(Collectors.toList());
        return ToolResults.page(total, items);
    }
}
