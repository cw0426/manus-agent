# AI 超级智能体项目

> 本项目基于 [程序员鱼皮](https://github.com/liyupi/yu-ai-agent) 的 AI 超级智能体项目进行改动和优化。

## 项目介绍

这是一套以 **AI 开发实战** 为核心的项目，包含 **AI 恋爱大师应用** 和 **拥有自主规划能力的超级智能体**。

`AI 恋爱大师应用` 可以依赖 AI 大模型解决用户的情感问题，支持多轮对话、基于自定义知识库进行问答、自主调用工具和 MCP 服务完成任务。

`自主规划智能体 YuManus` 可以利用网页搜索、资源下载和 PDF 生成工具，帮用户制定完整的约会计划并生成文档。

## 项目功能

- **AI 恋爱大师应用**：用户在恋爱过程中难免遇到各种难题，让 AI 为用户提供贴心情感指导。支持多轮对话、对话记忆持久化、RAG 知识库检索、工具调用、MCP 服务调用。
- **AI 超级智能体**：可以根据用户的需求，自主推理和行动，直到完成目标。
- **提供给 AI 的工具**：包括联网搜索、文件操作、网页抓取、资源下载、终端操作、PDF 生成。
- **AI MCP 服务**：可以从特定网站搜索图片。

## 技术栈

- Java 21 + Spring Boot 3 框架
- Spring AI + LangChain4j
- RAG 知识库
- PGvector 向量数据库
- Tool Calling 工具调用
- MCP 模型上下文协议
- ReAct Agent 智能体构建
- Serverless 计算服务
- AI 大模型开发平台百炼
- SSE 异步推送
- 第三方接口：如 SearchAPI / Pexels API
- Ollama 大模型部署
- 工具库：Kryo 高性能序列化 + Jsoup 网页抓取 + iText PDF 生成 + Knife4j 接口文档

## 项目改动说明

本项目在原项目基础上进行了以下改动：

### 1. Bug 修复
- 修复 SLF4J 多绑定警告
- 修复 SSE 连接断开错误
- 修复历史对话空会话残留问题
- 修复终端命令执行卡死问题（错误流阻塞）

### 2. 功能增强
- AI 回复添加免责声明
- 历史对话保存功能（支持新建、切换、删除、清空）
- Markdown 渲染支持
- AI 超级智能体工具调用详情前端展示
- 区分 AI 最终答复与中间思考过程

### 3. 依赖优化
- 添加 Bean Validation 支持（spring-boot-starter-validation）


## 致谢

本项目基于 [程序员鱼皮](https://github.com/liyupi) 的 [yu-ai-agent](https://github.com/liyupi/yu-ai-agent) 项目开发，感谢原作者的开源贡献。

## License

MIT License
