package com.emailassistant.tools;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ToolRegistry — 工具注册中心，管理所有可供 LLM 调用的工具。
 *
 * <p>工作原理：
 * <ol>
 *   <li>Spring 自动注入所有 {@link Tool} 接口的 Bean（来自 {@link ToolConfig}）</li>
 *   <li>构造器中按工具名建立索引，便于快速查找</li>
 *   <li>图工作流的 toolCallNode 通过 {@link #getTool(String)} 获取工具并执行</li>
 * </ol>
 *
 * <p>设计意图：
 * <ul>
 *   <li>解耦：图工作流不需要知道具体有哪些工具类，只需通过名称查找</li>
 *   <li>可扩展：新增工具只需添加一个 @Bean 方法，无需修改图逻辑</li>
 *   <li>统一管理：所有工具集中于此，便于审计和监控</li>
 * </ul>
 */
@Component
public class ToolRegistry {

    /** 工具名 → Tool 实例的不可变映射 */
    private final Map<String, Tool> tools;

    /**
     * Spring 自动注入所有 Tool 类型的 Bean，构造索引。
     *
     * @param toolBeans 容器中所有 Tool 接口的实现 Bean
     */
    public ToolRegistry(List<Tool> toolBeans) {
        this.tools = toolBeans.stream()
                .collect(Collectors.toUnmodifiableMap(
                        Tool::getName,
                        Function.identity(),
                        // 同名工具冲突时的处理：保留第一个注册的
                        (existing, duplicate) -> {
                            throw new IllegalStateException(
                                    "工具名称冲突：「" + existing.getName() + "」存在多个实现");
                        }));
    }

    /**
     * 根据名称获取工具。
     *
     * @param name 工具名
     * @return Tool 实例
     * @throws NoSuchToolException 如果找不到对应工具
     */
    public Tool getTool(String name) {
        Tool tool = tools.get(name);
        if (tool == null) {
            throw new NoSuchToolException(name);
        }
        return tool;
    }

    /**
     * 获取所有已注册工具的不可变视图。
     *
     * @return 工具名 → Tool 的映射
     */
    public Map<String, Tool> getAllTools() {
        return tools;
    }

    /**
     * 检查指定名称的工具是否存在。
     *
     * @param name 工具名
     * @return true 如果存在
     */
    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    /**
     * 获取已注册工具的数量。
     *
     * @return 工具总数
     */
    public int toolCount() {
        return tools.size();
    }
}
