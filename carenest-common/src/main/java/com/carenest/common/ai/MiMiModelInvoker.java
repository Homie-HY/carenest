package com.carenest.common.ai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MiMiModelInvoker {

    @Autowired
    private LLMConfig miMiAIProperties;

    public String miMoInvoker(String prompt) {
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(miMiAIProperties.getApiKey())
                .baseUrl(miMiAIProperties.getBaseUrl())
                .build();

        // 小米 MiMo 不支持 responseFormat，已删除！
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .addUserMessage(prompt)
                .model(miMiAIProperties.getModel())
                .build();

        ChatCompletion chatCompletion = client.chat().completions().create(params);
        return chatCompletion.choices().get(0).message().content().orElse("");
    }
}