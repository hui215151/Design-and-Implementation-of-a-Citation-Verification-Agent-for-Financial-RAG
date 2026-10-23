package com.finguard.controller;

import com.finguard.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 问答接口
     * 入参：{"question": "易方达蓝筹精选的基金代码是多少？"}
     */
    @PostMapping
    public Map<String, Object> chat(@RequestBody Map<String, String> body) {
        String question = body.get("question");
        return chatService.ask(question);
    }
}
