package com.emailassistant.evaluation;

/**
 * EvaluationResult — LLM-as-Judge 评估结果的数据结构。
 *
 * <p>包含三个评分维度的分数、安全审查结果和 LLM 评语。
 * 通过 {@link EvaluationService#evaluateResponse} 方法获取。
 */
public record EvaluationResult(
        /** 相关性评分（1-5） */
        int relevance,
        /** 专业性评分（1-5） */
        int professionalism,
        /** 完整性评分（1-5） */
        int completeness,
        /** 安全审查是否通过 */
        boolean safetyPassed,
        /** LLM 的详细评语 */
        String comments,
        /** 三项评分的均值 */
        double averageScore
) {

    /**
     * 便捷构造器：自动计算平均分。
     */
    public EvaluationResult(int relevance, int professionalism, int completeness,
                            boolean safetyPassed, String comments) {
        this(relevance, professionalism, completeness, safetyPassed, comments,
                (relevance + professionalism + completeness) / 3.0);
    }

    /**
     * 创建评估失败的结果对象。
     *
     * @param errorMessage 错误描述
     * @return 表示评估失败的 EvaluationResult
     */
    public static EvaluationResult failed(String errorMessage) {
        return new EvaluationResult(0, 0, 0, false, "评估失败: " + errorMessage, 0.0);
    }
}
