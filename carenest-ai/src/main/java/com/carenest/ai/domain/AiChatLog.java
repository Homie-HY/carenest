package com.carenest.ai.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * AI 对话审计日志 ai_chat_log。
 * <p>
 * 一行 = 一轮对话（用户输入 + 模型输出 + 期间发生的工具调用链）。
 * 养老护理属于医疗相关场景，对话内容涉及老人健康信息，必须可追溯：
 * 谁在什么时候问了什么、助手基于哪些数据回答、消耗了多少 token。
 *
 * @author Homie
 */
@Data
@TableName("ai_chat_log")
@ApiModel(value = "AiChatLog对象", description = "AI对话审计日志")
public class AiChatLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("主键")
    @TableId(type = IdType.AUTO)
    private Long id;

    @ApiModelProperty("会话id")
    private String sessionId;

    @ApiModelProperty("提问用户id")
    private Long userId;

    @ApiModelProperty("提问用户所属部门id")
    private Long deptId;

    @ApiModelProperty("提问用户角色标识，多个用逗号分隔")
    private String userRole;

    @ApiModelProperty("模型名称")
    private String model;

    @ApiModelProperty("用户输入")
    private String userInput;

    @ApiModelProperty("模型输出")
    private String modelOutput;

    @ApiModelProperty("工具调用链，JSON数组")
    private String toolCalls;

    @ApiModelProperty("输入token数")
    private Integer inputTokens;

    @ApiModelProperty("输出token数")
    private Integer outputTokens;

    @ApiModelProperty("总token数")
    private Integer totalTokens;

    @ApiModelProperty("本轮耗时（毫秒）")
    private Long costMs;

    @ApiModelProperty("是否成功（0失败 1成功）")
    private Integer success;

    @ApiModelProperty("失败原因")
    private String errorMsg;

    @ApiModelProperty("创建时间")
    private Date createTime;
}
