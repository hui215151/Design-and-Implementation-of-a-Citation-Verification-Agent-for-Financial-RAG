package com.finguard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档表：存用户上传的 PDF / TXT / MD 文件元信息
 *
 * @TableName 告诉 MyBatis-Plus 这个类对应哪张数据库表
 * @Data      是 Lombok 注解，自动生成 getter/setter/toString/equals/hashCode
 */
@Data
@TableName("document")
public class Document {

    /**
     * 主键
     * @TableId 标注主键
     * IdType.AUTO 表示数据库自增（H2/MySQL 都是自增主键）
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 原始文件名，如 "基金年报2023.pdf" */
    private String fileName;

    /** 文件类型：pdf / txt / md */
    private String fileType;

    /** 文件大小，单位字节 */
    private Long fileSize;

    /** 文件在服务器上的存储路径 */
    private String filePath;

    /**
     * 处理状态：
     * PROCESSING = 上传完，正在切片+embedding
     * READY      = 处理完，可以检索
     * FAILED     = 处理失败
     */
    private String status;

    /** 切片数量：这篇文档切成了多少块 */
    private Integer chunkCount;

    /** 上传时间 */
    private LocalDateTime createdAt;
}
