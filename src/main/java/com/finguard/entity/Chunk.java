package com.finguard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 切片表：存文档切片后的文本内容
 *
 * @TableName 告诉 MyBatis-Plus 这个类对应哪张数据库表
 * @Data      是 Lombok 注解，自动生成 getter/setter/toString/equals/hashCode
 */
@Data
@TableName("chunk")
public class Chunk {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的文档ID */
    private Long documentId;

    /** 切片序号 */
    private Integer chunkIndex;

    /** 切片内容 */
    private String content;

    /** 向量（JSON 数组字符串，后面接真 embedding 模型） */
    private String embedding;

    /** 创建时间 */
    private LocalDateTime createdAt;
}