package com.finguard.controller;

import com.finguard.entity.Document;
import com.finguard.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /**
     * 上传文档（先支持 txt/md）
     */
    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) {
        Document doc = documentService.upload(file);
        Map<String, Object> result = new HashMap<>();
        result.put("id", doc.getId());
        result.put("fileName", doc.getFileName());
        result.put("status", doc.getStatus());
        result.put("chunkCount", doc.getChunkCount());
        return result;
    }

    /**
     * 列出所有文档
     */
    @GetMapping
    public List<Document> list() {
        return documentService.listDocuments();
    }

    /**
     * 删除文档（级联删 chunk + 物理文件）
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        documentService.deleteDocument(id);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }
}
