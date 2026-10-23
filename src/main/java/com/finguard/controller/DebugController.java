package com.finguard.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finguard.entity.Chunk;
import com.finguard.mapper.ChunkMapper;
import com.finguard.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
public class DebugController {

    private final ChunkMapper chunkMapper;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/chunks")
    public Map<String, Object> chunks() {
        List<Chunk> list = chunkMapper.selectList(new QueryWrapper<>());
        Map<String, Object> result = new HashMap<>();
        result.put("total", list.size());
        for (int i = 0; i < list.size(); i++) {
            Chunk c = list.get(i);
            result.put("chunk_" + i + "_id", c.getId());
            result.put("chunk_" + i + "_length", c.getContent() == null ? 0 : c.getContent().length());
            // 看 embedding 长度
            try {
                List<Double> vec = objectMapper.readValue(c.getEmbedding(), List.class);
                result.put("chunk_" + i + "_embeddingDim", vec.size());
            } catch (Exception e) {
                result.put("chunk_" + i + "_embeddingError", e.getMessage());
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    @GetMapping("/similarity")
    public Map<String, Object> similarity(@RequestParam String question) {
        // 问题向量
        List<Double> questionVec = embeddingService.embed(question);

        // chunk 向量
        List<Chunk> chunks = chunkMapper.selectList(new QueryWrapper<>());
        Map<String, Object> result = new HashMap<>();
        result.put("questionDim", questionVec.size());

        for (int i = 0; i < chunks.size(); i++) {
            try {
                List<Double> chunkVec = objectMapper.readValue(chunks.get(i).getEmbedding(), List.class);
                double sim = cosineSimilarity(questionVec, chunkVec);
                result.put("chunk_" + i + "_sim", sim);
            } catch (Exception e) {
                result.put("chunk_" + i + "_error", e.getMessage());
            }
        }
        return result;
    }

    private double cosineSimilarity(List<Double> a, List<Double> b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
