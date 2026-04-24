package com.emailassistant.prompts;

/*
 * ============================================================================
 * PromptTemplates — Prompt 模板管理
 * ============================================================================
 *
 * 功能描述:
 *   集中管理所有发送给 LLM 的 system prompt 和 user prompt 模板。
 *   将 Prompt 文本与业务逻辑分离，便于维护、版本控制和 A/B 测试。
 *
 *   映射关系: Python prompts.py → Java PromptTemplates + application.yml
 *
 * 编码建议:
 *   1. 方案 A — Java 常量类（简单项目推荐）:
 *        public class PromptTemplates {
 *            public static final String TRIAGE_SYSTEM = """
 *                You are an email triage assistant...
 *                """;
 *        }
 *      优点: 编译期检查，IDE 支持好
 *      缺点: 修改需要重新编译部署
 *
 *   2. 方案 B — 外部文件 + @PropertySource（推荐生产环境）:
 *        - 将 Prompt 存放在 src/main/resources/prompts/ 目录下
 *          如: triage-system.txt, email-writer-system.txt
 *        - 使用 Spring 的 @Value 或 ResourceLoader 加载
 *        - 可以通过配置中心（Nacos/Apollo）动态更新
 *      优点: 运维可热更新 Prompt，无需重新部署
 *
 *   3. 方案 C — 数据库存储（大规模系统）:
 *        - 支持 A/B 测试、版本管理、效果追踪
 *
 *   4. 建议分类整理的 Prompt:
 *      a) 分类 Prompt (Triage):
 *         - 定义 ignore/respond/notify 的判定标准
 *         - 包含分类推理的格式要求（用于结构化输出）
 *
 *      b) 邮件回复 Prompt (Email Writer):
 *         - 回复邮件的语气、格式、长度约束
 *         - 是否包含签名、称呼等格式要求
 *
 *      c) 通知 Prompt (Notification):
 *         - 生成推送通知的摘要文本
 *
 *      d) 偏好提取 Prompt (Memory):
 *         - 从对话中提取用户偏好的指令
 *
 *   5. Prompt 编写建议:
 *      - 明确输出格式要求（如 JSON Schema），便于结构化解析
 *      - 使用占位符 {email_content}/{sender_name} 等，运行时替换
 *      - 控制长度: system prompt 过长会挤占上下文窗口
 *      - 多语言场景: 准备中英文两个版本的 Prompt
 *      - 添加语气控制: "Be concise. Use a professional but friendly tone."
 *
 *   6. 使用 Java Text Blocks (""") 书写多行 Prompt，保持可读性。
 *      注意 Text Blocks 会保留换行和缩进，必要时调用 .stripIndent()。
 */
public class PromptTemplates {
    // TODO: TRIAGE_SYSTEM — 邮件分类的 system prompt

    // TODO: EMAIL_WRITER_SYSTEM — 邮件回复写作的 system prompt

    // TODO: PREFERENCE_EXTRACTION — 偏好提取的 prompt（记忆系统使用）

    // TODO: EVALUATION_PROMPT — 回复质量评估的 prompt（评估系统使用）

    /*
     * 附加建议:
     *   如果使用外部文件管理 Prompt，此类可以作为 PromptLoader:
     *     @Component
     *     public class PromptLoader {
     *         @Value("${prompts.triage-system}")
     *         private String triagePrompt;
     *     }
     *   并在 application.yml 中通过 spring.config.import 引入外部 Prompt 文件。
     */
}
