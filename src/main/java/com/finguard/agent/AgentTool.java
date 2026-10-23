package com.finguard.agent;

import java.util.Map;

/**
 * Agent 可调用的工具
 * 实现类只要声明 name / description / schema，并实现 execute，
 * 就会被 ToolRegistry 自动收集并暴露给 LLM
 */
public interface AgentTool {

    /** 工具名，LLM 通过这个名字发起调用 */
    String name();

    /** 工具说明，直接进入 function description，影响模型的选择 */
    String description();

    /** 参数的 JSON Schema（即 function.parameters） */
    Map<String, Object> schema();

    /**
     * 执行工具
     * 约定：失败也返回文本（如 "工具执行失败: xxx"），不抛异常，
     * 让模型自己决定重试或换参数，避免一次工具失败打断整个循环
     */
    String execute(Map<String, Object> args, AgentContext ctx);
}
