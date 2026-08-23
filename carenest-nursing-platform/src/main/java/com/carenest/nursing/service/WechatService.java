package com.carenest.nursing.service;

public interface WechatService {

    /**
     * 获取openid
     * @param code
     * @return
     */
    public String getOpenid(String code);

    /**
     * 获取手机号
     * @param detailCode
     * @return
     */
    public String getPhone(String detailCode);
}