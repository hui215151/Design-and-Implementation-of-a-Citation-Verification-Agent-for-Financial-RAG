package com.finguard.controller;

import com.finguard.entity.Conversation;
import com.finguard.entity.Message;
import com.finguard.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conversation")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    /**
     * 创建新对话
     */
    @PostMapping
    public Conversation create(@RequestBody Map<String, String> body) {
        String title = body.getOrDefault("title", "新对话");
        return conversationService.createConversation(title);
    }

    /**
     * 列出所有对话（按创建时间倒序）
     */
    @GetMapping
    public List<Conversation> list() {
        return conversationService.listConversations();
    }

    /**
     * 删除对话（级联删除该对话下的所有消息）
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        conversationService.deleteConversation(id);
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("success", true);
        return result;
    }

    /**
     * 问答（带校验，阻塞式）
     */
    @PostMapping("/{id}/ask")
    public Map<String, Object> ask(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String question = body.get("question");
        return conversationService.ask(id, question);
    }

    /**
     * 流式问答（带校验 + 存库，SSE 打字机效果）
     */
    @GetMapping(value = "/{id}/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@PathVariable Long id, @RequestParam String question) {
        SseEmitter emitter = new SseEmitter(60000L);
        conversationService.askStream(id, question, emitter);
        return emitter;
    }

    /**
     * 获取对话历史
     */
    @GetMapping("/{id}/history")
    public List<Message> history(@PathVariable Long id) {
        return conversationService.getHistory(id);
    }
}
