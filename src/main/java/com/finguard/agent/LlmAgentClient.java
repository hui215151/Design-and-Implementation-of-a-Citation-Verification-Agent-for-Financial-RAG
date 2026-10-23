package com.finguard.agent;

import com.finguard.llm.ToolCallResponse;

import java.util.List;
import java.util.Map;

/**
 * Agent 循环依赖的 LLM 能力抽象
 * 单独抽接口的原因：AgentService 的收敛/分发/兜底逻辑可以脱离真实 API 做单元测试，
 * 后续换模型（DeepSeek / Qwen / GPT）也只需换实现
 */
public interface LlmAgentClient {

    /**
     * 发起一轮带工具的对话
     *
     * @param messages 完整消息列表
     * @param tools    工具 schema；传空列表表示禁用工具
     */
    ToolCallResponse chatWithTools(List<Map<String, Object>> messages,
                                   List<Map<String, Object>> tools);
}
