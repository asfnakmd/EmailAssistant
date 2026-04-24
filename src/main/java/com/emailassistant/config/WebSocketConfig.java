package com.emailassistant.config;

/*
 * ============================================================================
 * WebSocketConfig — WebSocket 和 STOMP 配置
 * ============================================================================
 *
 * 功能描述:
 *   配置 STOMP over WebSocket 协议，为人机交互功能提供实时双向通信通道。
 *   前端通过 WebSocket 连接可以实时接收审批请求、进度更新等消息。
 *
 * 编码建议:
 *   1. 使用 @Configuration + @EnableWebSocketMessageBroker 注解。
 *   2. 实现 WebSocketMessageBrokerConfigurer 接口。
 *
 *   3. configureMessageBroker 方法:
 *      - config.enableSimpleBroker("/topic")
 *        启用内置的简单消息代理，目标前缀为 /topic
 *        这是服务端推送到客户端的通道
 *      - config.setApplicationDestinationPrefixes("/app")
 *        客户端发送消息到服务端时使用的前缀
 *        例如: 客户端发送到 /app/agent/approval → 对应
 *        @MessageMapping("/agent/approval") 方法
 *
 *   4. registerStompEndpoints 方法:
 *      - registry.addEndpoint("/ws-agent")
 *        定义 WebSocket 连接端点，客户端连接:
 *        new SockJS("http://localhost:8080/ws-agent")
 *      - .setAllowedOriginPatterns("*")
 *        跨域配置，开发阶段可用 "*"，生产环境必须限制具体域名
 *      - .withSockJS()
 *        启用 SockJS 降级支持（WebSocket 不可用时回退到长轮询）
 *
 *   5. 高级配置（可选）:
 *      - 配置消息大小限制:
 *          config.setMaxTextMessageSize(65536);
 *          config.setMaxBinaryMessageSize(65536);
 *      - 配置外部消息代理（替代内置 SimpleBroker）:
 *          config.enableStompBrokerRelay("/topic")
 *              .setRelayHost("localhost")
 *              .setRelayPort(61613);
 *        适用于多实例集群部署（使用 RabbitMQ/ActiveMQ 作为消息代理）
 *      - 添加 ChannelInterceptor 做权限校验:
 *          config.setClientInboundChannel(...)
 *
 *   6. 与 Spring Security 集成（如果需要）:
 *      - 添加 AbstractSecurityWebSocketMessageBrokerConfigurer
 *      - 配置 CSRF 保护（对 WebSocket 通常是禁用的）
 *      - JWT token 可通过连接参数或首次 STOMP CONNECT 帧传递
 *
 *   7. 生产环境注意事项:
 *      - SockJS 的 XHR 轮询可能会绕过某些网关配置
 *      - 配置 Nginx 反向代理时确保支持 WebSocket Upgrade
 *      - 设置合理的超时和心跳参数
 */
public class WebSocketConfig {
    // TODO: 实现 WebSocketMessageBrokerConfigurer 接口

    // TODO: configureMessageBroker(MessageBrokerRegistry) — 配置消息代理

    // TODO: registerStompEndpoints(StompEndpointRegistry) — 注册 WebSocket 端点
}
