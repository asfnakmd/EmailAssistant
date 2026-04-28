package com.emailassistant.controller;

import com.emailassistant.email.EmailReceiverService;
import com.emailassistant.evaluation.EvaluationService;
import com.emailassistant.graph.EmailAssistantGraph;
import com.emailassistant.graph.HumanInLoopGraph;
import com.emailassistant.graph.MemoryEnabledGraph;
import com.emailassistant.state.AgentState;
import com.emailassistant.state.ChatMessage;
import com.emailassistant.state.EmailData;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * EmailAssistantController — REST API 和 WebSocket 控制器。
 *
 * <p>对外暴露邮件助手的 HTTP API 和 WebSocket 端点：
 * <ul>
 *   <li>REST API：接收邮件处理请求，返回处理结果</li>
 *   <li>WebSocket：人机交互的实时通信通道</li>
 * </ul>
 *
 * <h3>API 端点</h3>
 * <ul>
 *   <li>POST /api/email/process — 处理单封邮件（基础版）</li>
 *   <li>POST /api/email/process/hitl — 人机交互版处理</li>
 *   <li>POST /api/email/process/memory — 记忆增强版处理</li>
 *   <li>POST /api/email/approval/{sessionId} — 提交审批决策</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/email")
public class EmailAssistantController {

    private static final Logger log = LoggerFactory.getLogger(EmailAssistantController.class);

    private final EmailAssistantGraph emailGraph;
    private final HumanInLoopGraph hitlGraph;
    private final MemoryEnabledGraph memoryGraph;
    private final EvaluationService evaluationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final EmailReceiverService emailReceiverService;

    public EmailAssistantController(EmailAssistantGraph emailGraph,
                                    HumanInLoopGraph hitlGraph,
                                    MemoryEnabledGraph memoryGraph,
                                    EvaluationService evaluationService,
                                    SimpMessagingTemplate messagingTemplate,
                                    EmailReceiverService emailReceiverService) {
        this.emailGraph = emailGraph;
        this.hitlGraph = hitlGraph;
        this.memoryGraph = memoryGraph;
        this.evaluationService = evaluationService;
        this.messagingTemplate = messagingTemplate;
        this.emailReceiverService = emailReceiverService;
    }

    // ==================== REST API：基础版 ====================

    /**
     * 处理单封邮件（基础版工作流）。
     *
     * <p>接收邮件内容，通过 triage→llmCall→toolCall 工作流处理后返回结果。
     *
     * @param request 邮件请求体
     * @return 处理结果
     */
    @PostMapping("/process")
    public ResponseEntity<ProcessResponse> processEmail(@Valid @RequestBody EmailRequest request) {
        Instant start = Instant.now();
        log.info("收到邮件处理请求：subject={}", request.subject());

        // 构建初始状态
        AgentState initialState = new AgentState(
                request.emailContent(),
                null,
                List.of(ChatMessage.user(request.emailContent())),
                null
        );

        // 执行工作流
        AgentState result = emailGraph.buildEmailAssistant().run(initialState);

        // 提取处理结果
        String response = extractLastResponse(result.messages());
        List<String> toolCallsMade = extractToolCalls(result.messages());

        long durationMs = Duration.between(start, Instant.now()).toMillis();
        log.info("邮件处理完成，耗时={}ms，分类={}", durationMs, result.classificationDecision());

        // 异步评估（不阻塞响应）
        if (response != null && !response.isBlank()) {
            evaluateAsync(request.emailContent(), response, "basic");
        }

        return ResponseEntity.ok(new ProcessResponse(
                result.classificationDecision() != null
                        ? result.classificationDecision().name() : "UNKNOWN",
                response,
                toolCallsMade,
                durationMs
        ));
    }

    /**
     * 处理单封邮件（人机交互版）。
     */
    @PostMapping("/process/hitl")
    public ResponseEntity<ProcessResponse> processEmailHitl(@Valid @RequestBody EmailRequest request) {
        Instant start = Instant.now();
        log.info("收到 HITL 邮件处理请求：subject={}", request.subject());

        AgentState initialState = new AgentState(
                request.emailContent(),
                null,
                List.of(ChatMessage.user(request.emailContent())),
                null
        );

        AgentState result = hitlGraph.buildHumanInLoopGraph().run(initialState);

        String response = extractLastResponse(result.messages());
        List<String> toolCallsMade = extractToolCalls(result.messages());
        long durationMs = Duration.between(start, Instant.now()).toMillis();

        return ResponseEntity.ok(new ProcessResponse(
                result.classificationDecision() != null
                        ? result.classificationDecision().name() : "UNKNOWN",
                response,
                toolCallsMade,
                durationMs
        ));
    }

