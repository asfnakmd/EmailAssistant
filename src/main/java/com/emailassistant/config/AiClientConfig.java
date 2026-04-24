package com.emailassistant.config;

/*
 * ============================================================================
 * AiClientConfig — AI 客户端配置
 * ============================================================================
 *
 * 功能描述:
 *   配置 LLM 客户端的 Bean，支持多种 LLM 提供商（OpenAI、通义千问等）。
 *   通过 Spring 的条件注解实现按需加载不同提供商的客户端。
 *
 * 编码建议:
 *   1. 使用 @Configuration 注解使其成为配置类。
 *
 *   2. 配置属性（在 application.yml 中定义）:
 *      spring.ai.provider: openai | alibaba | custom
 *      spring.ai.openai.api-key: ${OPENAI_API_KEY}
 *      spring.ai.openai.chat.options.model: gpt-4
 *      spring.ai.openai.chat.options.temperature: 0.0
 *
 *   3. 使用条件 Bean 注册:
 *      @Bean
 *      @ConditionalOnProperty(name = "spring.ai.provider", havingValue = "openai")
 *      public AiClient openAiClient(OpenAiApi openAiApi) { ... }
 *
 *      @Bean
 *      @ConditionalOnProperty(name = "spring.ai.provider", havingValue = "alibaba")
 *      public AiClient alibabaAiClient(QwenApi qwenApi) { ... }
 *
 *   4. 或者使用更简单的方案 — 依赖 Spring AI 自动配置:
 *      Spring AI 会根据 classpath 中存在的 starter 自动创建对应的
 *      ChatClient / AiClient Bean。本配置类仅在需要自定义行为时使用。
 *
 *   5. 常用自定义配置项:
 *      a) 连接超时和读取超时:
 *          某些邮件处理可能耗时较长，需要适当增大超时值
 *      b) 重试策略:
 *          配置 Spring Retry 的 RetryTemplate，处理 API 临时不可用
 *      c) 速率限制:
 *          使用 Resilience4j RateLimiter 防止超出 API 配额
 *      d) 响应缓存:
 *          对于相同的 triage prompt 结果，可用 Spring Cache 缓存
 *          减少 API 调用费用
 *
 *   6. 多模型切换:
 *      - 分类任务: 使用快速便宜的模型（如 gpt-3.5-turbo / qwen-turbo）
 *      - 写作任务: 使用高质量模型（如 gpt-4 / qwen-max）
 *      - 可以创建多个 AiClient Bean，用 @Qualifier 区分
 *          @Bean @Qualifier("triage") AiClient triageClient() { ... }
 *          @Bean @Qualifier("writer") AiClient writerClient() { ... }
 *
 *   7. 安全:
 *      - API Key 不要硬编码，通过环境变量或 Secret Manager 注入
 *      - 生产环境使用 Vault 或云厂商的密钥管理服务
 */
public class AiClientConfig {
    // TODO: @Bean openAiClient — OpenAI 客户端配置

    // TODO: @Bean alibabaAiClient — 通义千问客户端配置（可选）

    // TODO: @Bean @Qualifier("triage") triageClient — 分类专用客户端（可选）

    // TODO: @Bean @Qualifier("writer") writerClient — 写作专用客户端（可选）

    // TODO: @Bean retryTemplate — API 调用重试策略（可选）
}
