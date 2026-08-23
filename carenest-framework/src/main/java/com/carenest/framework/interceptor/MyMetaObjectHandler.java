package com.carenest.framework.interceptor;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.carenest.common.core.domain.model.LoginUser;
import com.carenest.common.utils.SecurityUtils;
import lombok.SneakyThrows;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Autowired
    private HttpServletRequest request;



    @SneakyThrows
    public boolean isExclude() {
        String requestURI = request.getRequestURI();
        if(requestURI.startsWith("/member")) {
            return true;
        }
        return false;
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