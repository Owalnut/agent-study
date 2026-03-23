package com.walnut.agent.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ChatClientFactory {
    private static final Logger log = LoggerFactory.getLogger(ChatClientFactory.class);

    public ChatClient createDeepSeekClient(String baseUrl, String apiKey, String model, Double temperature) {
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        log.info("DeepSeek client init: baseUrl(normalized)={}, model={}, temperature={}", normalizedBaseUrl, model, temperature);
        OpenAiApi openAiApi = new OpenAiApi(
                normalizedBaseUrl,
                apiKey == null ? "" : apiKey.trim()
        );
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model == null || model.isBlank() ? "deepseek-chat" : model)
                .temperature(temperature == null ? 0.7 : temperature)
                .build();
        OpenAiChatModel chatModel = new OpenAiChatModel(openAiApi, options);
        return ChatClient.builder(chatModel).build();
    }

    private String normalizeBaseUrl(String baseUrl) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        // Spring AI (OpenAiApi) 会默认把请求拼成：{baseUrl}/v1/chat/completions
        // 为了兼容 UI 输入带有 /v1 或 /chat/completions 的情况，
        // 这里统一剔除潜在导致重复的路径片段。
        while (trimmed.endsWith("/chat/completions")) {
            trimmed = trimmed.substring(0, trimmed.length() - "/chat/completions".length());
        }
        while (trimmed.endsWith("/v1")) {
            trimmed = trimmed.substring(0, trimmed.length() - "/v1".length());
        }
        return trimmed;
    }
}
