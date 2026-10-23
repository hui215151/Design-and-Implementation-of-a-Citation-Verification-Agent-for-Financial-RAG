package com.finguard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("conversation")
public class Conversation {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的会话标题 */
    private String title;

    /** 创建时间 */
    private LocalDateTime createdAt;
}