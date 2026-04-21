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

## 快速开始

### 1. 环境准备

- JDK 21+
- Maven 3.6+
- Docker（用于 PGVector 向量数据库）

### 2. 启动 PGVector 向量数据库

```bash
# 拉取并启动 PGVector 容器
docker run -e POSTGRES_USER=wzrooo -e POSTGRES_PASSWORD=mypassword -e POSTGRES_DB=yu_ai_agent --name postgresql -p 5432:5432 -d pgvector/pgvector:pg16

# 进入容器启用 vector 扩展
docker exec -it postgresql psql -U wzrooo -d yu_ai_agent -c "CREATE EXTENSION vector;"

# 创建向量存储表
docker exec -it postgresql psql -U wzrooo -d yu_ai_agent -c "
CREATE TABLE IF NOT EXISTS public.vector_store
(
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content   TEXT,
    metadata  JSONB,
    embedding VECTOR(1536)
);
"
```

### 3. 配置项目

修改 `src/main/resources/application.yml`，配置数据库连接：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/yu_ai_agent
    username: wzrooo
    password: mypassword
    driver-class-name: org.postgresql.Driver
```

### 4. 启动项目

```bash
mvn spring-boot:run
```

首次启动会自动加载知识库文档到 PGVector 数据库，后续启动会自动跳过（避免重复加载）。

### 5. 访问接口

- Swagger 文档：http://localhost:8123/api/swagger-ui.html
- RAG 对话接口：`GET /api/ai/love_app/chat/rag?message=你的问题&chatId=会话ID`

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
- **PGVector 向量数据库支持**：支持本地 Docker 部署，数据持久化存储
- **智能文档加载**：首次启动加载文档，后续启动自动跳过，避免重复
- **RAG 接口暴露**：新增 `/love_app/chat/rag` 接口，支持知识库问答
- **系统提示词优化**：更详细、更有深度的 AI 回答，包含心理学原理和实操建议

### 3. 依赖优化
- 添加 Bean Validation 支持（spring-boot-starter-validation）

### 4. 配置优化
- 启用 DataSource 自动配置
- 禁用内存向量存储（使用 PGVector 替代）

## 致谢

本项目基于 [程序员鱼皮](https://github.com/liyupi) 的 [yu-ai-agent](https://github.com/liyupi/yu-ai-agent) 项目开发，感谢原作者的开源贡献。

## License

MIT License
