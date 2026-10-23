package com.finguard.service;

import com.finguard.entity.Chunk;
import com.finguard.llm.LlmClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 问答服务：检索 → 拼 prompt → 调 LLM → 返回带引用的回答
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final SearchService searchService;
    private final LlmClient llmClient;
    private final VerificationService verificationService;

    /**
     * 问答主入口
     */
    public Map<String, Object> ask(String question) {
        // 1. 检索 top3 相关 chunk
        List<Chunk> chunks = searchService.search(question, 3);

        // 2. 如果没检索到，直接拒答（这是防幻觉的关键）
        if (chunks.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("answer", "我没有找到相关资料，无法回答这个问题。");
            result.put("citations", Collections.emptyList());
            result.put("verifications", Collections.emptyList());
            return result;
        }

        // 3. 拼 prompt：把检索到的资料编号，要求 LLM 必须带引用
        StringBuilder context = new StringBuilder();
        List<Map<String, Object>> citations = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Chunk c = chunks.get(i);
            String refId = "[资料" + (i + 1) + "]";
            context.append(refId).append(c.getContent()).append("\n\n");

            Map<String, Object> cite = new HashMap<>();
            cite.put("ref", refId);
            cite.put("chunkId", c.getId());
            cite.put("snippet", c.getContent().substring(0, Math.min(100, c.getContent().length())));
            citations.add(cite);
        }

        String systemPrompt = "你是一个金融问答助手。请严格根据下面提供的资料回答用户问题。" +
                "规则：\n" +
                "1. 只能使用资料里的信息，不要编造\n" +
                "2. 每句话后面必须标注引用编号，如 [资料1]\n" +
                "3. 如果资料里没有答案，直接说'根据现有资料无法回答'\n" +
                "4. 不要输出资料里没有的数字、日期、人名";

        String userMessage = "资料：\n" + context.toString() + "\n问题：" + question;

        // 4. 调 LLM
        String answer = llmClient.chat(systemPrompt, userMessage);

        // 5. ★ 引用校验：把回答拆成主张，逐条和证据对比
        List<Map<String, Object>> verifications = verificationService.verify(answer, chunks);

        // 6. 返回回答 + 引用 + 校验结果
        Map<String, Object> result = new HashMap<>();
        result.put("answer", answer);
        result.put("citations", citations);
        result.put("verifications", verifications);
        return result;
    }
}
