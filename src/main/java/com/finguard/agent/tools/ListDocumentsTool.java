package com.finguard.agent.tools;

import com.finguard.agent.AgentContext;
import com.finguard.agent.AgentTool;
import com.finguard.entity.Document;
import com.finguard.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档清单工具：让 Agent 先看清知识库里有什么，再决定检索方向
 */
@Component
@RequiredArgsConstructor
public class ListDocumentsTool implements AgentTool {

    private final DocumentService documentService;

    @Override
    public String name() {
        return "list_documents";
    }

    @Override
    public String description() {
        return "列出知识库中已上传的文档（文件名与切片数量）。当不确定知识库覆盖哪些基金时先调用它。";
    }

    @Override
    public Map<String, Object> schema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<>());
        schema.put("required", List.of());
        return schema;
    }

    @Override
    public String execute(Map<String, Object> args, AgentContext ctx) {
        List<Document> documents = documentService.listDocuments();
        if (documents.isEmpty()) {
            return "知识库为空，没有任何文档。";
        }

        StringBuilder sb = new StringBuilder("知识库文档：\n");
        for (Document doc : documents) {
            sb.append("- id=").append(doc.getId())
                    .append(" | ").append(doc.getFileName())
                    .append(" | ").append(doc.getChunkCount()).append(" 个切片")
                    .append(" | ").append(doc.getStatus())
                    .append("\n");
        }
        return sb.toString().trim();
    }
}
