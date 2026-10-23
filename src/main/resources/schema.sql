-- ────────────────────────────────────────────
-- FinGuard-RAG 建表 SQL（H2 自动执行）
-- ────────────────────────────────────────────

-- 文档表
CREATE TABLE IF NOT EXISTS document (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name   VARCHAR(500) NOT NULL,
    file_type   VARCHAR(20),
    file_size   BIGINT,
    file_path   VARCHAR(1000),
    status      VARCHAR(20) DEFAULT 'PROCESSING',
    chunk_count INT DEFAULT 0,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 切片表
CREATE TABLE IF NOT EXISTS chunk (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id    BIGINT NOT NULL,
    chunk_index    INT NOT NULL,
    content        CLOB,
    embedding      VARCHAR(100000),
    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_chunk_doc ON chunk(document_id);

-- 对话表
CREATE TABLE IF NOT EXISTS conversation (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(200),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 消息表
CREATE TABLE IF NOT EXISTS message (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id   BIGINT NOT NULL,
    role              VARCHAR(20) NOT NULL,
    content           CLOB,
    cited_chunk_ids   CLOB,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_message_conv ON message(conversation_id);

-- ★ 引用校验结果表
CREATE TABLE IF NOT EXISTS verification_result (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id         BIGINT,
    claim_text         CLOB,
    verdict            VARCHAR(20),
    evidence_chunk_id  BIGINT,
    evidence_snippet   CLOB,
    reason             CLOB,
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_verify_msg ON verification_result(message_id);
