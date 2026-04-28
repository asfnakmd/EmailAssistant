package com.emailassistant.graph.core;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * GraphBuilder — 图工作流的构建器，采用 Builder 模式逐步构建 {@link Graph}。
 *
 * <p>用法示例：
 * <pre>{@code
 * Graph<AgentState> graph = GraphBuilder.<AgentState>create()
 *     .addNode("triage", this::triageNode)
 *     .addNode("llmCall", this::llmCallNode)
 *     .addConditionalEdge("triage", this::routeFromTriage)
 *     .addConditionalEdge("llmCall", this::shouldContinue)
 *     .setEntryPoint("triage")
 *     .build();
 *
 * AgentState result = graph.run(initialState);
 * }</pre>
 *
 * <h3>规则说明</h3>
 * <ul>
 *   <li>节点通过 {@code addNode()} 注册，名称字符串唯一标识</li>
 *   <li>固定边通过 {@code addEdge(from, to)} 注册，无条件走向 to</li>
 *   <li>条件边通过 {@code addConditionalEdge(from, router)} 注册，
 *       router 函数根据当前状态返回目标节点名或 {@link Graph#END}</li>
 *   <li>同一节点不可同时拥有固定边和条件边（条件边优先）</li>
 *   <li>入口节点未设置时默认取第一个注册的节点</li>
 * </ul>
 *
 * @param <T> 状态类型
 */
public class GraphBuilder<T> {

    /** 节点名 → 节点处理函数（有序，确保可预测的遍历顺序） */
    private final Map<String, Function<T, T>> nodes = new LinkedHashMap<>();

    /** 固定边：节点名 → 下一个节点名 */
    private final Map<String, String> edges = new HashMap<>();

    /** 条件边：节点名 → 路由函数 */
    private final Map<String, Function<T, String>> conditionalEdges = new HashMap<>();

    /** 入口节点名 */
    private String entryPoint;

    /** 私有构造器，通过 {@link #create()} 创建实例 */
    private GraphBuilder() {}

    /**
     * 创建 GraphBuilder 实例。
     *
     * @param <T> 状态类型
     * @return 新的构建器
     */
    public static <T> GraphBuilder<T> create() {
        return new GraphBuilder<>();
    }

    /**
     * 注册一个处理节点。
     *
     * @param name         节点名称（必须唯一）
     * @param nodeFunction 节点处理函数：接收当前状态 → 返回新状态
     * @return this（链式调用）
     */
    public GraphBuilder<T> addNode(String name, Function<T, T> nodeFunction) {
        nodes.put(name, nodeFunction);
        return this;
    }

    /**
     * 注册一条固定边。执行完 from 节点后无条件走向 to 节点。
     *
     * @param from 源节点名
     * @param to   目标节点名（使用 {@link Graph#END} 表示结束）
     * @return this（链式调用）
     */
    public GraphBuilder<T> addEdge(String from, String to) {
        edges.put(from, to);
        return this;
    }

    /**
     * 注册一条条件边。执行完 from 节点后由 router 函数动态决定走向。
     *
     * <p>条件边优先级高于固定边。如果同一节点同时注册了固定边和条件边，
     * 固定边将被忽略。
     *
     * @param from   源节点名
     * @param router 路由函数：接收当前状态 → 返回目标节点名或 {@link Graph#END}
     * @return this（链式调用）
     */
    public GraphBuilder<T> addConditionalEdge(String from, Function<T, String> router) {
        conditionalEdges.put(from, router);
        return this;
    }

    /**
     * 设置入口节点。不调用此方法时，默认使用第一个注册的节点作为入口。
     *
     * @param entryPoint 入口节点名
     * @return this（链式调用）
     */
    public GraphBuilder<T> setEntryPoint(String entryPoint) {
        this.entryPoint = entryPoint;
        return this;
    }

    /**
     * 构建不可变的 {@link Graph} 实例。
     *
     * @return 构建完成的 Graph
     * @throws IllegalStateException 如果没有注册任何节点
     */
    public Graph<T> build() {
        if (nodes.isEmpty()) {
            throw new IllegalStateException("图中至少需要一个节点");
        }
        if (entryPoint == null) {
            entryPoint = nodes.keySet().iterator().next();
        }
        return new Graph<>(nodes, edges, conditionalEdges, entryPoint);
    }
}
