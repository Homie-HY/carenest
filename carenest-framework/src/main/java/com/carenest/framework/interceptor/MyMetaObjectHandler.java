package com.carenest.framework.interceptor;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.carenest.common.core.domain.model.LoginUser;
import com.carenest.common.utils.SecurityUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Date;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    /**
     * 是否跳过 createBy / updateBy 填充。
     * <p>
     * 原先直接注入 HttpServletRequest 并调用 getRequestURI()，在非请求线程上会抛
     * IllegalStateException（Spring 注入的是 scoped proxy，没有线程绑定的 request 就不能用），
     * 导致定时任务、AI 流式回调等场景下的 insert 整体失败。
     * 改为从 RequestContextHolder 取，取不到则视为非 Web 请求场景。
     */
    public boolean isExclude() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes)) {
            // 定时任务线程、SSE / 流式回调线程：没有请求上下文，不填 createBy
            return true;
        }
        String requestURI = ((ServletRequestAttributes) attributes).getRequest().getRequestURI();
        return requestURI.startsWith("/member");
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", Date.class, new Date());
        if(!isExclude()) {
            this.strictInsertFill(metaObject, "createBy", String.class, loadUserId() + "");
        }

    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.setFieldValByName("updateTime", new Date(), metaObject);
        if(!isExclude()) {
            this.setFieldValByName("updateBy", loadUserId() + "", metaObject);
        }

    }

    /**
     * 获取当前登录人的ID
     *
     * @return
     */
    private static Long loadUserId() {

        // 获取当前登录人的id
        try {
            LoginUser loginUser = SecurityUtils.getLoginUser();
            if (ObjectUtils.isNotEmpty(loginUser)) {
                return loginUser.getUserId();
            }
            return 1L;
        } catch (Exception e) {
            return 1L;
        }
    }
}