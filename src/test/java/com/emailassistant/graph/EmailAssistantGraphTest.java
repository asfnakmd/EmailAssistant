package com.emailassistant.graph;

import com.emailassistant.graph.core.Graph;
import com.emailassistant.state.AgentState;
import com.emailassistant.state.ChatMessage;
import com.emailassistant.state.ToolCall;
import com.emailassistant.tools.Tool;
import com.emailassistant.tools.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * EmailAssistantGraphTest — 图工作流单元测试。
 *
 * <p>使用 Mockito 模拟 ChatClient 和 ToolRegistry，对 EmailAssistantGraph 的
 * 各个节点和路由逻辑进行隔离测试。
 */
@ExtendWith(MockitoExtension.class)
class EmailAssistantGraphTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec responseSpec;

    @Mock
    private ToolRegistry toolRegistry;

    private EmailAssistantGraph graph;

    @BeforeEach
    void setUp() {
        graph = new EmailAssistantGraph(chatClient, toolRegistry);
    }

    // ==================== 路由逻辑测试 ====================

    @Test
    void testRouteFromTriage_Respond() {
        AgentState state = new AgentState("test email", AgentState.Classification.RESPOND,
                List.of(), null);

        // 通过反射调用私有方法
        String nextNode = graph.routeFromTriage(state);

        assertThat(nextNode).isEqualTo("llmCall");
    }

    @Test
    void testRouteFromTriage_Ignore() {
        AgentState state = new AgentState("test email", AgentState.Classification.IGNORE,
                List.of(), null);

        String nextNode = graph.routeFromTriage(state);

        assertThat(nextNode).isEqualTo(Graph.END);
    }

    @Test
    void testRouteFromTriage_Notify() {
        AgentState state = new AgentState("test email", AgentState.Classification.NOTIFY,
                List.of(), null);

        String nextNode = graph.routeFromTriage(state);

        assertThat(nextNode).isEqualTo(Graph.END);
    }

    // ==================== shouldContinue 测试 ====================

    @Test
    void testShouldContinue_HasToolCalls() {
        ChatMessage msg = ChatMessage.assistant("Let me check that",
                List.of(new ToolCall("tc1", "check_calendar", Map.of("date", "2024-01-15"))));
        AgentState state = new AgentState("test", null, List.of(msg), null);

        String nextNode = graph.shouldContinue(state);

        assertThat(nextNode).isEqualTo("toolCall");
    }

    @Test
    void testShouldContinue_DoneToolCalled() {
        ChatMessage msg = ChatMessage.assistant("Done",
                List.of(new ToolCall("tc1", "done", Map.of("done", true))));
        AgentState state = new AgentState("test", null, List.of(msg), null);

        String nextNode = graph.shouldContinue(state);

        assertThat(nextNode).isEqualTo(Graph.END);
    }

    @Test
    void testShouldContinue_NoToolCalls() {
        ChatMessage msg = ChatMessage.assistant("No tools needed");
        AgentState state = new AgentState("test", null, List.of(msg), null);

        String nextNode = graph.shouldContinue(state);

        assertThat(nextNode).isEqualTo(Graph.END);
    }

    @Test
    void testShouldContinue_EmptyMessages() {
        AgentState state = new AgentState("test", null, List.of(), null);

        String nextNode = graph.shouldContinue(state);

        assertThat(nextNode).isEqualTo(Graph.END);
    }

    // ==================== toolCallNode 测试 ====================

    @Test
    void testToolCallNode_ExecutesTool() {
        Tool mockTool = new Tool() {
            @Override public String getName() { return "test_tool"; }
            @Override public String getDescription() { return "Test tool"; }
            @Override public String execute(Map<String, Object> args) {
                return "Executed: " + args.get("param");
            }
        };

        when(toolRegistry.getTool("test_tool")).thenReturn(mockTool);

        List<ToolCall> toolCalls = List.of(
                new ToolCall("tc1", "test_tool", Map.of("param", "value1")));
        ChatMessage msg = ChatMessage.assistant("Using tool", toolCalls);
        AgentState state = new AgentState("test", null, List.of(msg), null);

        AgentState result = graph.toolCallNode(state);

        assertThat(result.messages()).hasSize(2);
        assertThat(result.messages().get(1).role()).isEqualTo("tool");
        assertThat(result.messages().get(1).content()).contains("Executed: value1");
    }

    @Test
    void testToolCallNode_NoToolCalls() {
        ChatMessage msg = ChatMessage.assistant("No tools");
        AgentState state = new AgentState("test", null, List.of(msg), null);

        AgentState result = graph.toolCallNode(state);

        // 不应追加新消息
        assertThat(result.messages()).hasSize(1);
    }

    @Test
    void testToolCallNode_EmptyMessages() {
        AgentState state = new AgentState("test", null, List.of(), null);

        AgentState result = graph.toolCallNode(state);

        assertThat(result.messages()).isEmpty();
    }

    @Test
    void testToolCallNode_HandlesException() {
        when(toolRegistry.getTool("failing_tool"))
                .thenThrow(new RuntimeException("Tool not found"));

        List<ToolCall> toolCalls = List.of(
                new ToolCall("tc1", "failing_tool", Map.of()));
        ChatMessage msg = ChatMessage.assistant("Using tool", toolCalls);
        AgentState state = new AgentState("test", null, List.of(msg), null);

        AgentState result = graph.toolCallNode(state);

        assertThat(result.messages()).hasSize(2);
        assertThat(result.messages().get(1).content()).contains("错误");
    }

    // ==================== 完整工作流测试（不含真实 LLM） ====================

    @Test
    void testGraphBuildsSuccessfully() {
        Graph<AgentState> built = graph.buildEmailAssistant();

        assertThat(built).isNotNull();
    }

    @Test
    void testGraphRun_IgnoreClassification() {
        // 验证 IGNORE 分类的邮件会直接结束（不进入 llmCall）
        AgentState initialState = new AgentState(
                "Marketing newsletter",
                AgentState.Classification.IGNORE,
                List.of(ChatMessage.user("Marketing newsletter")),
                null
        );

        // 手动执行 triage 验证路由
        AgentState afterTriage = initialState.withClassificationDecision(AgentState.Classification.IGNORE);
        String route = graph.routeFromTriage(afterTriage);

        assertThat(route).isEqualTo(Graph.END);
    }
}
