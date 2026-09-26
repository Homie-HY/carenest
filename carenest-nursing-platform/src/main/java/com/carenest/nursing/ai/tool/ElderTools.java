package com.carenest.nursing.ai.tool;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.carenest.nursing.ai.context.AccessScope;
import com.carenest.nursing.ai.dto.ElderBriefDto;
import com.carenest.nursing.domain.Bed;
import com.carenest.nursing.domain.Elder;
import com.carenest.nursing.domain.Floor;
import com.carenest.nursing.domain.NursingElder;
import com.carenest.nursing.domain.Room;
import com.carenest.nursing.service.IBedService;
import com.carenest.nursing.service.IElderService;
import com.carenest.nursing.service.IFloorService;
import com.carenest.nursing.service.INursingElderService;
import com.carenest.nursing.service.IRoomService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 老人档案查询工具（只读）。
 * <p>
 * <b>数据权限</b>：elder 表无 dept_id，护理员与老人的绑定在 nursing_elder(nursing_id, elder_id)。
 * 非管理员只能查到自己负责的老人；管理员（userId=1）可见全部。范围由构造时注入的 {@link AccessScope}
 * 决定，<b>绝不在此调用 SecurityUtils</b>（工具可能运行在流式回调线程，上下文为空）。
 * <p>
 * <b>脱敏</b>：只返回 {@link ElderBriefDto}，绝不返回 {@link Elder} 实体（含身份证、手机号、住址、证件照）。
 *
 * @author Homie
 */
public class ElderTools {

    private final IElderService elderService;
    private final INursingElderService nursingElderService;
    private final IFloorService floorService;
    private final IRoomService roomService;
    private final IBedService bedService;
    private final AccessScope scope;

    public ElderTools(IElderService elderService, INursingElderService nursingElderService,
                      IFloorService floorService, IRoomService roomService, IBedService bedService,
                      AccessScope scope) {
        this.elderService = elderService;
        this.nursingElderService = nursingElderService;
        this.floorService = floorService;
        this.roomService = roomService;
        this.bedService = bedService;
        this.scope = scope;
    }

    @Tool("根据老人姓名查询老人档案，支持姓名模糊匹配。返回脱敏后的基本信息（姓名、性别、年龄、床位号、状态），不含身份证、手机号、住址等隐私。")
    public String findElderByName(@P("老人姓名，支持模糊匹配，例如 李天龙") String name) {
        if (isBlank(name)) {
            return ToolResults.text("请提供要查询的老人姓名");
        }
        Set<Long> allowedIds = accessibleElderIdsOrNull();
        if (isEmptyScope(allowedIds)) {
            return ToolResults.text("你没有可查看的老人档案");
        }
        String keyword = name.trim();
        long total = baseElderQuery(allowedIds).like(Elder::getName, keyword).count();
        List<Elder> elders = baseElderQuery(allowedIds)
                .like(Elder::getName, keyword)
                .orderByDesc(Elder::getId)
                .last("limit " + ToolResults.MAX_ITEMS)
                .list();
        return ToolResults.page(total, toBrief(elders));
    }

    @Tool("根据床位编号查询入住该床位的老人档案。返回脱敏后的基本信息，不含隐私字段。")
    public String findElderByBedNumber(@P("床位编号，例如 104-1") String bedNumber) {
        if (isBlank(bedNumber)) {
            return ToolResults.text("请提供要查询的床位编号");
        }
        Set<Long> allowedIds = accessibleElderIdsOrNull();
        if (isEmptyScope(allowedIds)) {
            return ToolResults.text("你没有可查看的老人档案");
        }
        String keyword = bedNumber.trim();
        long total = baseElderQuery(allowedIds).eq(Elder::getBedNumber, keyword).count();
        List<Elder> elders = baseElderQuery(allowedIds)
                .eq(Elder::getBedNumber, keyword)
                .last("limit " + ToolResults.MAX_ITEMS)
                .list();
        if (total == 0) {
            return ToolResults.text("床位 " + keyword + " 当前没有登记老人，或该床位不在你的负责范围内");
        }
        return ToolResults.page(total, toBrief(elders));
    }

    @Tool("查询某楼层的老人清单。需要提供楼层名称（例如 3楼）。返回脱敏后的老人基本信息列表，不含隐私字段。")
    public String listEldersByFloor(@P("楼层名称，例如 3楼") String floorName) {
        if (isBlank(floorName)) {
            return ToolResults.text("请提供楼层名称，例如 3楼");
        }
        Set<Long> allowedIds = accessibleElderIdsOrNull();
        if (isEmptyScope(allowedIds)) {
            return ToolResults.text("你没有可查看的老人档案");
        }
        Floor floor = floorService.lambdaQuery().eq(Floor::getName, floorName.trim()).one();
        if (floor == null) {
            return ToolResults.text("未找到名为 " + floorName + " 的楼层");
        }
        List<Long> bedIds = bedIdsOfFloor(floor.getId());
        if (bedIds.isEmpty()) {
            return ToolResults.text(floorName + " 暂无床位登记");
        }
        long total = baseElderQuery(allowedIds).in(Elder::getBedId, bedIds).count();
        List<Elder> elders = baseElderQuery(allowedIds)
                .in(Elder::getBedId, bedIds)
                .orderByAsc(Elder::getBedNumber)
                .last("limit " + ToolResults.MAX_ITEMS)
                .list();
        if (total == 0) {
            return ToolResults.text(floorName + " 没有你可查看的在住老人");
        }
        return ToolResults.page(total, toBrief(elders));
    }

    /**
     * 构造带数据权限过滤的老人查询链。allowedIds 为 null 表示管理员，不加过滤。
     */
    private LambdaQueryChainWrapper<Elder> baseElderQuery(Set<Long> allowedIds) {
        LambdaQueryChainWrapper<Elder> query = elderService.lambdaQuery();
        if (allowedIds != null) {
            query.in(Elder::getId, allowedIds);
        }
        return query;
    }

    /**
     * 当前用户可见的老人 id 集合。
     *
     * @return null 表示管理员、无需过滤；空集合表示无任何可见老人
     */
    private Set<Long> accessibleElderIdsOrNull() {
        if (scope.isAdmin()) {
            return null;
        }
        List<NursingElder> bindings = nursingElderService.lambdaQuery()
                .eq(NursingElder::getNursingId, scope.getUserId())
                .list();
        return bindings.stream().map(NursingElder::getElderId).collect(Collectors.toSet());
    }

    private boolean isEmptyScope(Set<Long> allowedIds) {
        return allowedIds != null && allowedIds.isEmpty();
    }

    /**
     * 某楼层下所有床位 id：楼层 -> 房间 -> 床位。
     */
    private List<Long> bedIdsOfFloor(Long floorId) {
        List<Room> rooms = roomService.lambdaQuery().eq(Room::getFloorId, floorId).list();
        if (rooms.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> roomIds = rooms.stream().map(Room::getId).collect(Collectors.toList());
        List<Bed> beds = bedService.lambdaQuery().in(Bed::getRoomId, roomIds).list();
        return beds.stream().map(Bed::getId).collect(Collectors.toList());
    }

    private List<ElderBriefDto> toBrief(List<Elder> elders) {
        List<ElderBriefDto> list = new ArrayList<>(elders.size());
        for (Elder elder : elders) {
            list.add(ElderBriefDto.from(elder));
        }
        return list;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
