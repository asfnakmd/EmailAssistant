package com.emailassistant.graph.core;

import java.util.Map;
import java.util.function.Function;

/**
 * Graph — 简易图工作流引擎，用于串联 AI Agent 的各处理节点。
 *
 * <p>每个节点是一个 {@link Function}{@code <T, T>}，接收当前状态、返回新状态。
 * 节点间的流转由固定边（{@link #edges}）或条件边（{@link #conditionalEdges}）决定。
 *
 * <p>执行流程：从 entryPoint 节点开始，循环执行节点→路由→下一节点，直至遇到
 * {@link #END} 标记或没有出边为止。
 *
 * <h3>设计思想</h3>
 * 借鉴 LangGraph 的节点-边模型，但大幅简化：
 * <ul>
 *   <li>无子图、无并行分支 — 线性流程最适合邮件处理场景</li>
 *   <li>状态通过泛型 T 传递 — 与 AgentState 解耦</li>
 *   <li>节点是无状态的 Function — 所有状态在 T 中维护</li>
 * </ul>
 *
 * @param <T> 状态类型，推荐使用不可变 Record，通过 withXxx() 方法创建新实例
 */
public class Graph<T> {

    /** 结束标记：当路由函数返回此值时，工作流停止执行 */
    public static final String END = "__END__";

    private final Map<String, Function<T, T>> nodes;
    private final Map<String, String> edges;
    private final Map<String, Function<T, String>> conditionalEdges;
    private final String entryPoint;

    /**
     * 由 {@link GraphBuilder#build()} 调用，不对外公开。
     *
     * @param nodes            节点名 → 节点处理函数
     * @param edges            节点名 → 下一个节点名（固定边）
     * @param conditionalEdges 节点名 → 路由函数（条件边，优先级高于固定边）
     * @param entryPoint       入口节点名
     */
    Graph(Map<String, Function<T, T>> nodes,
          Map<String, String> edges,
          Map<String, Function<T, String>> conditionalEdges,
          String entryPoint) {
        this.nodes = Map.copyOf(nodes);
        this.edges = Map.copyOf(edges);
        this.conditionalEdges = Map.copyOf(conditionalEdges);
        this.entryPoint = entryPoint;
    }

    /**
     * 从 entryPoint 开始执行工作流，依次经过各节点处理，最终返回结束状态。
     *
     * <p>执行过程：
     * <ol>
     *   <li>从入口节点开始</li>
     *   <li>执行当前节点的处理函数，得到新状态</li>
     *   <li>通过条件边（优先）或固定边决定下一个节点</li>
     *   <li>如果路由到 {@link #END} 或无出边，停止执行</li>
     *   <li>否则重复步骤 2-4</li>
     * </ol>
     *
     * @param initialState 初始状态
     * @return 最终状态（经过所有节点处理后）
     * @throws IllegalStateException 如果路由到不存在的节点
     */
    public T run(T initialState) {
        T state = initialState;
        String current = entryPoint;

        while (!END.equals(current)) {
            // ---- 执行当前节点 ----
            Function<T, T> nodeFunc = nodes.get(current);
            if (nodeFunc == null) {
                throw new IllegalStateException(
                        "图中不存在节点 '" + current + "'。可用节点: " + nodes.keySet());
            }
            state = nodeFunc.apply(state);

            // ---- 路由：确定下一个节点 ----
            String next;
            if (conditionalEdges.containsKey(current)) {
                // 条件边优先：由路由函数根据当前状态决定走向
                next = conditionalEdges.get(current).apply(state);
            } else {
                // 固定边：直接取预设的下一个节点
                next = edges.get(current);
            }

            if (next == null) {
                // 没有出边或路由返回 null → 执行结束
                return state;
            }
            current = next;
        }
        return state;
    }
}
