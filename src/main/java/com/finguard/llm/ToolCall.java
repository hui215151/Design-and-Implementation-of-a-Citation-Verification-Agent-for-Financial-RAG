package com.finguard.llm;

import lombok.Data;

/**
 * LLM 发起的一次工具调用
 */
@Data
public class ToolCall {

    /** 调用 id，回填工具结果时必须原样带回 */
    private String id;

    /** 工具名 */
    private String name;

    /** 参数 JSON 字符串，如 {"query":"易方达"} */
    private String arguments;
}
