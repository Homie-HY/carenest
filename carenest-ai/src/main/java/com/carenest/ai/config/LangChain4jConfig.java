package com.carenest.ai.config;

import com.carenest.ai.memory.ChatMemoryFactory;
import com.carenest.ai.memory.ChatSessionRegistry;
import com.carenest.ai.memory.RedisChatMemoryStore;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * LangChain4j 装配。
 * <p>
 * 有意不使用 {@code langchain4j-spring-boot-starter}：0.35.0 的 starter 面向 Spring Boot 3，
 * 本项目是 Boot 2.5.15，手动声明 Bean 可以把兼容性完全掌握在自己手里。
 * <p>
 * <b>关于密钥缺失的兜底</b>：openai4j 的 {@code openAiApiKey()} / {@code baseUrl()} 在收到
 * null 或空串时会直接抛 {@code IllegalArgumentException}。若照原样建 Bean，
 * 未配置 {@code LLM_DEEPSEEK_API_KEY} 的环境（包括本地开发机）会连应用都启动不了，
 * 连带把现有的小米 MiMo 健康评估链路一起拖垮。因此这里用占位值保证 Bean 能建出来，
 * 由 {@code AssistantService} 在真正调用前用 {@link DeepSeekProperties#isAvailable()} 拦截。
 *
 * @author Homie
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(DeepSeekProperties.class)
public class LangChain4jConfig {

    /** 密钥未配置时的占位值，仅用于让 Bean 构建通过，永远不会真正发出去 */
    static final String PLACEHOLDER_API_KEY = "not-configured";

    /** base-url 未配置时的兜底值 */
    static final String DEFAULT_BASE_URL = "https://api.deepseek.com";

    @Bean
    public RedisChatMemoryStore redisChatMemoryStore(StringRedisTemplate stringRedisTemplate,
                                                     DeepSeekProperties properties) {
        return new RedisChatMemoryStore(stringRedisTemplate, memoryTtl(properties));
    }

    @Bean
    public ChatSessionRegistry chatSessionRegistry(StringRedisTemplate stringRedisTemplate,
                                                   DeepSeekProperties properties) {
        return new ChatSessionRegistry(stringRedisTemplate, memoryTtl(properties));
    }

    @Bean
    public ChatMemoryFactory chatMemoryFactory(RedisChatMemoryStore redisChatMemoryStore,
                                               DeepSeekProperties properties) {
        return new ChatMemoryFactory(redisChatMemoryStore, properties.getMemoryWindow());
    }

    /**
     * 同步模型。工具调用回环、健康评估这类"要完整结果"的场景用它。
     */
    @Bean
    public ChatLanguageModel chatLanguageModel(DeepSeekProperties properties) {
        String apiKey = resolveApiKey(properties);
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(resolveBaseUrl(properties))
                .modelName(resolveModel(properties))
                .temperature(properties.getTemperature())
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .maxRetries(2)
                .logRequests(properties.isLogPayload())
                .logResponses(properties.isLogPayload())
                .build();
    }

    /**
     * 流式模型。注意它没有 {@code maxRetries}：流式请求重试会产生重复输出，openai4j 也没开放该开关。
     */
    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel(DeepSeekProperties properties) {
        String apiKey = resolveApiKey(properties);
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(resolveBaseUrl(properties))
                .modelName(resolveModel(properties))
                .temperature(properties.getTemperature())
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .logRequests(properties.isLogPayload())
                .logResponses(properties.isLogPayload())
                .build();
    }

    private static Duration memoryTtl(DeepSeekProperties properties) {
        int days = properties.getMemoryTtlDays() <= 0 ? 7 : properties.getMemoryTtlDays();
        return Duration.ofDays(days);
    }

    private static String resolveApiKey(DeepSeekProperties properties) {
        if (properties.getApiKey() == null || properties.getApiKey().trim().isEmpty()) {
            log.warn("未配置 LLM_DEEPSEEK_API_KEY，AI 助手接口将返回未配置提示；"
                    + "现有小米 MiMo 健康评估链路不受影响");
            return PLACEHOLDER_API_KEY;
        }
        return properties.getApiKey().trim();
    }

    private static String resolveBaseUrl(DeepSeekProperties properties) {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return DEFAULT_BASE_URL;
        }
        // openai4j 自己会补结尾斜杠，这里不重复处理，避免出现双斜杠
        return baseUrl.trim();
    }

    private static String resolveModel(DeepSeekProperties properties) {
        String model = properties.getModel();
        if (model == null || model.trim().isEmpty()) {
            log.warn("未配置 llm.deepseek.model，请先执行 GET {baseUrl}/v1/models 确认可用模型 id 后填入");
            return "deepseek-chat";
        }
        return model.trim();
    }
}
