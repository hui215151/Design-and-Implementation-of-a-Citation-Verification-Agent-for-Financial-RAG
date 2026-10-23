package com.finguard.agent.tools;

import com.finguard.agent.AgentContext;
import com.finguard.agent.AgentTool;
import com.finguard.entity.Chunk;
import com.finguard.entity.VerificationResult;
import com.finguard.service.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单条主张校验工具：让 Agent 在作答前自己抽查关键数字是否有证据支撑
 * 复用 VerificationService 的「数字硬比对 + LLM 蕴含判断」双路校验
 */
@Component
@RequiredArgsConstructor
public class VerifyClaimTool implements AgentTool {

    private final VerificationService verificationService;

    @Override
    public String name() {
        return "verify_claim";
    }

    @Override
    public String description() {
        return "校验一条主张是否能被已检索到的资料支撑。传入主张原文和它对应的资料编号 [资料N] 中的 N，"
                + "返回 SUPPORTED / PARTIAL / UNSUPPORTED / CONTRADICTED 及理由。";
    }

    @Override
    public Map<String, Object> schema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("claim", Map.of(
                "type", "string",
                "description", "待校验的主张原文（不含引用标记）"
        ));
        props.put("ref", Map.of(
                "type", "integer",
                "description", "该主张引用的资料编号 N，即 [资料N] 中的数字"
        ));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("claim", "ref"));
        return schema;
    }

    @Override
    public String execute(Map<String, Object> args, AgentContext ctx) {
        Object claimObj = args.get("claim");
        if (claimObj == null || claimObj.toString().isBlank()) {
            return "参数错误：claim 不能为空";
        }
        String claim = claimObj.toString();

        Object refObj = args.get("ref");
        int ref;
        if (refObj instanceof Number) {
            ref = ((Number) refObj).intValue();
        } else {
            return "参数错误：ref 必须是资料编号数字";
        }

        List<Chunk> evidence = ctx.getEvidence();
        if (evidence.isEmpty()) {
            return "当前还没有检索到任何资料，请先调用 search_knowledge。";
        }
        if (ref < 1 || ref > evidence.size()) {
            return "参数错误：资料编号 " + ref + " 不存在，当前共有 " + evidence.size() + " 条资料。";
        }

        Chunk target = evidence.get(ref - 1);

        // 构造单条证据的校验输入：[资料1] 对应传入的唯一 chunk
        List<VerificationResult> results =
                verificationService.verify(claim + "[资料1]", List.of(target));
        if (results.isEmpty()) {
            return "校验未返回结果。";
        }

        VerificationResult vr = results.get(0);
        return "verdict: " + vr.getVerdict() + "\nreason: " + vr.getReason();
    }
}