    /**
     * 处理单封邮件（记忆增强版）。
     */
    @PostMapping("/process/memory")
    public ResponseEntity<ProcessResponse> processEmailWithMemory(
            @Valid @RequestBody EmailRequest request) {
        Instant start = Instant.now();
        log.info("收到记忆增强邮件处理请求：subject={}", request.subject());

        AgentState initialState = new AgentState(
                request.emailContent(),
                null,
                List.of(ChatMessage.user(request.emailContent())),
                null
        );

        AgentState result = memoryGraph.buildMemoryEnabledGraph().run(initialState);

        String response = extractLastResponse(result.messages());
        List<String> toolCallsMade = extractToolCalls(result.messages());
        long durationMs = Duration.between(start, Instant.now()).toMillis();

        return ResponseEntity.ok(new ProcessResponse(
                result.classificationDecision() != null
                        ? result.classificationDecision().name() : "UNKNOWN",
                response,
                toolCallsMade,
                durationMs
        ));
    }

    // ==================== 审批 API ====================

    /**
     * 提交工具调用审批决策（人机交互流程）。
     *
     * @param sessionId 会话标识
     * @param approval  审批决策
     * @return 处理结果
     */
    @PostMapping("/approval/{sessionId}")
    public ResponseEntity<Map<String, String>> submitApproval(
            @PathVariable String sessionId,
            @Valid @RequestBody ToolApproval approval) {

        log.info("收到审批决策：sessionId={}, toolCallId={}, approved={}",
                sessionId, approval.toolCallId(), approval.approved());

        // 通过 WebSocket 通知前端审批结果已处理
        messagingTemplate.convertAndSend("/topic/agent/approval-result",
                Map.of(
                        "sessionId", sessionId,
                        "toolCallId", approval.toolCallId(),
                        "approved", approval.approved(),
                        "timestamp", System.currentTimeMillis()
                ));

        String status = approval.approved() ? "已批准" : "已拒绝";
        return ResponseEntity.ok(Map.of(
                "status", status,
                "sessionId", sessionId,
                "toolCallId", approval.toolCallId()
        ));
    }

    // ==================== WebSocket 消息处理 ====================

