package com.finguard.llm;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 带工具调用的 LLM 响应
 * toolCalls 为空表示模型认为信息已足够，可以收敛输出最终答案
 */
@Data
public class ToolCallResponse {

    /** 模型输出的文本内容（有工具调用时通常为空） */
    private String content;

    /** 本轮要求的工具调用 */
    private List<ToolCall> toolCalls = new ArrayList<>();

    /** 原样可回填进 messages 的 assistant 消息（含 tool_calls 结构） */
    private Map<String, Object> rawAssistantMessage;

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
