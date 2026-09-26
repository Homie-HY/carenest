package com.carenest.nursing.ai.tool;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.carenest.nursing.ai.context.AccessScope;
import com.carenest.nursing.domain.Elder;
import com.carenest.nursing.domain.NursingElder;
import com.carenest.nursing.service.IBedService;
import com.carenest.nursing.service.IElderService;
import com.carenest.nursing.service.IFloorService;
import com.carenest.nursing.service.INursingElderService;
import com.carenest.nursing.service.IRoomService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ElderTools} 数据权限单测。
 * <p>
 * 覆盖计划要求的“越权查不到”：非管理员护理员只能查到自己名下（nursing_elder 绑定）的老人。
 * elder 表无 dept_id，权限按 nursing_id=userId 收敛，因此这里验证两件事：
 * <ol>
 *   <li>没有任何绑定的护理员发起查询时被直接拒绝，且<b>根本不会去查 elder 表</b>；</li>
 *   <li>只绑定了部分老人的护理员，其 elder 查询被强制加上 {@code id in (允许集合)} 过滤，
 *       未绑定的老人不可能进入结果。</li>
 * </ol>
 * MyBatis-Plus 的链式 wrapper 用 {@code RETURNS_SELF} 打桩：所有流式方法返回自身，只对终止方法
 * {@code count() / list()} 精确桩定，避免逐个 overload 匹配。
 *
 * @author Homie
 */
class ElderToolsPermissionTest {

    private final IElderService elderService = mock(IElderService.class);
    private final INursingElderService nursingElderService = mock(INursingElderService.class);
    private final IFloorService floorService = mock(IFloorService.class);
    private final IRoomService roomService = mock(IRoomService.class);
    private final IBedService bedService = mock(IBedService.class);

    private static AccessScope nurseScope(long userId) {
        return new AccessScope(userId, 5L, false, Collections.singleton("nurse"));
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryChainWrapper<NursingElder> mockNursingChain(List<NursingElder> bindings) {
        LambdaQueryChainWrapper<NursingElder> chain = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        when(nursingElderService.lambdaQuery()).thenReturn(chain);
        when(chain.list()).thenReturn(bindings);
        return chain;
    }

    private static NursingElder binding(long elderId) {
        NursingElder ne = new NursingElder();
        ne.setElderId(elderId);
        return ne;
    }

    @Test
    @DisplayName("无绑定的护理员：查询被拒绝，且完全不触碰 elder 表")
    void nurseWithNoBindings_isRefused_andNeverQueriesElderTable() {
        mockNursingChain(Collections.emptyList());
        ElderTools tools = new ElderTools(elderService, nursingElderService,
                floorService, roomService, bedService, nurseScope(100L));

        String result = tools.findElderByName("张三");

        assertThat(result).isEqualTo("你没有可查看的老人档案");
        verify(elderService, never()).lambdaQuery();
    }

    @Test
    @DisplayName("只绑定老人10的护理员：elder 查询被强制加上 id in (10) 过滤")
    @SuppressWarnings("unchecked")
    void nurseQuery_isConstrainedToOwnedElderIds() {
        mockNursingChain(Collections.singletonList(binding(10L)));

        LambdaQueryChainWrapper<Elder> elderChain = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        when(elderService.lambdaQuery()).thenReturn(elderChain);
        when(elderChain.count()).thenReturn(1L);
        Elder owned = new Elder();
        owned.setId(10L);
        owned.setName("张三");
        owned.setBedNumber("104-1");
        when(elderChain.list()).thenReturn(Collections.singletonList(owned));

        ElderTools tools = new ElderTools(elderService, nursingElderService,
                floorService, roomService, bedService, nurseScope(100L));
        String result = tools.findElderByName("张");

        // 查询链上必须出现 id in (允许集合) 的收敛条件，且允许集合恰为 {10}
        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(elderChain, org.mockito.Mockito.atLeastOnce()).in(any(), captor.capture());
        assertThat(captor.getAllValues()).isNotEmpty();
        for (Collection<Long> allowed : captor.getAllValues()) {
            assertThat(allowed).containsExactly(10L);
        }
        // 结果只含被绑定的老人，且已脱敏（不含身份证等字段）
        assertThat(result).contains("张三").doesNotContain("idCardNo");
    }

    @Test
    @DisplayName("管理员：不加归属过滤（in 不被调用），可见全部")
    void adminQuery_isNotConstrained() {
        LambdaQueryChainWrapper<Elder> elderChain = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        when(elderService.lambdaQuery()).thenReturn(elderChain);
        when(elderChain.count()).thenReturn(0L);
        when(elderChain.list()).thenReturn(Collections.emptyList());

        AccessScope admin = new AccessScope(1L, null, true, Collections.singleton("admin"));
        ElderTools tools = new ElderTools(elderService, nursingElderService,
                floorService, roomService, bedService, admin);
        tools.findElderByName("张");

        // 管理员无需归属收敛：不查 nursing_elder，也不对 elder 加 in 过滤
        verify(nursingElderService, never()).lambdaQuery();
        verify(elderChain, never()).in(any(), any(Collection.class));
    }

    @Test
    @DisplayName("按床位号查询同样受权限约束：无绑定直接拒绝")
    void findByBedNumber_respectsScope() {
        mockNursingChain(Collections.emptyList());
        ElderTools tools = new ElderTools(elderService, nursingElderService,
                floorService, roomService, bedService, nurseScope(100L));

        String result = tools.findElderByBedNumber("104-1");

        assertThat(result).isEqualTo("你没有可查看的老人档案");
        verify(elderService, never()).lambdaQuery();
    }

    @Test
    @DisplayName("按楼层查询同样受权限约束：无绑定直接拒绝")
    void listByFloor_respectsScope() {
        mockNursingChain(Collections.emptyList());
        ElderTools tools = new ElderTools(elderService, nursingElderService,
                floorService, roomService, bedService, nurseScope(100L));

        String result = tools.listEldersByFloor("3楼");

        assertThat(result).isEqualTo("你没有可查看的老人档案");
        verify(floorService, never()).lambdaQuery();
    }

    @Test
    @DisplayName("空姓名：直接返回提示，不触发任何查询")
    void blankName_shortCircuits() {
        ElderTools tools = new ElderTools(elderService, nursingElderService,
                floorService, roomService, bedService, nurseScope(100L));

        assertThat(tools.findElderByName("  ")).isEqualTo("请提供要查询的老人姓名");
        verify(nursingElderService, never()).lambdaQuery();
        verify(elderService, never()).lambdaQuery();
    }

    @Test
    @DisplayName("Set 收敛：绑定多条时允许集合去重且完整")
    @SuppressWarnings("unchecked")
    void multipleBindings_collectAllOwnedIds() {
        mockNursingChain(java.util.Arrays.asList(binding(10L), binding(20L), binding(20L)));

        LambdaQueryChainWrapper<Elder> elderChain = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        when(elderService.lambdaQuery()).thenReturn(elderChain);
        when(elderChain.count()).thenReturn(0L);
        when(elderChain.list()).thenReturn(Collections.emptyList());

        ElderTools tools = new ElderTools(elderService, nursingElderService,
                floorService, roomService, bedService, nurseScope(100L));
        tools.findElderByName("张");

        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(elderChain, org.mockito.Mockito.atLeastOnce()).in(any(), captor.capture());
        assertThat(captor.getAllValues().get(0)).containsExactlyInAnyOrder(10L, 20L);
    }
}
