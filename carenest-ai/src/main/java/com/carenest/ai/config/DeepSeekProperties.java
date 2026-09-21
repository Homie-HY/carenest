package com.carenest.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DeepSeek 大模型配置。
 * <p>
 * 与既有的 {@code com.carenest.common.ai.LLMConfig}（小米 MiMo）平级共存，
 * MiMo 链路在健康评估迁移完成前保持不动。
 * <p>
 * 注意：DeepSeek 走 OpenAI 兼容协议，base-url 只到域名，不带 {@code /v1}，
 * 由 langchain4j-open-ai 自行拼接 {@code /chat/completions}。
 *
 * @author qoder
 */
@Data
@ConfigurationProperties(prefix = "llm.deepseek")
public class DeepSeekProperties {

    /** API 密钥，从环境变量 LLM_DEEPSEEK_API_KEY 注入，禁止写入仓库 */
    private String apiKey;

    /** OpenAI 兼容接口地址，默认 https://api.deepseek.com */
    private String baseUrl = "https://api.deepseek.com";

    /** 模型 id，以 GET {baseUrl}/v1/models 的实际返回为准 */
    private String model;

    /** 单次请求超时秒数 */
    private int timeoutSeconds = 60;

    /** 会话记忆窗口条数 */
    private int memoryWindow = 20;

    /** 记忆在 Redis 中的存活天数 */
    private int memoryTtlDays = 7;

    /** 采样温度；护理问答需要稳定输出，默认偏低 */
    private Double temperature = 0.3D;

    /** 是否打印请求/响应报文，仅排障时开启（会把对话内容写进日志） */
    private boolean logPayload = false;

    /**
     * 是否已具备可用的模型配置。
     * 未配置密钥时不阻断应用启动，仅让 AI 相关接口返回明确提示。
     */
    public boolean isAvailable() {
        return apiKey != null && !apiKey.trim().isEmpty()
                && model != null && !model.trim().isEmpty();
    }
}
