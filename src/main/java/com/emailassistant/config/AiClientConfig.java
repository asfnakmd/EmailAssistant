package com.emailassistant.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AiClientConfig — AI 客户端和 OpenTelemetry 配置。
 *
 * <p>配置 LLM 客户端的 Bean，以及 OpenTelemetry 的 Tracer 实例。
 *
 * <h3>配置说明</h3>
 * <ul>
 *   <li>Spring AI 的 ChatClient 由 {@code spring-ai-openai-spring-boot-starter}
 *       自动配置，本类提供 ChatClient.Builder → ChatClient 的快捷构建</li>
 *   <li>通过 application.yml 中的 {@code spring.ai.openai} 配置项控制模型、
 *      温度、API Key 等参数</li>
 *   <li>OpenTelemetry 的 Tracer 用于工作流执行追踪</li>
 * </ul>
 */
@Configuration
public class AiClientConfig {

    /**
     * 构建全局唯一的 ChatClient 实例。
     *
     * <p>所有图工作流和评估服务共享同一个 ChatClient Bean，
     * 避免重复创建带来的资源浪费。
     *
     * @param builder Spring AI 自动注入的 ChatClient.Builder
     * @return 配置完成的 ChatClient
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    /**
     * 创建 OpenTelemetry Tracer，用于工作流执行链路追踪。
     *
     * <p>配置项：
     * <ul>
     *   <li>通过 application.yml 中的 {@code management.tracing} 控制采样率</li>
     *   <li>追踪数据默认导出到 OTLP 端点（可配置）</li>
     * </ul>
     *
     * @param openTelemetry Spring 自动配置的 OpenTelemetry 实例
     * @return Tracer 实例
     */
    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("com.emailassistant", "1.0.0");
    }
}