    /**
     * 接收前端通过 WebSocket 提交的审批决策。<｜end▁of▁thinking｜>

    // ==================== 邮件接收 API ====================

    /**
     * 获取收件箱中的未读邮件列表。
     */
    @GetMapping("/inbox")
    public ResponseEntity<List<InboxEmailResponse>> listInbox() {
        log.info("获取未读邮件列表");
        List<EmailData> emails = emailReceiverService.fetchUnreadEmails();
        List<InboxEmailResponse> response = emails.stream()
                .map(e -> new InboxEmailResponse(e.id(), e.from(), e.subject(),
                        e.body().length() > 200 ? e.body().substring(0, 200) + "..." : e.body(),
                        e.receivedAt(), e.priority()))
                .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * 获取收件箱中最近的指定数量邮件。
     */
    @GetMapping("/inbox/recent")
    public ResponseEntity<List<InboxEmailResponse>> listRecent(
            @RequestParam(defaultValue = "10") int count) {
        log.info("获取最近 {} 封邮件", count);
        List<EmailData> emails = emailReceiverService.fetchRecentEmails(count);
        List<InboxEmailResponse> response = emails.stream()
                .map(e -> new InboxEmailResponse(e.id(), e.from(), e.subject(),
                        e.body().length() > 200 ? e.body().substring(0, 200) + "..." : e.body(),
                        e.receivedAt(), e.priority()))
                .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * 获取未读邮件并通过基础工作流自动处理。
     */
    @PostMapping("/fetch-and-process")
    public ResponseEntity<BatchProcessResponse> fetchAndProcess() {
        Instant start = Instant.now();
        log.info("开始自动获取并处理未读邮件");

        List<EmailData> emails = emailReceiverService.fetchUnreadEmails();
        List<EmailProcessResult> results = new ArrayList<>();

        for (EmailData email : emails) {
            try {
                AgentState initialState = email.toAgentState();
                AgentState result = emailGraph.buildEmailAssistant().run(initialState);
                String response = extractLastResponse(result.messages());
                results.add(new EmailProcessResult(
                        email.id(), email.from(), email.subject(),
                        result.classificationDecision() != null
                                ? result.classificationDecision().name() : "UNKNOWN",
                        response, Duration.between(start, Instant.now()).toMillis()));
            } catch (Exception e) {
                log.warn("处理邮件 {} 失败: {}", email.id(), e.getMessage());
                results.add(new EmailProcessResult(
                        email.id(), email.from(), email.subject(),
                        "ERROR", "处理失败: " + e.getMessage(),
                        Duration.between(start, Instant.now()).toMillis()));
            }
        }

        long totalMs = Duration.between(start, Instant.now()).toMillis();
        log.info("批量处理完成: {}/{} 封", results.size(), emails.size());
        return ResponseEntity.ok(new BatchProcessResponse(results.size(), totalMs, results));
    }

    /**
     * 获取未读邮件并通过记忆增强版工作流自动处理。
     */
    @PostMapping("/fetch-and-process/memory")
    public ResponseEntity<BatchProcessResponse> fetchAndProcessWithMemory() {
        Instant start = Instant.now();
        log.info("开始自动获取并处理未读邮件（记忆增强版）");

        List<EmailData> emails = emailReceiverService.fetchUnreadEmails();
        List<EmailProcessResult> results = new ArrayList<>();

        for (EmailData email : emails) {
            try {
                AgentState initialState = email.toAgentState();
                AgentState result = memoryGraph.buildMemoryEnabledGraph().run(initialState);
                String response = extractLastResponse(result.messages());
                results.add(new EmailProcessResult(
                        email.id(), email.from(), email.subject(),
                        result.classificationDecision() != null
                                ? result.classificationDecision().name() : "UNKNOWN",
                        response, Duration.between(start, Instant.now()).toMillis()));
            } catch (Exception e) {
                log.warn("处理邮件 {} 失败: {}", email.id(), e.getMessage());
                results.add(new EmailProcessResult(
                        email.id(), email.from(), email.subject(),
                        "ERROR", "处理失败: " + e.getMessage(),
                        Duration.between(start, Instant.now()).toMillis()));
            }
        }

        long totalMs = Duration.between(start, Instant.now()).toMillis();
        log.info("批量处理完成（记忆版）: {}/{} 封", results.size(), emails.size());
        return ResponseEntity.ok(new BatchProcessResponse(results.size(), totalMs, results));
    }

    // ==================== WebSocket 消息处理 ====================

    /**
     * 接收前端通过 WebSocket 提交的审批决策。
     *
     * <p>客户端发送到 /app/agent/approval 的消息会路由到此方法。
     *
     * @param approval 审批决策
     */
    @MessageMapping("/agent/approval")
    public void handleToolApproval(ToolApproval approval) {
        log.info("WebSocket 收到审批：toolCallId={}, approved={}",
                approval.toolCallId(), approval.approved());

        // 广播审批结果
        messagingTemplate.convertAndSend("/topic/agent/approval-result",
                Map.of(
                        "toolCallId", approval.toolCallId(),
                        "approved", approval.approved(),
                        "timestamp", System.currentTimeMillis()
                ));
    }

    // ==================== 内部方法 ====================

    /**
     * 从消息列表中提取最后一条非空的 assistant 回复。
     */
    private String extractLastResponse(List<ChatMessage> messages) {
        if (messages == null) return null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if ("assistant".equals(msg.role()) && msg.content() != null && !msg.content().isBlank()) {
                return msg.content();
            }
        }
        return null;
    }

    /**
     * 从消息列表中提取所有调用过的工具名。
     */
    private List<String> extractToolCalls(List<ChatMessage> messages) {
        if (messages == null) return List.of();
        return messages.stream()
                .flatMap(m -> m.toolCalls().stream())
                .map(tc -> tc.name() + "(" + tc.args() + ")")
                .toList();
    }

    /**
     * 异步评估处理结果（不阻塞 API 响应）。
     */
    private void evaluateAsync(String email, String response, String graphType) {
        try {
            evaluationService.evaluateResponse(email, response);
        } catch (Exception e) {
            log.warn("异步评估异常（非关键错误）", e);
        }
    }

    // ==================== DTO 定义 ====================

    /**
     * 邮件处理请求体。
     */
    public record EmailRequest(
            @NotBlank(message = "邮件内容不能为空") String emailContent,
            String sender,
            String subject,
            String userId
    ) {}

    /**
     * 邮件处理响应体。
     */
    public record ProcessResponse(
            String classification,
            String response,
            List<String> toolCallsMade,
            long processingTimeMs
    ) {}

    /**
     * 工具调用审批决策请求体。
     */
    public record ToolApproval(
            @NotBlank String toolCallId,
            boolean approved,
            String comment
    ) {}

    /**
     * 收件箱邮件列表响应体。
     */
    public record InboxEmailResponse(
            String id,
            String from,
            String subject,
            String bodyPreview,
            LocalDateTime receivedAt,
            EmailData.Priority priority
    ) {}

    /**
     * 单封邮件的处理结果。
     */
    public record EmailProcessResult(
            String emailId,
            String from,
            String subject,
            String classification,
            String response,
            long processingTimeMs
    ) {}

    /**
     * 批量处理响应体。
     */
    public record BatchProcessResponse(
            int processedCount,
            long totalTimeMs,
            List<EmailProcessResult> results
    ) {}
}
