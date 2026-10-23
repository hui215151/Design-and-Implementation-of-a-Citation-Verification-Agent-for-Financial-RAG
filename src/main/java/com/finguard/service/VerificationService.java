package com.finguard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finguard.entity.Chunk;
import com.finguard.entity.VerificationResult;
import com.finguard.mapper.VerificationResultMapper;
import com.finguard.llm.LlmClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 引用校验服务：把 LLM 的回答拆成主张，逐条和证据对比
 */
@Service
@RequiredArgsConstructor
public class VerificationService {

    private final LlmClient llmClient;
    private final VerificationResultMapper verificationResultMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 校验主入口
     * @param answer LLM 的回答
     * @param chunks  检索到的证据 chunk 列表（按 [资料1] [资料2] 顺序）
     * @return 每条主张的校验结果列表
     */
    public List<Map<String, Object>> verify(String answer, List<Chunk> chunks) {
        // 1. 把回答拆成一条条主张（按句子拆，带上引用编号）
        List<Claim> claims = splitClaims(answer);

        // 2. 逐条校验
        List<Map<String, Object>> results = new ArrayList<>();
        for (Claim claim : claims) {
            // 找到对应的证据 chunk
            Chunk evidence = findEvidence(claim, chunks);
            if (evidence == null) {
                // 没有引用编号 → 直接标 UNSUPPORTED
                results.add(buildResult(claim, null, "UNSUPPORTED", "没有标注引用来源"));
                continue;
            }

            // 3. 先做数字硬比对（抓 LLM 改数字的问题）
            String numberCheck = checkNumbers(claim.text, evidence.getContent());
            if (numberCheck != null) {
                results.add(buildResult(claim, evidence, "CONTRADICTED", "数字不匹配: " + numberCheck));
                continue;
            }

            // 4. 再用 LLM 做 NLI 蕴含判断
            String verdict = nliCheck(claim.text, evidence.getContent());
            String reason = nliReason(claim.text, evidence.getContent());

            results.add(buildResult(claim, evidence, verdict, reason));
        }

        return results;
    }

    /**
     * 把回答拆成主张
     * 格式："xxx[资料1] yyy[资料2]" → 拆成两条
     */
    private List<Claim> splitClaims(String answer) {
        List<Claim> claims = new ArrayList<>();
        // 按 [资料N] 分割，每一段对应一条主张
        Pattern pattern = Pattern.compile("(\\[[^\\]]+\\])");
        String[] parts = pattern.split(answer);
        Matcher matcher = pattern.matcher(answer);

        List<String> refs = new ArrayList<>();
        while (matcher.find()) {
            refs.add(matcher.group(1));
        }

        for (int i = 0; i < parts.length; i++) {
            String text = parts[i].trim();
            if (text.isEmpty()) continue;
            String ref = i < refs.size() ? refs.get(i) : null;
            claims.add(new Claim(text, ref));
        }
        return claims;
    }

    /**
     * 根据 [资料N] 找到对应的证据 chunk
     */
    private Chunk findEvidence(Claim claim, List<Chunk> chunks) {
        if (claim.ref == null) return null;
        // [资料1] → 第 0 个 chunk
        Matcher m = Pattern.compile("资料(\\d+)").matcher(claim.ref);
        if (m.find()) {
            int idx = Integer.parseInt(m.group(1)) - 1;
            if (idx >= 0 && idx < chunks.size()) {
                return chunks.get(idx);
            }
        }
        return null;
    }

    /**
     * 数字硬比对：提取文本里的所有数字，看有没有对不上的
     */
    private String checkNumbers(String claimText, String evidenceText) {
        // 提取所有数字（带百分号、金额、日期）
        Pattern numPattern = Pattern.compile("\\d+\\.?\\d*%?|\\d+亿|\\d+万|\\d{4}年\\d{1,2}月?");
        Set<String> claimNums = new HashSet<>();
        Set<String> evidenceNums = new HashSet<>();

        Matcher m1 = numPattern.matcher(claimText);
        while (m1.find()) claimNums.add(m1.group());

        Matcher m2 = numPattern.matcher(evidenceText);
        while (m2.find()) evidenceNums.add(m2.group());

        // 主张里的数字，证据里找不到 → 可疑
        for (String num : claimNums) {
            if (!evidenceNums.contains(num)) {
                // 进一步模糊匹配：去掉百分号再比
                String cleanNum = num.replace("%", "");
                boolean found = false;
                for (String en : evidenceNums) {
                    if (en.contains(cleanNum) || cleanNum.contains(en.replace("%", ""))) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return "主张中的数字'" + num + "'在证据中未找到";
                }
            }
        }
        return null;
    }

    /**
     * NLI 蕴含判断：用 LLM 判断主张能不能从证据推出来
     */
    private String nliCheck(String claim, String evidence) {
        String prompt = "判断下面的主张能不能从证据中推导出来。\n" +
                "只回答一个词：SUPPORTED / PARTIAL / UNSUPPORTED / CONTRADICTED\n\n" +
                "证据：" + evidence + "\n\n" +
                "主张：" + claim + "\n\n" +
                "回答：";
        String result = llmClient.chat("你是事实核查助手", prompt).trim().toUpperCase();

        if (result.contains("SUPPORTED")) return "SUPPORTED";
        if (result.contains("PARTIAL")) return "PARTIAL";
        if (result.contains("CONTRADICTED")) return "CONTRADICTED";
        return "UNSUPPORTED";
    }

    /**
     * 让 LLM 给出校验理由
     */
    private String nliReason(String claim, String evidence) {
        String prompt = "用一句话说明为什么这个主张是对的/错的。\n" +
                "证据：" + evidence + "\n\n" +
                "主张：" + claim + "\n\n" +
                "理由：";
        return llmClient.chat("你是事实核查助手", prompt).trim();
    }

    private Map<String, Object> buildResult(Claim claim, Chunk evidence, String verdict, String reason) {
        Map<String, Object> map = new HashMap<>();
        map.put("claim", claim.text);
        map.put("ref", claim.ref);
        map.put("verdict", verdict);
        map.put("reason", reason);
        map.put("evidenceChunkId", evidence != null ? evidence.getId() : null);

        // 存库
        VerificationResult vr = new VerificationResult();
        vr.setClaimText(claim.text);
        vr.setVerdict(verdict);
        vr.setEvidenceChunkId(evidence != null ? evidence.getId() : null);
        vr.setEvidenceSnippet(evidence != null ? evidence.getContent().substring(0, Math.min(200, evidence.getContent().length())) : null);
        vr.setReason(reason);
        verificationResultMapper.insert(vr);

        return map;
    }

    /**
     * 内部类：一条主张
     */
    private static class Claim {
        String text;
        String ref; // [资料1]

        Claim(String text, String ref) {
            this.text = text;
            this.ref = ref;
        }
    }
}
