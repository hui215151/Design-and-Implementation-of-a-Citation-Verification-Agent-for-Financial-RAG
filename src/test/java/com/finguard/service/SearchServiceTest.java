package com.finguard.service;

import com.finguard.mapper.ChunkMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SearchService 单元测试
 * 只测 cosineSimilarity（纯数学，不需要 mock 外部依赖）
 */
@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private ChunkMapper chunkMapper;

    @Test
    void cosineSimilarity_identicalVectors_returnsOne() {
        SearchService search = new SearchService(chunkMapper, null);
        List<Double> a = List.of(1.0, 2.0, 3.0);
        double sim = search.cosineSimilarity(a, a);
        assertEquals(1.0, sim, 0.0001);
    }

    @Test
    void cosineSimilarity_orthogonalVectors_returnsZero() {
        SearchService search = new SearchService(chunkMapper, null);
        List<Double> a = List.of(1.0, 0.0);
        List<Double> b = List.of(0.0, 1.0);
        double sim = search.cosineSimilarity(a, b);
        assertEquals(0.0, sim, 0.0001);
    }

    @Test
    void cosineSimilarity_oppositeVectors_returnsMinusOne() {
        SearchService search = new SearchService(chunkMapper, null);
        List<Double> a = List.of(1.0, 2.0);
        List<Double> b = List.of(-1.0, -2.0);
        double sim = search.cosineSimilarity(a, b);
        assertEquals(-1.0, sim, 0.0001);
    }

    @Test
    void cosineSimilarity_sameDirection_differentScale() {
        SearchService search = new SearchService(chunkMapper, null);
        List<Double> a = List.of(1.0, 1.0, 1.0);
        List<Double> b = List.of(2.0, 2.0, 2.0);
        double sim = search.cosineSimilarity(a, b);
        assertEquals(1.0, sim, 0.0001);   // 方向相同，不管长度
    }

    @Test
    void cosineSimilarity_zeroVector_returnsZero() {
        SearchService search = new SearchService(chunkMapper, null);
        List<Double> a = List.of(0.0, 0.0, 0.0);
        List<Double> b = List.of(1.0, 2.0, 3.0);
        // 零向量 norm 为 0 → 分母为 0 → 返回 0.0（防御性处理，避免除零错误）
        assertEquals(0.0, search.cosineSimilarity(a, b), 0.0001);
    }

    @Test
    void cosineSimilarity_typicalCase() {
        SearchService search = new SearchService(chunkMapper, null);
        List<Double> a = List.of(1.0, 0.0, 0.0, 1.0);
        List<Double> b = List.of(1.0, 0.0, 0.0, 0.0);
        double sim = search.cosineSimilarity(a, b);
        // dot=1, |a|=sqrt(2), |b|=1 → 1/sqrt(2) ≈ 0.7071
        assertEquals(0.7071, sim, 0.01);
    }
}
