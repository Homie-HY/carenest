package com.carenest.common.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "llm.xiaomi")
public class LLMConfig {
    private String apiKey;
    private String baseUrl;
    private String model;
}