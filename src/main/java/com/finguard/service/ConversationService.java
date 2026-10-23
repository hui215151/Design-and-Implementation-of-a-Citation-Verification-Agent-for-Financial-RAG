package com.finguard.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finguard.entity.*;
import com.finguard.mapper.*;
import com.finguard.llm.LlmClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 对话服务：带对话历史的问答 + 校验
 */
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final SearchService searchService;
    private final LlmClient llmClient;
    private final VerificationService verificationService;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final VerificationResultMapper verificationResultMapper;

    /**
     * 创建新对话
     */
    public Conversation createConversation(String title) {
        Conversation conv = new Conversation();
        conv.setTitle(title);
        conv.setCreatedAt(LocalDateTime.now());
        conversationMapper.insert(conv);
        return conv;
    }

    /**
     * 列出所有对话（按创建时间倒序）
     */
    public List<Conversation> listConversations() {
        return conversationMapper.selectList(
                new QueryWrapper<Conversation>().orderByDesc("created_at")
        );
    }

    /**
     * 删除对话（级联删除该对话下的所有消息）
     */
    public void deleteConversation(Long id) {
        messageMapper.delete(
                new QueryWrapper<Message>().eq("conversation_id", id)
        );
        conversationMapper.deleteById(id);
    }

    /**
     * 带校验的问答（非流式）
     */
    public Map<String, Object> ask(Long conversationId, String question) {
        // 1. 检索
        List<Chunk> chunks = searchService.search(question, 3);

        // 2. 保存用户消息
        Message userMsg = new Message();
        userMsg.setConversationId(conversationId);
        userMsg.setRole("user");
        userMsg.setContent(question);
        userMsg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(userMsg);

        if (chunks.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("answer", "我没有找到相关资料，无法回答这个问题。");
            result.put("verifications", Collections.emptyList());
            return result;
        }

        // 3. 拼 prompt
        StringBuilder context = new StringBuilder();
        List<Map<String, Object>> citations = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Chunk c = chunks.get(i);
            String refId = "[资料" + (i + 1) + "]";
            context.append(refId).append(c.getContent()).append("\n\n");
            Map<String, Object> cite = new HashMap<>();
            cite.put("ref", refId);
            cite.put("chunkId", c.getId());
            citations.add(cite);
        }

        String systemPrompt = "你是一个金融问答助手。请严格根据下面提供的资料回答用户问题。" +
                "规则：1.只能用资料里的信息 2.每句话后面标引用[资料N] 3.资料没有就说无法回答 4.不要编造数字";
        String userMessage = "资料：\n" + context + "\n问题：" + question;

        // 4. 调 LLM
        String answer = llmClient.chat(systemPrompt, userMessage);

        // 5. 保存助手消息
        Message assistantMsg = new Message();
        assistantMsg.setConversationId(conversationId);
        assistantMsg.setRole("assistant");
        assistantMsg.setContent(answer);
        assistantMsg.setCitedChunkIds(citations.toString());
        assistantMsg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(assistantMsg);

        // 6. 校验
        List<Map<String, Object>> verifications = verificationService.verify(answer, chunks);

        // 7. 把校验结果和 message_id 关联
        for (Map<String, Object> v : verifications) {
            if (v.get("evidenceChunkId") != null) {
                VerificationResult vr = new VerificationResult();
                vr.setMessageId(assistantMsg.getId());
                vr.setClaimText((String) v.get("claim"));
                vr.setVerdict((String) v.get("verdict"));
                vr.setEvidenceChunkId((Long) v.get("evidenceChunkId"));
                vr.setReason((String) v.get("reason"));
                verificationResultMapper.insert(vr);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("answer", answer);
        result.put("citations", citations);
        result.put("verifications", verifications);
        return result;
    }

    /**
     * 获取对话历史
     */
    public List<Message> getHistory(Long conversationId) {
        return messageMapper.selectList(
                new QueryWrapper<Message>().eq("conversation_id", conversationId).orderByAsc("id")
        );
    }
}
