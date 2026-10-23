package com.finguard.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finguard.entity.Chunk;
import com.finguard.mapper.ChunkMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final ChunkMapper chunkMapper;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 向量余弦相似度检索 topN
     */
    @SuppressWarnings("unchecked")
    public List<Chunk> search(String question, int topN) {
        // 1. 把问题也转成向量
        List<Double> questionVec = embeddingService.embed(question);

        // 2. 查出所有 chunk
        List<Chunk> allChunks = chunkMapper.selectList(new QueryWrapper<>());

        // 3. 算每个 chunk 和问题的余弦相似度
        List<Map<String, Object>> scored = new ArrayList<>();
        for (Chunk chunk : allChunks) {
            if (chunk.getEmbedding() == null || chunk.getEmbedding().isEmpty()) continue;
            try {
                List<Double> chunkVec = objectMapper.readValue(chunk.getEmbedding(), List.class);
                double sim = cosineSimilarity(questionVec, chunkVec);
                Map<String, Object> item = new HashMap<>();
                item.put("chunk", chunk);
                item.put("score", sim);
                scored.add(item);
            } catch (Exception e) {
                // 解析失败的 chunk 跳过
            }
        }

        // 4. 按相似度降序，取 topN
        scored.sort((a, b) -> Double.compare((Double) b.get("score"), (Double) a.get("score")));

        List<Chunk> result = new ArrayList<>();
        for (int i = 0; i < Math.min(topN, scored.size()); i++) {
            double score = (Double) scored.get(i).get("score");
            // 相似度低于 0.3 的不要（完全不相关）
            if (score > 0.3) {
                result.add((Chunk) scored.get(i).get("chunk"));
            }
        }
        return result;
    }

    /**
     * 余弦相似度计算
     */
    double cosineSimilarity(List<Double> a, List<Double> b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
