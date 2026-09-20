package com.carenest;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

public class XiaomiMiMoTest {
    public static void main(String[] args) {
        // 1. MiMo 开放平台申请的 sk- 开头 key，从环境变量读取，禁止硬编码到仓库
        String apiKey = System.getenv("LLM_XIAOMI_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            System.err.println("请先设置环境变量 LLM_XIAOMI_API_KEY");
            return;
        }

        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl("https://api.xiaomimimo.com/v1") // 2. MiMo 地址
                .build();

        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .addUserMessage("你好，我在用小米 MiMo 大模型")
                .model("mimo-v2.5-pro") // 3. MiMo 模型名
                // MiMo 同样不支持 responseFormat，不要加
                .build();

        ChatCompletion chatCompletion = client.chat().completions().create(params);
        System.out.println(chatCompletion.choices().get(0).message().content().orElse(""));
    }
}