package com.finguard.agent;

import com.finguard.entity.Chunk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单次 Agent 运行的上下文
 * - evidence：本次运行检索到的全部 chunk，顺序即 [资料N] 的编号顺序，最终校验时按同样顺序回传
 * - trace：每一步工具调用的执行轨迹（轮次 / 工具名 / 参数 / 耗时 / 结果摘要）
 */
public class AgentContext {

    private static final int PREVIEW_LIMIT = 120;

    private final List<Chunk> evidence = new ArrayList<>();
    private final List<Map<String, Object>> trace = new ArrayList<>();

    public List<Chunk> getEvidence() {
        return evidence;
    }

    public List<Map<String, Object>> getTrace() {
        return trace;
    }

    /**
     * 加入证据 chunk（按 id 去重），返回它在证据列表中的编号（从 1 开始）
     */
    public int addEvidence(Chunk chunk) {
        for (int i = 0; i < evidence.size(); i++) {
            if (evidence.get(i).getId().equals(chunk.getId())) {
                return i + 1;
            }
        }
        evidence.add(chunk);
        return evidence.size();
    }

    /**
     * 记录一步工具执行轨迹
     */
    public void addTrace(int round, String tool, Map<String, Object> args,
                         long costMs, String result) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("round", round);
        step.put("tool", tool);
        step.put("args", args);
        step.put("costMs", costMs);
        step.put("resultPreview", truncate(result, PREVIEW_LIMIT));
        trace.add(step);
    }

    static String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
