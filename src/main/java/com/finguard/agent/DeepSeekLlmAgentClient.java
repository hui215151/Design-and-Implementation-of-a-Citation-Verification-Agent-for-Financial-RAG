package com.finguard.agent;

import com.finguard.llm.LlmClient;
import com.finguard.llm.ToolCallResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * LlmAgentClient 的 DeepSeek 实现，委托给已有的 LlmClient
 */
@Component
@RequiredArgsConstructor
public class DeepSeekLlmAgentClient implements LlmAgentClient {

    private final LlmClient llmClient;

    @Override
    public ToolCallResponse chatWithTools(List<Map<String, Object>> messages,
                                          List<Map<String, Object>> tools) {
        return llmClient.chatWithTools(messages, tools);
    }
}
