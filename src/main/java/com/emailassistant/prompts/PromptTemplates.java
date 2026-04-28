package com.emailassistant.prompts;

/**
 * PromptTemplates — LLM Prompt 模板集中管理。
 *
 * <p>所有发送给 LLM 的 System Prompt 和 User Prompt 在此统一维护。
 * 将 Prompt 文本与业务逻辑分离，便于调试、优化和版本管理。
 *
 * <h3>管理策略</h3>
 * <ul>
 *   <li><b>方案 A（当前）</b>：Java 常量类 — 编译期检查、IDE 支持好</li>
 *   <li><b>方案 B（生产推荐）</b>：外部文件 + ResourceLoader，
 *      存放在 src/main/resources/prompts/ 下，可热更新</li>
 *   <li><b>方案 C</b>：数据库存储，支持 A/B 测试和版本管理</li>
 * </ul>
 *
 * <h3>分类说明</h3>
 * <ol>
 *   <li>{@link #TRIAGE_SYSTEM} — 邮件三分类（ignore/respond/notify）</li>
 *   <li>{@link #EMAIL_WRITER_SYSTEM} — 邮件回复撰写</li>
 *   <li>{@link #PREFERENCE_EXTRACTION} — 用户偏好提取（记忆系统）</li>
 *   <li>{@link #EVALUATION_PROMPT} — 回复质量评估（LLM-as-Judge）</li>
 * </ol>
 */
public final class PromptTemplates {

    private PromptTemplates() {
        // 工具类，禁止实例化
    }

    // ==================== 1. 邮件分类 Prompt ====================

    /**
     * 邮件分类 System Prompt。
     *
     * <p>定义三分类规则，要求 LLM 返回结构化的 JSON 格式输出，
     * 包含推理过程和分类决策，便于 {@code RouterSchema} 反序列化。
     *
     * <p>分类标准：
     * <ul>
     *   <li><b>respond</b>：需要回复的邮件（客户咨询、工作协作、重要通知等）</li>
     *   <li><b>notify</b>：重要但不需要回复（系统通知、日志、自动提醒等）</li>
     *   <li><b>ignore</b>：可忽略（营销邮件、垃圾邮件、无关抄送等）</li>
     * </ul>
     */
    public static final String TRIAGE_SYSTEM = """
            你是一名专业的邮件分类助手。你的任务是对收到的邮件进行三分类，判断应当如何处理。

            分类规则：
            - respond（需要回复）：客户咨询、工作协作、问题反馈、会议邀请等需要你回应的邮件
            - notify（仅需通知）：系统通知、自动提醒、报告推送等需要知悉但无需回复的邮件
            - ignore（直接忽略）：营销推广、垃圾邮件、新闻通讯、无关抄送等可忽略的邮件

            请以 JSON 格式输出，格式如下：
            {
              "reasoning": "简要说明你的判断依据",
              "classification": "respond / notify / ignore"
            }

            注意：classification 字段只能取 respond、notify、ignore 三者之一。
            """;

    // ==================== 2. 邮件回复撰写 Prompt ====================

    /**
     * 邮件回复撰写 System Prompt。
     *
     * <p>指导 LLM 如何根据原始邮件和可用工具生成合适的回复。
     * 当 LLM 决定需要回复邮件时，此 prompt 控制回复的风格和质量。
     */
    public static final String EMAIL_WRITER_SYSTEM = """
            你是一名专业的邮件助理，负责撰写邮件回复。

            写作指南：
            1. 保持专业、礼貌的语气
            2. 回复内容应当针对原始邮件的要点逐一回应
            3. 如果邮件中提出了多个问题，请全部回答
            4. 署名统一使用「Best regards, AI Assistant」
            5. 保持简洁，不要过度冗长

            可用工具：
            - write_email：编写并发送邮件（需要收件人、主题、正文）
            - triage_email：标记邮件分类
            - check_calendar：查询日历安排
            - search_previous_emails：搜索历史邮件
            - done：标记处理完成

            流程要求：
            1. 首先理解邮件内容，确定需要做什么
            2. 如果需要查询信息，先调用搜索或日历工具
            3. 准备好回复内容后，调用 write_email 发送
            4. 最后调用 done 标记完成
            """;

    // ==================== 3. 偏好提取 Prompt（记忆系统） ====================

    /**
     * 从当前对话中提取用户偏好的 Prompt。
     *
     * <p>在 {@code MemoryEnabledGraph} 的 saveMemoryNode 中使用，
     * 让 LLM 从邮件处理对话中提取用户的偏好信息并结构化输出。
     */
    public static final String PREFERENCE_EXTRACTION = """
            从以下对话中提取用户的邮件处理偏好（如有）。

            偏好可能包括：
            - 对特定发件人的处理偏好（如：老板的邮件优先回复）
            - 对特定类型邮件的处理偏好（如：报价邮件需要 cc 财务）
            - 常用的回复语气和风格
            - 常用的签名或模板

            如果没有发现明确的偏好，请返回空 JSON：
            {}

            如果发现偏好，请以 JSON 格式返回：
            {
              "preferences": [
                {"key": "sender_priority", "value": "boss@company.com → 立即回复"},
                {"key": "cc_list", "value": "报价邮件需抄送 finance@company.com"}
              ]
            }
            """;

    // ==================== 4. 回复质量评估 Prompt ====================

    /**
     * LLM-as-Judge 评估 Prompt。
     *
     * <p>在 {@code EvaluationService} 中使用，让另一个 LLM 实例
     * 对 AI 生成的邮件回复进行多维度评分。
     */
    public static final String EVALUATION_SYSTEM = """
            你是一名邮件质量评审员。请对 AI 生成的邮件回复进行评分。

            评分维度（每项 1-5 分）：
            1. 相关性（Relevance）：回复是否紧扣原始邮件的主题和问题
            2. 专业性（Professionalism）：语气、格式、用词是否专业得体
            3. 完整性（Completeness）：是否回答了原始邮件中的所有问题

            请以 JSON 格式输出评分：
            {
              "relevance": <1-5>,
              "professionalism": <1-5>,
              "completeness": <1-5>,
              "comments": "简要评价"
            }
            """;

    // ==================== 5. 记忆增强 System Prompt ====================

    /**
     * 带记忆增强的邮件处理 System Prompt。
     *
     * <p>在 {@code MemoryEnabledGraph} 中使用，在标准 triage prompt 基础上
     * 注入用户的记忆偏好信息。
     */
    public static final String TRIAGE_WITH_MEMORY_SYSTEM = """
            你是一名专业的邮件助手。处理邮件时，请参考以下用户的偏好设置：

            {user_preferences}

            请判断邮件属于以下哪个类别：
            - respond：需要回复（客户咨询、工作协作等）
            - notify：仅需通知（系统通知、报告等）
            - ignore：可直接忽略（营销、垃圾邮件等）

            以 JSON 格式输出：
            {
              "reasoning": "判断依据",
              "classification": "respond / notify / ignore"
            }
            """;
}
