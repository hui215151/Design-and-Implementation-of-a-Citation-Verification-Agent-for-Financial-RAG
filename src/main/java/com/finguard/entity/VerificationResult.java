package com.finguard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ★ 核心：引用校验结果表
 * 一条 AI 回答拆成 N 条原子主张（claim），每条独立校验，打 verdict
 */
@Data
@TableName("verification_result")
public class VerificationResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 message.id（哪条回答的校验结果） */
    private Long messageId;

    /**
     * 原子主张原文，如 "该基金2023年收益率12.3%"
     * 一句话拆成最小可判断的单元
     */
    private String claimText;

    /**
     * 校验结论：
     * SUPPORTED     = 证据能支撑 ✅
     * PARTIAL       = 证据部分支撑 ⚠️
     * UNSUPPORTED   = 无证据支撑 ❌
     * CONTRADICTED  = 和证据矛盾 ❌
     */
    private String verdict;

    /** 匹配到的证据 chunk id */
    private Long evidenceChunkId;

    /** 证据原文片段（截取的那几句） */
    private String evidenceSnippet;

    /**
     * 校验原因，如：
     * "数字不匹配：回答12.3%，证据13.2%"
     * "语义矛盾：证据说2020年成立，回答说2021年"
     * "未找到相关证据"
     */
    private String reason;

    private LocalDateTime createdAt;
}
