package com.finguard.service;

import com.finguard.mapper.ChunkMapper;
import com.finguard.mapper.DocumentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DocumentService 单元测试
 * 只测 splitText（纯字符串切分，不需要 mock 外部依赖）
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private ChunkMapper chunkMapper;

    @Test
    void splitText_emptyString_returnsEmpty() {
        DocumentService service = new DocumentService(documentMapper, chunkMapper, null);
        List<String> chunks = service.splitText("");
        assertTrue(chunks.isEmpty());
    }

    @Test
    void splitText_shortText_singleChunk() {
        DocumentService service = new DocumentService(documentMapper, chunkMapper, null);
        List<String> chunks = service.splitText("hello world");
        assertEquals(1, chunks.size());
        assertEquals("hello world", chunks.get(0));
    }

    @Test
    void splitText_exactChunkSize_twoChunks() {
        DocumentService service = new DocumentService(documentMapper, chunkMapper, null);
        // 500 字 → start=0[0,500] + start=400[400,500] → 2 块
        String text = "a".repeat(500);
        List<String> chunks = service.splitText(text);

        assertEquals(2, chunks.size());
        assertEquals(500, chunks.get(0).length());
        assertEquals(100, chunks.get(1).length());   // 第二块只有重叠的 100 字
    }

    @Test
    void splitText_twoChunks_overlapWorks() {
        DocumentService service = new DocumentService(documentMapper, chunkMapper, null);
        // 1000 字 → start=0[0,500], start=400[400,900], start=800[800,1000] → 3 块
        String text = "a".repeat(1000);
        List<String> chunks = service.splitText(text);

        assertEquals(3, chunks.size());
        assertEquals(500, chunks.get(0).length());
        assertEquals(500, chunks.get(1).length());
        assertEquals(200, chunks.get(2).length());
    }

    @Test
    void splitText_threeChunks_correctSizes() {
        DocumentService service = new DocumentService(documentMapper, chunkMapper, null);
        // 1500 字 → 4 块：[0,500], [400,900], [800,1300], [1200,1500]
        String text = "a".repeat(1500);
        List<String> chunks = service.splitText(text);

        assertEquals(4, chunks.size());
        assertEquals(500, chunks.get(0).length());
        assertEquals(500, chunks.get(1).length());
        assertEquals(500, chunks.get(2).length());
        assertEquals(300, chunks.get(3).length());
    }

    @Test
    void splitText_partialFinalChunk() {
        DocumentService service = new DocumentService(documentMapper, chunkMapper, null);
        // 700 字 → chunk1[0,500], chunk2[400,700]
        String text = "a".repeat(700);
        List<String> chunks = service.splitText(text);

        assertEquals(2, chunks.size());
        assertEquals(500, chunks.get(0).length());
        assertEquals(300, chunks.get(1).length());   // 最后一块不够 500
    }

    @Test
    void splitText_noGap_betweenChunks() {
        DocumentService service = new DocumentService(documentMapper, chunkMapper, null);
        // 验证相邻 chunk 有重叠（chunk1 的后 100 字 == chunk2 的前 100 字）
        String text = "abcdefghij".repeat(200);   // 2000 字
        List<String> chunks = service.splitText(text);

        // start 每次 +400：0,400,800,1200,1600 → 5 块
        assertEquals(5, chunks.size());
        // chunk1 [0,500] 和 chunk2 [400,900] 重叠区 [400,500]
        assertEquals(chunks.get(0).substring(400), chunks.get(1).substring(0, 100));
    }
}
