package com.carenest.nursing.ai.context;

import lombok.Data;

import java.util.Collections;
import java.util.Set;

/**
 * 工具调用的数据权限上下文。
 * <p>
 * 由 {@code AssistantService} 在<b>请求线程</b>上通过 {@code SecurityUtils} 解析好后构造，
 * 再注入到各 Tool 对象里。<b>Tool 内部绝不能再调用 {@code SecurityUtils}</b>——
 * 流式场景下工具执行发生在 okhttp 回调线程，{@code SecurityContextHolder}（ThreadLocal）为空。
 * <p>
 * 关于 elder 数据权限：{@code elder} 表没有 {@code dept_id} 列，护理员与老人的真实绑定在
 * {@code nursing_elder(nursing_id, elder_id)}。因此非管理员的可见范围按
 * “{@code nursing_elder.nursing_id = userId}” 收敛；{@code deptId} 仅用于审计留痕。
 *
 * @author qoder
 */
@Data
public class AccessScope {

    /** 登录用户 id */
    private final Long userId;

    /** 登录用户部门 id，仅用于审计，不参与 elder 过滤（elder 表无该列） */
    private final Long deptId;

    /** 是否超级管理员：true 时不做 elder 归属过滤，可见全部 */
    private final boolean admin;

    /** 角色标识集合，供审计与后续更细粒度策略使用 */
    private final Set<String> roleKeys;

    public AccessScope(Long userId, Long deptId, boolean admin, Set<String> roleKeys) {
        this.userId = userId;
        this.deptId = deptId;
        this.admin = admin;
        this.roleKeys = roleKeys == null ? Collections.emptySet() : roleKeys;
    }

    /**
     * 角色标识拼成逗号分隔串，写入审计日志的 user_role 字段。
     */
    public String roleKeysText() {
        return String.join(",", roleKeys);
    }
}
