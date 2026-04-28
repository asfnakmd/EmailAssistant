package com.emailassistant.email;

import com.emailassistant.graph.EmailAssistantGraph;
import com.emailassistant.state.AgentState;
import com.emailassistant.state.EmailData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class ScheduledEmailService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledEmailService.class);

    private final EmailReceiverService emailReceiverService;
    private final EmailAssistantGraph emailGraph;

    private final boolean autoProcessEnabled;
    private final int maxEmailsPerCycle;

    public ScheduledEmailService(EmailReceiverService emailReceiverService,
                                 EmailAssistantGraph emailGraph,
                                 @Value("${app.auto-process.enabled:false}") boolean autoProcessEnabled,
                                 @Value("${app.auto-process.max-emails:10}") int maxEmailsPerCycle) {
        this.emailReceiverService = emailReceiverService;
        this.emailGraph = emailGraph;
        this.autoProcessEnabled = autoProcessEnabled;
        this.maxEmailsPerCycle = maxEmailsPerCycle;
    }

    @Scheduled(fixedDelayString = "${app.auto-process.interval-ms:300000}")
    public void autoFetchAndProcess() {
        if (!autoProcessEnabled) {
            return;
        }

        Instant start = Instant.now();
        log.info("[定时任务] 开始自动获取未读邮件...");

        try {
            List<EmailData> unread = emailReceiverService.fetchUnreadEmails();
            if (unread.isEmpty()) {
                return;
            }

            int limit = Math.min(unread.size(), maxEmailsPerCycle);
            int success = 0;

            for (int i = 0; i < limit; i++) {
                EmailData email = unread.get(i);
                try {
                    AgentState result = emailGraph.buildEmailAssistant().run(email.toAgentState());
                    log.info("[定时任务] 已处理邮件: from={}, subject={}, 分类={}",
                            email.from(), email.subject(),
                            result.classificationDecision());
                    success++;
                } catch (Exception e) {
                    log.warn("[定时任务] 邮件处理失败: {} - {}", email.id(), e.getMessage());
                }
            }

            long elapsed = Duration.between(start, Instant.now()).toMillis();
            log.info("[定时任务] 本轮完成: {}/{} 封处理成功, 耗时={}ms", success, limit, elapsed);
        } catch (Exception e) {
            log.warn("[定时任务] 自动获取邮件异常: {}", e.getMessage());
        }
    }
}
