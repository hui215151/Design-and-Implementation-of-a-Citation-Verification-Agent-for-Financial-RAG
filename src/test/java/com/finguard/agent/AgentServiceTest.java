package com.finguard.agent;

import com.finguard.entity.Chunk;
import com.finguard.llm.ToolCall;
import com.finguard.llm.ToolCallResponse;
import com.finguard.service.VerificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * AgentService 单元测试
 * LLM 与工具注册表全部 mock，只验证循环控制逻辑：
 * 收敛、工具分发、轨迹记录、超轮数兜底
 */
@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock
    private LlmAgentClient llmAgentClient;

    @Mock
    private ToolRegistry toolRegistry;

    @Mock
    private VerificationService verificationService;

    private AgentService newService() {
        return new AgentService(llmAgentClient, toolRegistry, verificationService);
    }

    /**
     * 模型第一次就不调工具 → 直接收敛，轨迹为空
     */
    @Test
    void run_convergeWithoutTools_returnsAnswerAndEmptyTrace() {
        when(toolRegistry.toolSchemas()).thenReturn(List.of());
        when(llmAgentClient.chatWithTools(any(), any()))
                .thenReturn(textResponse("易方达蓝筹的基金代码是 005827[资料1]"));

        Map<String, Object> result = newService().run("易方达蓝筹的基金代码是多少");

        assertEquals("易方达蓝筹的基金代码是 005827[资料1]", result.get("answer"));
        assertTrue(((List<?>) result.get("trace")).isEmpty());
        verify(llmAgentClient, times(1)).chatWithTools(any(), any());
    }

    /**
     * 模型先调 search_knowledge，第二轮收敛 → 轨迹记录一轮工具调用
     */
    @Test
    void run_callsToolThenConverges_recordsTrace() {
        when(toolRegistry.toolSchemas()).thenReturn(List.of());
        when(toolRegistry.execute(anyString(), anyMap(), any()))
                .thenReturn("[资料1] 易方达蓝筹精选 基金代码 005827");

        when(llmAgentClient.chatWithTools(any(), any()))
                .thenReturn(toolCallResponse("search_knowledge", "{\"query\":\"易方达蓝筹 基金代码\"}"))
                .thenReturn(textResponse("基金代码是 005827[资料1]"));

        Map<String, Object> result = newService().run("易方达蓝筹的基金代码是多少");

        assertEquals("基金代码是 005827[资料1]", result.get("answer"));

        List<?> trace = (List<?>) result.get("trace");
        assertEquals(1, trace.size());

        Map<?, ?> step = (Map<?, ?>) trace.get(0);
        assertEquals(1, step.get("round"));
        assertEquals("search_knowledge", step.get("tool"));
        assertEquals("易方达蓝筹 基金代码", ((Map<?, ?>) step.get("args")).get("query"));
        assertNotNull(step.get("costMs"));

        verify(llmAgentClient, times(2)).chatWithTools(any(), any());
    }

    /**
     * 连续调用工具直到超过最大轮数 → 禁用工具兜底收敛
     */
    @Test
    void run_exceedsMaxRounds_fallsBackWithoutTools() {
        when(toolRegistry.toolSchemas()).thenReturn(List.of());
        when(toolRegistry.execute(anyString(), anyMap(), any())).thenReturn("[资料1] 部分内容");

        when(llmAgentClient.chatWithTools(any(), any()))
                .thenReturn(toolCallResponse("search_knowledge", "{\"query\":\"a\"}"))
                .thenReturn(toolCallResponse("search_knowledge", "{\"query\":\"b\"}"))
                .thenReturn(toolCallResponse("search_knowledge", "{\"query\":\"c\"}"))
                .thenReturn(textResponse("基于现有资料的兜底回答"));

        Map<String, Object> result = newService().run("反复检索也不收敛的问题");

        assertEquals("基于现有资料的兜底回答", result.get("answer"));
        assertEquals(AgentService.MAX_ROUNDS, ((List<?>) result.get("trace")).size());
        verify(llmAgentClient, times(4)).chatWithTools(any(), any());

        // 最后一次兜底必须禁用工具
        ArgumentCaptor<List<Map<String, Object>>> toolsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(llmAgentClient, times(4)).chatWithTools(any(), toolsCaptor.capture());
        assertTrue(toolsCaptor.getAllValues().get(3).isEmpty());
    }

    /**
     * 工具执行失败不打断循环，错误文本回填给模型
     */
    @Test
    void run_toolFailure_doesNotBreakLoop() {
        when(toolRegistry.toolSchemas()).thenReturn(List.of());
        when(toolRegistry.execute(anyString(), anyMap(), any()))
                .thenReturn("工具执行失败: 检索服务超时");

        when(llmAgentClient.chatWithTools(any(), any()))
                .thenReturn(toolCallResponse("search_knowledge", "{\"query\":\"易方达\"}"))
                .thenReturn(textResponse("检索失败，暂时无法回答[资料1]"));

        Map<String, Object> result = newService().run("易方达基金代码");

        assertNotNull(result.get("answer"));
        assertEquals(1, ((List<?>) result.get("trace")).size());
        verify(llmAgentClient, times(2)).chatWithTools(any(), any());
    }

    /**
     * 空问题直接拒绝，不发起 LLM 调用
     */
    @Test
    void run_blankQuestion_throws() {
        assertThrows(IllegalArgumentException.class, () -> newService().run("   "));
        verifyNoInteractions(llmAgentClient);
    }

    /**
     * 同一 chunk 被检索两次 → 复用同一个资料编号，不重复占用证据位
     */
    @Test
    void context_addEvidence_deduplicatesByChunkId() {
        AgentContext ctx = new AgentContext();

        int first = ctx.addEvidence(chunk(10L, "内容A"));
        int second = ctx.addEvidence(chunk(10L, "内容A"));
        int third = ctx.addEvidence(chunk(11L, "内容B"));

        assertEquals(1, first);
        assertEquals(1, second);
        assertEquals(2, third);
        assertEquals(2, ctx.getEvidence().size());
    }

    /**
     * 轨迹里的结果摘要必须截断，避免 trace 无限膨胀
     */
    @Test
    void context_addTrace_truncatesLongResult() {
        AgentContext ctx = new AgentContext();
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 500; i++) longText.append("字");

        ctx.addTrace(1, "search_knowledge", Map.of("query", "x"), 12L, longText.toString());

        String preview = (String) ctx.getTrace().get(0).get("resultPreview");
        assertTrue(preview.length() < longText.length());
        assertTrue(preview.endsWith("..."));
    }

    // ── 测试辅助 ──

    private ToolCallResponse textResponse(String content) {
        ToolCallResponse response = new ToolCallResponse();
        response.setContent(content);
        response.setToolCalls(List.of());
        response.setRawAssistantMessage(assistantMessage(content, List.of()));
        return response;
    }

    private ToolCallResponse toolCallResponse(String toolName, String arguments) {
        ToolCall call = new ToolCall();
        call.setId("call_" + toolName);
        call.setName(toolName);
        call.setArguments(arguments);

        ToolCallResponse response = new ToolCallResponse();
        response.setToolCalls(List.of(call));
        response.setRawAssistantMessage(assistantMessage("", List.of(call)));
        return response;
    }

    private Map<String, Object> assistantMessage(String content, List<ToolCall> calls) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", content);
        if (!calls.isEmpty()) {
            message.put("tool_calls", calls);
        }
        return message;
    }

    private Chunk chunk(Long id, String content) {
        Chunk chunk = new Chunk();
        chunk.setId(id);
        chunk.setContent(content);
        return chunk;
    }
}
