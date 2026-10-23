package com.finguard.controller;

import com.finguard.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 问答接口（阻塞式，一次性返回完整回答）
     */
    @PostMapping
    public Map<String, Object> chat(@RequestBody Map<String, String> body) {
        String question = body.get("question");
        return chatService.ask(question);
    }

    /**
     * 流式问答（SSE 打字机效果，逐字推送）
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String question) {
        SseEmitter emitter = new SseEmitter(60000L);   // 超时 60 秒
        chatService.askStream(question, emitter);
        return emitter;
    }
}
