package com.yupi.yuaiagent.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.yupi.yuaiagent.agent.model.AgentState;
import com.yupi.yuaiagent.agent.model.StepResult;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 处理工具调用的基础代理类，具体实现了 think 和 act 方法，可以用作创建实例的父类
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    // 可用的工具
    private final ToolCallback[] availableTools;

    // 保存工具调用信息的响应结果（要调用那些工具）
    private ChatResponse toolCallChatResponse;

    // 工具调用管理者
    private final ToolCallingManager toolCallingManager;

    // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
    private final ChatOptions chatOptions;

    // 保存最终回答文本（当 think 返回 false 时，LLM 的文本回复）
    private String finalAnswerText;

    public ToolCallAgent(ToolCallback[] availableTools) {
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        // 禁用 Spring AI 内置的工具调用机制，自己维护选项和消息上下文
        this.chatOptions = DashScopeChatOptions.builder()
                .withInternalToolExecutionEnabled(false)
                .build();
    }

    /**
     * 处理当前状态并决定下一步行动
     *
     * @return 是否需要执行行动
     */
    @Override
    public boolean think() {
        // 1、校验提示词，拼接用户提示词
        if (StrUtil.isNotBlank(getNextStepPrompt())) {
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessageList().add(userMessage);
        }
        // 2、调用 AI 大模型，获取工具调用结果
        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, this.chatOptions);
        try {
            ChatResponse chatResponse = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .toolCallbacks(List.of(availableTools))
                    .call()
                    .chatResponse();
            // 记录响应，用于等下 Act
            this.toolCallChatResponse = chatResponse;
            // 3、解析工具调用结果，获取要调用的工具
            // 助手消息
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            // 获取要调用的工具列表
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
            // 输出提示信息
            String result = assistantMessage.getText();
            log.info(getName() + "的思考：" + result);
            log.info(getName() + "选择了 " + toolCallList.size() + " 个工具来使用");
            String toolCallInfo = toolCallList.stream()
                    .map(toolCall -> String.format("工具名称：%s，参数：%s", toolCall.name(), toolCall.arguments()))
                    .collect(Collectors.joining("\n"));
            log.info(toolCallInfo);
            // 如果不需要调用工具，保存最终回答文本
            if (toolCallList.isEmpty()) {
                // 只有不调用工具时，才需要手动记录助手消息
                getMessageList().add(assistantMessage);
                this.finalAnswerText = StrUtil.isNotBlank(result) ? result : "思考完成";
                return false;
            } else {
                // 需要调用工具时，也保存 LLM 的文本内容（可能包含最终回答）
                this.finalAnswerText = StrUtil.isNotBlank(result) ? result : null;
                return true;
            }
        } catch (Exception e) {
            log.error(getName() + "的思考过程遇到了问题：" + e.getMessage());
            getMessageList().add(new AssistantMessage("处理时遇到了错误：" + e.getMessage()));
            this.finalAnswerText = "处理时遇到了错误：" + e.getMessage();
            return false;
        }
    }

    /**
     * 执行工具调用并处理结果，返回包含详细工具调用信息的 StepResult
     *
     * @return 执行结果
     */
    @Override
    public StepResult act() {
        if (!toolCallChatResponse.hasToolCalls()) {
            return new StepResult(StepResult.Type.FINAL_ANSWER, "没有工具需要调用");
        }
        // 获取工具调用信息
        AssistantMessage assistantMessage = toolCallChatResponse.getResult().getOutput();
        List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();

        // 构建工具调用详情列表
        List<StepResult.ToolCallInfo> toolCallInfos = new ArrayList<>();
        for (AssistantMessage.ToolCall toolCall : toolCallList) {
            StepResult.ToolCallInfo info = new StepResult.ToolCallInfo();
            info.setName(toolCall.name());
            info.setArguments(toolCall.arguments());
            toolCallInfos.add(info);
        }

        // 构建简要工具调用摘要
        String toolCallSummary = toolCallList.stream()
                .map(toolCall -> String.format("🔧 调用工具：%s\n   参数：%s", toolCall.name(), toolCall.arguments()))
                .collect(Collectors.joining("\n"));

        // 调用工具
        Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);
        // 记录消息上下文，conversationHistory 已经包含了助手消息和工具调用返回的结果
        setMessageList(toolExecutionResult.conversationHistory());
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());

        // 更新工具调用结果
        List<ToolResponseMessage.ToolResponse> responses = toolResponseMessage.getResponses();
        for (int i = 0; i < responses.size() && i < toolCallInfos.size(); i++) {
            ToolResponseMessage.ToolResponse response = responses.get(i);
            toolCallInfos.get(i).setResult(response.responseData());
            // 尝试从结果中提取图片URL
            List<String> images = extractImageUrls(response.responseData());
            if (!images.isEmpty()) {
                toolCallInfos.get(i).setImages(images);
            }
        }

        // 判断是否调用了终止工具
        boolean terminateToolCalled = responses.stream()
                .anyMatch(response -> response.name().equals("doTerminate"));
        if (terminateToolCalled) {
            // 任务结束，更改状态
            setState(AgentState.FINISHED);
            // 如果 LLM 在调用终止工具的同时输出了文本内容，作为最终回答发送
            if (StrUtil.isNotBlank(finalAnswerText)) {
                return new StepResult(StepResult.Type.FINAL_ANSWER, finalAnswerText);
            }
        }
        String results = responses.stream()
                .map(response -> "工具 " + response.name() + " 返回的结果：" + response.responseData())
                .collect(Collectors.joining("\n"));
        log.info(results);

        // 返回包含详细工具调用信息的 StepResult
        StepResult stepResult = new StepResult(StepResult.Type.TOOL_CALL, toolCallSummary);
        stepResult.setToolCalls(toolCallInfos);
        return stepResult;
    }

    /**
     * 从工具返回结果中提取图片URL
     */
    private List<String> extractImageUrls(String responseData) {
        List<String> images = new ArrayList<>();
        if (responseData == null) {
            return images;
        }
        // 尝试解析JSON并提取图片URL字段
        try {
            cn.hutool.json.JSONObject jsonObj = cn.hutool.json.JSONUtil.parseObj(responseData);

            // 检查 images 数组（图片搜索工具返回格式）
            cn.hutool.json.JSONArray imagesArray = jsonObj.getJSONArray("images");
            if (imagesArray != null && !imagesArray.isEmpty()) {
                for (Object obj : imagesArray) {
                    if (obj instanceof cn.hutool.json.JSONObject) {
                        cn.hutool.json.JSONObject imgObj = (cn.hutool.json.JSONObject) obj;
                        String url = imgObj.getStr("imageUrl");
                        if (url != null && url.startsWith("http")) {
                            images.add(url);
                        }
                    }
                }
                if (!images.isEmpty()) {
                    return images;
                }
            }

            // 检查常见的图片字段
            String[] imageFields = {"image", "imageUrl", "image_url", "img", "imgUrl", "photo", "picture"};
            for (String field : imageFields) {
                if (jsonObj.containsKey(field)) {
                    String url = jsonObj.getStr(field);
                    if (url != null && url.startsWith("http")) {
                        images.add(url);
                    }
                }
            }
            // 检查图片数组字段
            String[] arrayFields = {"imageUrls", "photos", "pictures"};
            for (String field : arrayFields) {
                if (jsonObj.containsKey(field)) {
                    cn.hutool.json.JSONArray arr = jsonObj.getJSONArray(field);
                    if (arr != null) {
                        for (Object obj : arr) {
                            String url = obj.toString();
                            if (url.startsWith("http")) {
                                images.add(url);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 不是JSON格式，忽略
        }
        return images;
    }

    @Override
    protected String getFinalAnswer() {
        return finalAnswerText;
    }
}
