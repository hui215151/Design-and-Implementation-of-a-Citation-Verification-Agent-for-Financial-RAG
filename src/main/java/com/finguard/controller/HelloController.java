package com.finguard.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 骨架验证接口：Step 1 结束时用它确认项目能跑起来。
 */
@RestController
@RequestMapping("/api")
public class HelloController {

    @Value("${spring.application.name}")
    private String appName;

    @Value("${llm.provider}")
    private String llmProvider;

    @GetMapping("/hello")
    public Map<String, Object> hello() {
        Map<String, Object> m = new HashMap<>();
        m.put("status", "UP");
        m.put("app", appName);
        m.put("llmProvider", llmProvider);
        m.put("serverTime", System.currentTimeMillis());
        return m;
    }
}
