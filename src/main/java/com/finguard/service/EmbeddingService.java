package com.finguard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Embedding 服务：调硅基流动的 text-embedding 接口
 */
@Service
public class EmbeddingService {

    @Value("${embedding.base-url}")
    private String baseUrl;

    @Value("${embedding.api-key}")
    private String apiKey;

    @Value("${embedding.model}")
    private String model;

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 把一段文本转成 1024 维向量
     */
    @SuppressWarnings("unchecked")
    public List<Double> embed(String text) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("input", text);

            RequestBody requestBody = RequestBody.create(
                    objectMapper.writeValueAsString(body),
                    MediaType.parse("application/json")
            );

            Request request = new Request.Builder()
                    .url(baseUrl + "/embeddings")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String respBody = response.body().string();
                System.out.println("=== Embedding 响应 ===");
                System.out.println("code: " + response.code());
                System.out.println("body: " + respBody.substring(0, Math.min(500, respBody.length())));
                System.out.println("======================");

                if (!response.isSuccessful()) {
                    throw new RuntimeException("Embedding 接口错误: " + response.code() + " " + respBody);
                }

                JsonNode json = objectMapper.readTree(respBody);
                JsonNode embeddingNode = json.path("data").path(0).path("embedding");
                return objectMapper.convertValue(embeddingNode, List.class);
            }
        } catch (Exception e) {
            throw new RuntimeException("Embedding 调用失败: " + e.getMessage(), e);
        }
    }
}
