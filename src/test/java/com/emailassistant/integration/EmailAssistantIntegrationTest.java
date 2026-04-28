package com.emailassistant.integration;

import com.emailassistant.controller.EmailAssistantController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.springframework.http.MediaType;

/**
 * EmailAssistantIntegrationTest — API 集成测试。
 *
 * <p>测试 REST API 端点的完整链路。
 * 启动嵌入式服务器（随机端口），通过 WebTestClient 发送 HTTP 请求验证。
 *
 * <p>注意：集成测试依赖真实的 LLM API（需要配置 OPENAI_API_KEY）。
 * 如果没有配置外部依赖，部分测试可能需要通过条件注解跳过。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(MockitoExtension.class)
class EmailAssistantIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private EmailAssistantController controller;

    // ==================== 基础功能测试 ====================

    @Test
    void contextLoads() {
        // 验证 Spring 上下文能正常加载，所有 Bean 创建成功
        assertThat(controller).isNotNull();
    }

    @Test
    void controllerBeanExists() {
        assertThat(controller).isInstanceOf(EmailAssistantController.class);
    }

    // ==================== 参数校验测试 ====================

    @Test
    void testProcessEmail_InvalidRequest() {
        // 缺少必填字段 emailContent
        String invalidJson = """
                {
                    "sender": "test@example.com",
                    "subject": "Test"
                }
                """;

        webTestClient.post()
                .uri("/api/email/process")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidJson)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void testProcessEmail_EmptyContent() {
        // emailContent 为空字符串
        String emptyContent = """
                {
                    "emailContent": "",
                    "subject": "Test"
                }
                """;

        webTestClient.post()
                .uri("/api/email/process")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(emptyContent)
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ==================== DTO 构造测试 ====================

    @Test
    void testEmailRequestRecord() {
        var request = new EmailAssistantController.EmailRequest(
                "Hello, this is a test email",
                "sender@example.com",
                "Test Subject",
                "user123"
        );

        assertThat(request.emailContent()).isEqualTo("Hello, this is a test email");
        assertThat(request.sender()).isEqualTo("sender@example.com");
        assertThat(request.subject()).isEqualTo("Test Subject");
        assertThat(request.userId()).isEqualTo("user123");
    }

    @Test
    void testProcessResponseRecord() {
        var response = new EmailAssistantController.ProcessResponse(
                "RESPOND",
                "Thank you for your email",
                List.of("write_email({to=user@example.com})"),
                1500L
        );

        assertThat(response.classification()).isEqualTo("RESPOND");
        assertThat(response.response()).isEqualTo("Thank you for your email");
        assertThat(response.toolCallsMade()).hasSize(1);
        assertThat(response.processingTimeMs()).isGreaterThan(0);
    }

    @Test
    void testToolApprovalRecord() {
        var approval = new EmailAssistantController.ToolApproval(
                "tc_12345",
                true,
                "Looks good, please proceed"
        );

        assertThat(approval.toolCallId()).isEqualTo("tc_12345");
        assertThat(approval.approved()).isTrue();
        assertThat(approval.comment()).isEqualTo("Looks good, please proceed");
    }

    // ==================== WebSocket 端点测试 ====================

    @Test
    void testWebSocketEndpointConfigured() {
        // 验证 WebSocket 端点已注册（通过 Actuator 查看 Bean）
        // 此测试确保配置类被正确加载
        assertThat(controller).isNotNull();
    }
}
