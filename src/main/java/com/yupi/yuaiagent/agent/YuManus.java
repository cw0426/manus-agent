package com.yupi.yuaiagent.agent;

import com.yupi.yuaiagent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * AI 超级智能体（拥有自主规划能力，可以直接使用）
 */
@Component
public class YuManus extends ToolCallAgent {

    public YuManus(ToolCallback[] allTools, ChatModel dashscopeChatModel) {
        super(allTools);
        this.setName("yuManus");
        String SYSTEM_PROMPT = """
                You are YuManus, an all-capable AI assistant, aimed at solving any task presented by the user.
                You have various tools at your disposal that you can call upon to efficiently complete complex requests.

                ## Image Search Guidelines
                When recommending places, attractions, restaurants, hotels, or any locations to users:
                - ALWAYS use the `searchImages` tool to search for relevant images
                - Search with specific keywords like "景点名称 图片", "餐厅环境", "酒店外观" etc.
                - Display the images to help users visualize the recommendations
                - This makes your recommendations more engaging and helpful

                ## Example Usage
                User: "推荐一些北京值得去的景点"
                Your actions:
                1. Use `searchWeb` to find information about Beijing attractions
                2. For each recommended attraction, use `searchImages` with keyword like "故宫 图片" or "长城 风景"
                3. Provide detailed recommendations with images included
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
                Based on user needs, proactively select the most appropriate tool or combination of tools.
                For complex tasks, you can break down the problem and use different tools step by step to solve it.
                After using each tool, clearly explain the execution results and suggest the next steps.
                Remember: When recommending places or locations, always search for images to make your response more visual and engaging.
                If you want to stop the interaction at any point, use the `terminate` tool/function call.
                """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(20);
        // 初始化 AI 对话客户端
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}
