package com.yupi.yuaiagent.agent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 智能体单步执行结果，包含类型和内容
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StepResult {

    /**
     * 步骤结果类型
     */
    public enum Type {
        TOOL_CALL,      // 工具调用（简要信息）
        FINAL_ANSWER    // 最终回答（LLM 的文本回复）
    }

    /**
     * 工具调用信息
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ToolCallInfo {
        private String name;        // 工具名称
        private String arguments;   // 工具参数（JSON字符串）
        private String result;      // 工具返回结果（可选）
        private List<String> images; // 图片URL列表（可选）
    }

    /**
     * 结果类型
     */
    private Type type;

    /**
     * 结果内容
     */
    private String content;

    /**
     * 工具调用详情列表
     */
    private List<ToolCallInfo> toolCalls;

    /**
     * 便捷构造方法（不含工具详情）
     */
    public StepResult(Type type, String content) {
        this.type = type;
        this.content = content;
        this.toolCalls = null;
    }
}
