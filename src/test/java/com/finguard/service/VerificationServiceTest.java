package com.finguard.service;

import com.finguard.entity.Chunk;
import com.finguard.entity.VerificationResult;
import com.finguard.llm.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * VerificationService 单元测试
 * 覆盖：splitClaims / findEvidence / checkNumbers / buildResult 四个纯算法方法
 * 不依赖 Spring 容器，直接 new 对象 + mock LlmClient
 */
@ExtendWith(MockitoExtension.class)
class VerificationServiceTest {

    @Mock
    private LlmClient llmClient;

    private VerificationService service;

    @BeforeEach
    void setUp() {
        service = new VerificationService(llmClient);
    }

    // ────────────── splitClaims ──────────────

    @Test
    void splitClaims_standard_twoRefs() {
        String answer = "基金代码005827[资料1] 2023年收益12.3%[资料2]";
        List<VerificationService.Claim> claims = service.splitClaims(answer);

        assertEquals(2, claims.size());
        assertEquals("基金代码005827", claims.get(0).text);
        assertEquals("[资料1]", claims.get(0).ref);
        assertEquals("2023年收益12.3%", claims.get(1).text);
        assertEquals("[资料2]", claims.get(1).ref);
    }

    @Test
    void splitClaims_noRef_wholeTextAsOne() {
        String answer = "基金代码005827，2023年收益12.3%";
        List<VerificationService.Claim> claims = service.splitClaims(answer);

        assertEquals(1, claims.size());
        assertEquals("基金代码005827，2023年收益12.3%", claims.get(0).text);
        assertNull(claims.get(0).ref);
    }

    @Test
    void splitClaims_threeRefs() {
        String answer = "成立于2020年[资料1] 规模500亿[资料2] 经理张三[资料3]";
        List<VerificationService.Claim> claims = service.splitClaims(answer);

        assertEquals(3, claims.size());
        assertEquals("[资料1]", claims.get(0).ref);
        assertEquals("[资料2]", claims.get(1).ref);
        assertEquals("[资料3]", claims.get(2).ref);
    }

    @Test
    void splitClaims_emptyString() {
        List<VerificationService.Claim> claims = service.splitClaims("");
        assertTrue(claims.isEmpty());
    }

    @Test
    void splitClaims_trailingPunctuationIgnored() {
        // 回答末尾的句号不应被当成一条主张，否则会产出「无支撑 · 没有标注引用来源」的噪音
        String answer = "基金代码005827[资料1]。";
        List<VerificationService.Claim> claims = service.splitClaims(answer);

        assertEquals(1, claims.size());
        assertEquals("基金代码005827", claims.get(0).text);
        assertEquals("[资料1]", claims.get(0).ref);
    }

    // ────────────── findEvidence ──────────────

    @Test
    void findEvidence_资料1_mapsToFirstChunk() {
        Chunk c0 = new Chunk(); c0.setId(100L);
        Chunk c1 = new Chunk(); c1.setId(200L);
        List<Chunk> chunks = List.of(c0, c1);

        VerificationService.Claim claim = new VerificationService.Claim("text", "[资料1]");
        Chunk found = service.findEvidence(claim, chunks);

        assertNotNull(found);
        assertEquals(100L, found.getId());
    }

    @Test
    void findEvidence_资料2_mapsToSecondChunk() {
        Chunk c0 = new Chunk(); c0.setId(100L);
        Chunk c1 = new Chunk(); c1.setId(200L);
        List<Chunk> chunks = List.of(c0, c1);

        VerificationService.Claim claim = new VerificationService.Claim("text", "[资料2]");
        Chunk found = service.findEvidence(claim, chunks);

        assertNotNull(found);
        assertEquals(200L, found.getId());
    }

    @Test
    void findEvidence_noRef_returnsNull() {
        List<Chunk> chunks = List.of(new Chunk());
        VerificationService.Claim claim = new VerificationService.Claim("text", null);

        assertNull(service.findEvidence(claim, chunks));
    }

