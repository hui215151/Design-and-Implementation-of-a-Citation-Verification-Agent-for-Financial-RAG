package com.finguard.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import okio.BufferedSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * DeepSeek LLM 客户端（自己封装，不依赖 Spring AI）
 * 面试亮点：没有用重型框架，直接基于 OkHttp 封装了 OpenAI 兼容协议
 */
@Component
public class LlmClient {

    @Value("${llm.base-url}")
    private String baseUrl;

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.main-model}")
    private String model;

    private final OkHttpClient http = new OkHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    /**
     * 简单 chat 调用（非流式）
     */
    public String chat(String systemPrompt, String userMessage) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userMessage)
            ));
            body.put("temperature", 0.2);

            RequestBody requestBody = RequestBody.create(
                    json.writeValueAsString(body),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build();

            try (Response response = http.newCall(request).execute()) {
                String respStr = response.body().string();
                JsonNode root = json.readTree(respStr);
                return root.path("choices").get(0).path("message").path("content").asText();
            }
        } catch (IOException e) {
            throw new RuntimeException("LLM 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 流式 chat 调用：开启 stream=true，逐 token 回调 onToken
     * 用于 SSE 打字机效果的前端体验
     */
    public void chatStream(String systemPrompt, String userMessage, Consumer<String> onToken) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("stream", true);   // ← 关键：开启流式
            body.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userMessage)
            ));
            body.put("temperature", 0.2);

            RequestBody requestBody = RequestBody.create(
                    json.writeValueAsString(body),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build();

            try (Response response = http.newCall(request).execute()) {
                BufferedSource source = response.body().source();
                while (!source.exhausted()) {
                    String line = source.readUtf8Line();
                    if (line == null || line.isEmpty()) continue;
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) break;

                    // 解析 SSE 格式 {"choices":[{"delta":{"content":"你"}}]}
                    JsonNode root = json.readTree(data);
                    JsonNode content = root.path("choices").get(0).path("delta").path("content");
                    if (!content.isMissingNode() && !content.isNull()) {
                        onToken.accept(content.asText());
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("LLM 流式调用失败: " + e.getMessage(), e);
        }
    }
}
