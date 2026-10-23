package com.finguard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finguard.entity.Chunk;
import com.finguard.entity.Document;
import com.finguard.mapper.ChunkMapper;
import com.finguard.mapper.DocumentMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentMapper documentMapper;
    private final ChunkMapper chunkMapper;
    private final EmbeddingService embeddingService;
    private final SearchService searchService;  // 用于上传/删除后清 embedding 缓存
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 切片大小：500 字 */
    private static final int CHUNK_SIZE = 500;
    /** 重叠：100 字（避免语义被切断） */
    private static final int CHUNK_OVERLAP = 100;

    /**
     * 上传文档：保存文件 → 切片 → 生成向量 → 入库
     */
    public Document upload(MultipartFile file) {
        // 1. 保存文档元信息
        Document doc = new Document();
        doc.setFileName(file.getOriginalFilename());
        doc.setFileType(getFileType(file.getOriginalFilename()));
        doc.setFileSize(file.getSize());
        doc.setStatus("PROCESSING");
        doc.setChunkCount(0);
        doc.setCreatedAt(LocalDateTime.now());
        documentMapper.insert(doc);

        // 2. 读取文本内容（按文件类型分流：PDF → PDFBox，txt/md → UTF-8 直接读）
        String content;
        try {
            content = extractText(file);
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败", e);
        }

        // 3. 切片
        List<String> chunks = splitText(content);

        // 4. 逐块生成 embedding + 入库
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = new Chunk();
            chunk.setDocumentId(doc.getId());
            chunk.setChunkIndex(i);
            chunk.setContent(chunks.get(i));
            chunk.setEmbedding(vectorize(chunks.get(i)));
            chunk.setCreatedAt(LocalDateTime.now());
            chunkMapper.insert(chunk);
        }

        // 5. 清 embedding 缓存（新 chunk 需要下次 search 能被检索到）
        searchService.invalidateCache();

        // 6. 更新文档状态
        doc.setStatus("READY");
        doc.setChunkCount(chunks.size());
        documentMapper.updateById(doc);

        return doc;
    }

    /**
     * 文本切片：按 500 字切，重叠 100 字
     */
    List<String> splitText(String text) {
        List<String> result = new ArrayList<>();
        int start = 0;
        int len = text.length();
        while (start < len) {
            int end = Math.min(start + CHUNK_SIZE, len);
            result.add(text.substring(start, end));
            start += (CHUNK_SIZE - CHUNK_OVERLAP);
        }
        return result;
    }

    /**
     * 把文本转成 embedding 向量，存成 JSON 字符串
     */
    private String vectorize(String text) {
        try {
            List<Double> vector = embeddingService.embed(text);
            return objectMapper.writeValueAsString(vector);
        } catch (Exception e) {
            throw new RuntimeException("向量序列化失败", e);
        }
    }

    private String getFileType(String fileName) {
        if (fileName == null) return "txt";
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(dot + 1).toLowerCase() : "txt";
    }

    /**
     * 从上传的文件里提取纯文本，按文件类型分流
     * - .pdf → PDFBox
     * - .txt / .md / 默认 → UTF-8 直接读
     */
    private String extractText(MultipartFile file) throws IOException {
        String type = getFileType(file.getOriginalFilename());
        if ("pdf".equals(type)) {
            return extractPdfText(file);
        } else {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * PDF 文本提取
     * PDDocument.load() 载入文件流，try-with-resources 自动 close 防止内存泄漏
     * PDFTextStripper 按物理位置排序提取，保留换行结构利于后续切片
     */
    private String extractPdfText(MultipartFile file) throws IOException {
        try (PDDocument doc = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        }
    }

    public List<Document> listDocuments() {
        return documentMapper.selectList(
                new QueryWrapper<Document>().orderByDesc("created_at")
        );
    }

    public void deleteDocument(Long id) {
        Document doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new IllegalArgumentException("文档不存在: " + id);
        }

        // 先删该文档的所有 chunk
        chunkMapper.delete(
                new QueryWrapper<Chunk>().eq("document_id", id)
        );

        // 删物理文件
        if (doc.getFilePath() != null) {
            File file = new File(doc.getFilePath());
            if (file.exists()) {
                file.delete();
            }
        }

        // 删文档记录本身
        documentMapper.deleteById(id);

        // 清 embedding 缓存（chunk 被删了，缓存里的旧 chunkId 得清掉）
        searchService.invalidateCache();
    }
}
