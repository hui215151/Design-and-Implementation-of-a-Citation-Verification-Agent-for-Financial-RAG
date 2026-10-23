package com.finguard.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finguard.entity.Chunk;
import com.finguard.mapper.ChunkMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 向量检索服务
 * 优化点：用 Caffeine 缓存 chunk 的 embedding，避免每次 search 都反序列化 JSON
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final ChunkMapper chunkMapper;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * embedding 缓存：chunkId → 预解析好的向量
     * TTL 5 分钟，最大 10 万条
     * - 命中 → 直接用，跳过 JSON parse
     * - 过期 → 重新从 DB 读 + parse
     * - 文档上传后主动 invalidateAll() 保证数据新鲜
     */
    private Cache<Long, List<Double>> embeddingCache;

    @PostConstruct
    void initCache() {
        embeddingCache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(100_000)
                .build();
    }

    /**
     * 对外暴露：文档上传后清缓存，保证新 chunk 能被立即检索到
     */
    public void invalidateCache() {
        embeddingCache.invalidateAll();
        log.debug("embedding cache invalidated");
    }

    /**
     * 向量余弦相似度检索 topN
     */
    @SuppressWarnings("unchecked")
    public List<Chunk> search(String question, int topN) {
        long t0 = System.currentTimeMillis();

        // 1. 把问题也转成向量
        List<Double> questionVec = embeddingService.embed(question);

        // 2. 查出所有 chunk
        List<Chunk> allChunks = chunkMapper.selectList(new QueryWrapper<>());

        // 3. 算每个 chunk 和问题的余弦相似度（embedding 走缓存）
        List<Map<String, Object>> scored = new ArrayList<>();
        for (Chunk chunk : allChunks) {
            if (chunk.getEmbedding() == null || chunk.getEmbedding().isEmpty()) continue;
            try {
                List<Double> chunkVec = getEmbedding(chunk);   // ← 改：走缓存
                if (chunkVec == null) continue;
                double sim = cosineSimilarity(questionVec, chunkVec);
                Map<String, Object> item = new HashMap<>();
                item.put("chunk", chunk);
                item.put("score", sim);
                scored.add(item);
            } catch (Exception e) {
                log.debug("chunk {} 向量解析失败: {}", chunk.getId(), e.getMessage());
            }
        }

        // 4. 按相似度降序，取 topN
        scored.sort((a, b) -> Double.compare((Double) b.get("score"), (Double) a.get("score")));

        List<Chunk> result = new ArrayList<>();
        for (int i = 0; i < Math.min(topN, scored.size()); i++) {
            double score = (Double) scored.get(i).get("score");
            if (score > 0.3) {
                result.add((Chunk) scored.get(i).get("chunk"));
            }
        }

        long t1 = System.currentTimeMillis();
        log.info("search done: {} chunks scanned → {} results in {}ms (cache hit rate: {}%)",
                allChunks.size(), result.size(), (t1 - t0), cacheHitRate());
        return result;
    }

    /**
     * 从缓存拿 embedding，没有就 parse 后写入缓存
     */
    @SuppressWarnings("unchecked")
    private List<Double> getEmbedding(Chunk chunk) {
        return embeddingCache.get(chunk.getId(), id -> {
            try {
                List<Double> vec = objectMapper.readValue(chunk.getEmbedding(), List.class);
                log.trace("cache miss: chunk {} parsed", id);
                return vec;
            } catch (Exception e) {
                log.warn("chunk {} embedding parse failed: {}", id, e.getMessage());
                return null;   // 解析失败的返回 null，search 里会跳过
            }
        });
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
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        if (denom == 0) return 0.0;   // 零向量防御
        return dot / denom;
    }

    /**
     * 当前缓存命中率（debug 用）
     */
    private double cacheHitRate() {
        var stats = embeddingCache.stats();
        long hit = stats.hitRate() > 0 ? Math.round(stats.hitRate() * 100) : 0;
        return hit;
    }
}
