package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.agent.model.StepResult;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * ReAct (Reasoning and Acting) 模式的代理抽象类
 * 实现了思考-行动的循环模式
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public abstract class ReActAgent extends BaseAgent {

    /**
     * 处理当前状态并决定下一步行动
     *
     * @return 是否需要执行行动，true表示需要执行，false表示不需要执行
     */
    public abstract boolean think();

    /**
     * 执行决定的行动
     *
     * @return 行动执行结果
     */
    public abstract StepResult act();

    /**
     * 执行单个步骤：思考和行动
     *
     * @return 步骤执行结果
     */
    @Override
    public StepResult step() {
        try {
            // 先思考
            boolean shouldAct = think();
            if (!shouldAct) {
                // 思考完成，不需要行动，返回最终回答
                return new StepResult(StepResult.Type.FINAL_ANSWER, getFinalAnswer());
            }
            // 再行动
            return act();
        } catch (Exception e) {
            // 记录异常日志
            e.printStackTrace();
            return new StepResult(StepResult.Type.FINAL_ANSWER, "步骤执行失败：" + e.getMessage());
        }
    }

    /**
     * 获取最终回答文本（由子类实现，从 think 阶段获取 LLM 的文本回复）
     */
    protected abstract String getFinalAnswer();

}
