package com.finguard.agent.tools;

import com.finguard.agent.AgentContext;
import com.finguard.agent.AgentTool;
import com.finguard.entity.Chunk;
import com.finguard.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库检索工具：让 Agent 自己决定检索关键词与召回数量
 */
@Component
@RequiredArgsConstructor
public class SearchKnowledgeTool implements AgentTool {

    /** 单个片段喂给模型的字符上限，控制 context 膨胀 */
    private static final int SNIPPET_LIMIT = 300;

    private final SearchService searchService;

    @Override
    public String name() {
        return "search_knowledge";
    }

    @Override
    public String description() {
        return "检索基金年报知识库。输入检索关键词，返回最相关的资料片段，每段带 [资料N] 编号，"
                + "回答引用时使用该编号。首次检索结果不足时可换关键词再次调用。";
    }

    @Override
    public Map<String, Object> schema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("query", Map.of(
                "type", "string",
                "description", "检索关键词，尽量包含基金名称和具体指标，如「易方达蓝筹 净值增长率」"
        ));
        props.put("topK", Map.of(
                "type", "integer",
                "description", "返回片段数量，默认 3"
        ));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("query"));
        return schema;
    }

    @Override
    public String execute(Map<String, Object> args, AgentContext ctx) {
        Object queryObj = args.get("query");
        if (queryObj == null || queryObj.toString().isBlank()) {
            return "参数错误：query 不能为空";
        }
        String query = queryObj.toString();

        int topK = 3;
        Object k = args.get("topK");
        if (k instanceof Number) {
            topK = ((Number) k).intValue();
        }
        topK = Math.max(1, Math.min(topK, 8));

        List<Chunk> chunks = searchService.search(query, topK);
        if (chunks.isEmpty()) {
            return "没有检索到相关资料，可以尝试更换关键词。";
        }

        StringBuilder sb = new StringBuilder();
        for (Chunk chunk : chunks) {
            // 编号由上下文统一分配，保证和最终校验使用的证据列表顺序一致
            int ref = ctx.addEvidence(chunk);
            sb.append("[资料").append(ref).append("] ")
                    .append(truncate(chunk.getContent(), SNIPPET_LIMIT))
                    .append("\n\n");
        }
        return sb.toString().trim();
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
