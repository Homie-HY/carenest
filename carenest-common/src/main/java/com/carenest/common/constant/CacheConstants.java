package com.carenest.common.constant;

/**
 * 缓存的key 常量
 * 
 * @author ruoyi
 */
public class CacheConstants
{
    /**
     * 登录用户 redis key
     */
    public static final String LOGIN_TOKEN_KEY = "login_tokens:";

    /**
     * 验证码 redis key
     */
    public static final String CAPTCHA_CODE_KEY = "captcha_codes:";

    /**
     * 参数管理 cache key
     */
    public static final String SYS_CONFIG_KEY = "sys_config:";

    /**
     * 字典管理 cache key
     */
    public static final String SYS_DICT_KEY = "sys_dict:";

    /**
     * 防重提交 redis key
     */
    public static final String REPEAT_SUBMIT_KEY = "repeat_submit:";

    /**
     * 限流 redis key
     */
    public static final String RATE_LIMIT_KEY = "rate_limit:";

    /**
     * 登录账户密码错误次数 redis key
     */
    public static final String PWD_ERR_CNT_KEY = "pwd_err_cnt:";
    /**
     * 护理等级缓存key
     */
    public static final String NURSING_LEVEL_ALL_KEY = "nursingLevel:all";
    /**
     * 护理计划缓存key
     */
    public static final Object NURSING_PLAN_ALL_KEY = "nursingPlan:all";
    /**
     * IoT设备最新数据缓存key
     */
    public static final String IOT_DEVICE_LAST_DATA = "iot_device_last_data";
    /**
     * 健康报告缓存key
     */
    public static final String HEALTH_REPORT = "health_report";
    /**
     * IoT所有产品列表缓存key
     */
    public static final String IOT_ALL_PRODUCT_LIST = "iot_all_product_list";
    /**
     * 告警触发次数计数前缀
     */
    public static final String ALERT_TRIGGER_COUNT_PREFIX = "alert_trigger_count:";
    /**
     * 告警静默期前缀
     */
    public static final String ALERT_SILENT_PREFIX = "alert_silent:";
    /**
     * 护理项目缓存key
     */
    public static final String NURSING_PROJECT_ALL_KEY = "nursingProject:all";
}
