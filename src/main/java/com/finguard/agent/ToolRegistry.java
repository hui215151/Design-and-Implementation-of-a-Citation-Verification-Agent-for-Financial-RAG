package com.finguard.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表
 * 通过构造器收集所有 AgentTool 实现，统一生成 OpenAI tools schema 并分发执行
 */
@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public ToolRegistry(List<AgentTool> toolList) {
        for (AgentTool tool : toolList) {
            tools.put(tool.name(), tool);
        }
        log.info("Agent 已注册 {} 个工具: {}", tools.size(), tools.keySet());
    }

    /**
     * 生成 OpenAI 兼容的 tools 参数
     */
    public List<Map<String, Object>> toolSchemas() {
        List<Map<String, Object>> schemas = new ArrayList<>();
        for (AgentTool tool : tools.values()) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.name());
            function.put("description", tool.description());
            function.put("parameters", tool.schema());

            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("type", "function");
            wrapper.put("function", function);
            schemas.add(wrapper);
        }
        return schemas;
    }

    /**
     * 执行工具：任何异常都转成文本返回给模型，不打断 Agent 循环
     */
    public String execute(String name, Map<String, Object> args, AgentContext ctx) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            return "工具不存在: " + name;
        }
        try {
            return tool.execute(args, ctx);
        } catch (Exception e) {
            log.warn("工具 {} 执行失败: {}", name, e.getMessage());
            return "工具执行失败: " + e.getMessage();
        }
    }

    public List<AgentTool> getAll() {
        return new ArrayList<>(tools.values());
    }
}
