package com.emailassistant.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocketConfig — STOMP over WebSocket 消息通信配置。
 *
 * <p>为人机交互（Human-in-the-Loop）功能提供实时双向通信通道。
 * 前端通过 WebSocket 连接实时接收审批请求、进度更新等消息。
 *
 * <h3>消息通道设计</h3>
 * <ul>
 *   <li><b>/topic/agent/progress</b> — 处理进度推送（服务端 → 客户端）</li>
 *   <li><b>/topic/agent/awaiting-approval</b> — 待审批工具调用推送</li>
 *   <li><b>/topic/agent/error</b> — 错误通知推送</li>
 *   <li><b>/app/agent/approval</b> — 客户端提交审批决策（客户端 → 服务端）</li>
 * </ul>
 *
 * <h3>通信流程</h3>
 * <ol>
 *   <li>前端通过 SockJS 连接到 {@code /ws-agent} 端点</li>
 *   <li>订阅 {@code /topic/agent/awaiting-approval} 主题</li>
 *   <li>Agent 在 humanReviewNode 中通过 SimpMessagingTemplate 推送待审批项</li>
 *   <li>前端展示待审批项，用户点击批准/拒绝</li>
 *   <li>前端发送审批结果到 {@code /app/agent/approval}</li>
 *   <li>Controller 处理并恢复工作流执行</li>
 * </ol>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * 配置消息代理。
     *
     * <ul>
     *   <li>启用 SimpleBroker（内置代理），目标前缀 /topic —
     *       服务端通过此代理推送消息到客户端</li>
     *   <li>应用目标前缀 /app — 客户端发送消息到此前缀的消息，
     *       会路由到 {@code @MessageMapping} 方法</li>
     * </ul>
     *
     * @param config 消息代理注册器
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 服务端 → 客户端：启用 /topic 前缀的简单消息代理
        config.enableSimpleBroker("/topic");
        // 客户端 → 服务端：消息目标前缀为 /app
        config.setApplicationDestinationPrefixes("/app");
    }

    /**
     * 注册 STOMP WebSocket 端点。
     *
     * <p>客户端通过 SockJS 连接到此端点建立 WebSocket 通信。
     *
     * @param registry STOMP 端点注册器
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-agent")
                // 开发阶段允许所有来源，生产环境必须限制
                .setAllowedOriginPatterns("*")
                // 启用 SockJS 降级支持（WebSocket 不可用时回退到长轮询）
                .withSockJS();
    }
}