    @Test
    void findEvidence_outOfRange_returnsNull() {
        Chunk c0 = new Chunk(); c0.setId(100L);
        List<Chunk> chunks = List.of(c0);
        VerificationService.Claim claim = new VerificationService.Claim("text", "[资料5]");

        assertNull(service.findEvidence(claim, chunks));
    }

    // ────────────── checkNumbers ──────────────

    @Test
    void checkNumbers_allMatched_returnsNull() {
        String claim = "收益率12.3%";
        String evidence = "2023年收益率12.3%，规模500亿";

        assertNull(service.checkNumbers(claim, evidence));
    }

    @Test
    void checkNumbers_numberMismatch_returnsReason() {
        String claim = "收益率13.2%";
        String evidence = "2023年收益率12.3%";

        String result = service.checkNumbers(claim, evidence);
        assertNotNull(result);
        assertTrue(result.contains("13.2%"));
        assertTrue(result.contains("未找到"));
    }

    @Test
    void checkNumbers_noNumbers_returnsNull() {
        String claim = "基金代码005827";       // 005827 被当数字
        String evidence = "基金代码005827";
        assertNull(service.checkNumbers(claim, evidence));
    }

    @Test
    void checkNumbers_dateFormatMatched() {
        String claim = "2020年成立";
        String evidence = "该基金成立于2020年3月";
        assertNull(service.checkNumbers(claim, evidence));
    }

    @Test
    void checkNumbers_dateMismatch() {
        String claim = "2021年成立";
        String evidence = "该基金成立于2020年3月";

        String result = service.checkNumbers(claim, evidence);
        assertNotNull(result);
        assertTrue(result.contains("2021"));
    }

    // ────────────── buildResult ──────────────

    @Test
    void buildResult_fieldsCorrect() {
        Chunk chunk = new Chunk();
        chunk.setId(42L);
        chunk.setContent("这是证据原文用于测试");

        VerificationService.Claim claim = new VerificationService.Claim("基金代码005827", "[资料1]");
        VerificationResult vr = service.buildResult(claim, chunk, "SUPPORTED", "数字匹配");

        assertEquals("基金代码005827", vr.getClaimText());
        assertEquals("SUPPORTED", vr.getVerdict());
        assertEquals(42L, vr.getEvidenceChunkId());
        assertEquals("数字匹配", vr.getReason());
        assertNotNull(vr.getEvidenceSnippet());
        assertEquals("这是证据原文用于测试", vr.getEvidenceSnippet());
        assertNull(vr.getMessageId());   // messageId 还没 set（由调用方 ConversationService 关联）
    }

    @Test
    void buildResult_nullEvidence_noChunkId() {
        VerificationService.Claim claim = new VerificationService.Claim("某主张", null);
        VerificationResult vr = service.buildResult(claim, null, "UNSUPPORTED", "没有引用");

        assertNull(vr.getEvidenceChunkId());
        assertNull(vr.getEvidenceSnippet());
    }

    // ────────────── verify 整体流程（数字硬比对命中，不调 LLM）──────────────

    @Test
    void verify_numberMismatch_returnsContradicted() {
        Chunk chunk = new Chunk();
        chunk.setId(1L);
        chunk.setContent("2023年收益率12.3%，规模500亿");
        List<Chunk> chunks = List.of(chunk);

        // claim 里的数字和 evidence 对不上 → 直接判 CONTRADICTED，不调 LLM
        List<VerificationResult> results = service.verify("收益率13.2%[资料1]", chunks);

        assertEquals(1, results.size());
        assertEquals("CONTRADICTED", results.get(0).getVerdict());
        assertTrue(results.get(0).getReason().contains("数字不匹配"));

        // 验证：nliCheck / nliReason 没有被调用
        verify(llmClient, never()).chat(anyString(), anyString());
    }

    @Test
    void verify_noRef_markedUnsupported() {
        List<Chunk> chunks = List.of(new Chunk());

        // 没有引用标记 → 直接 UNSUPPORTED，不调 LLM
        List<VerificationResult> results = service.verify("基金代码005827，收益率12.3%", chunks);

        assertEquals(1, results.size());
        assertEquals("UNSUPPORTED", results.get(0).getVerdict());
        verify(llmClient, never()).chat(anyString(), anyString());
    }
}
