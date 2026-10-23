package com.finguard.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finguard.entity.VerificationResult;
import com.finguard.llm.ToolCall;
import com.finguard.llm.ToolCallResponse;
import com.finguard.service.VerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具调用型 Agent 主循环
 *
 * 与 ChatService 固定流水线的区别：检索、自检的时机和次数由模型自己决定。
 * 收敛条件：模型不再返回 tool_calls；兜底条件：达到最大轮数后禁用工具强制文本作答。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    /** 最大推理轮数，防止工具调用死循环 */
    public static final int MAX_ROUNDS = 3;

    /** 单条工具结果喂回模型的字符上限，控制 context 膨胀 */
    private static final int TOOL_RESULT_LIMIT = 3000;

    private static final String SYSTEM_PROMPT =
            "你是一个金融问答 Agent，可以调用工具检索和校验资料。\n\n"
            + "可用工具：\n"
            + "- search_knowledge(query, topK)：检索基金年报知识库\n"
            + "- verify_claim(claim, ref)：校验一条主张是否有资料支撑，ref 是 [资料N] 中的编号 N\n"
            + "- list_documents()：查看知识库中有哪些文档\n\n"
            + "工作规则：\n"
            + "1. 回答任何涉及具体事实或数字的问题前，必须先调用 search_knowledge\n"
            + "2. 首次检索结果不足时，换关键词再次检索\n"
            + "3. 回答中的每个事实句必须以 [资料N] 标记结尾，N 对应检索结果编号\n"
            + "4. 只使用检索到的资料内容，不得编造数字、日期、人名\n"
            + "5. 资料中没有答案时，明确说明「根据现有资料无法回答」\n"
            + "6. 信息足够后直接输出最终答案，不要再调用工具";

    private final LlmAgentClient llmAgentClient;
    private final ToolRegistry toolRegistry;
    private final VerificationService verificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 执行一次 Agent 问答
     */
    public Map<String, Object> run(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }

        AgentContext ctx = new AgentContext();
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.add(Map.of("role", "user", "content", question));

        List<Map<String, Object>> tools = toolRegistry.toolSchemas();

        for (int round = 1; round <= MAX_ROUNDS; round++) {
            ToolCallResponse response = llmAgentClient.chatWithTools(messages, tools);

            // 模型认为信息已足够 → 收敛
            if (!response.hasToolCalls()) {
                return buildResult(response.getContent(), ctx);
            }

            // 回填 assistant 消息（含 tool_calls 结构），否则下一轮会 400
            messages.add(response.getRawAssistantMessage());

            for (ToolCall call : response.getToolCalls()) {
                Map<String, Object> args = parseArgs(call.getArguments());

                long t0 = System.currentTimeMillis();
                String result = toolRegistry.execute(call.getName(), args, ctx);
                long costMs = System.currentTimeMillis() - t0;

                ctx.addTrace(round, call.getName(), args, costMs, result);

                Map<String, Object> toolMessage = new LinkedHashMap<>();
                toolMessage.put("role", "tool");
                toolMessage.put("tool_call_id", call.getId());
                toolMessage.put("content", truncate(result, TOOL_RESULT_LIMIT));
                messages.add(toolMessage);
            }
        }

        // 超过最大轮数：禁用工具，强制模型基于已有信息作答
        log.warn("Agent 达到最大轮数 {}，进入兜底收敛", MAX_ROUNDS);
        ToolCallResponse fallback = llmAgentClient.chatWithTools(messages, Collections.emptyList());
        String answer = fallback.getContent();
        if (answer == null || answer.isBlank()) {
            answer = "已达到最大推理轮数，未能得出可靠结论。";
        }
        return buildResult(answer, ctx);
    }

    /**
     * 组装返回结果：答案 + 执行轨迹 + 引用校验
     */
    private Map<String, Object> buildResult(String answer, AgentContext ctx) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("answer", answer == null ? "" : answer);
        result.put("trace", ctx.getTrace());
        result.put("evidenceCount", ctx.getEvidence().size());
        result.put("verifications", verifyAnswer(answer, ctx));
        return result;
    }

    /**
     * 对最终回答做逐句引用校验
     * 回答里的 [资料N] 编号由 AgentContext 统一分配，与 evidence 列表同序，可直接对齐
     */
    private List<Map<String, Object>> verifyAnswer(String answer, AgentContext ctx) {
        if (answer == null || answer.isBlank() || ctx.getEvidence().isEmpty()) {
            return Collections.emptyList();
        }

        List<VerificationResult> results = verificationService.verify(answer, ctx.getEvidence());
        List<Map<String, Object>> verifications = new ArrayList<>();
        for (VerificationResult vr : results) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("claim", vr.getClaimText());
            item.put("verdict", vr.getVerdict());
            item.put("reason", vr.getReason());
            item.put("evidenceChunkId", vr.getEvidenceChunkId());
            verifications.add(item);
        }
        return verifications;
    }

    /**
     * 解析工具参数 JSON，解析失败返回空 Map，交给工具自己报参数错误
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgs(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(argumentsJson, Map.class);
        } catch (Exception e) {
            log.warn("工具参数解析失败: {}", argumentsJson);
            return Collections.emptyMap();
        }
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
