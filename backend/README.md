# AI Coding Assistant Backend

Java 21 / Spring Boot 业务服务，为 AI 编程助手提供认证、权限、会话、消息、模型、Prompt、知识库和反馈管理能力。

## 技术栈

Java 21、Spring Boot、Spring Security、MyBatis-Plus、Redis、PostgreSQL、pgvector 和 SSE。

## 本地启动

```bash
mvn -DskipTests package
java -jar aicoding-admin/target/aicoding-admin.jar
```

服务配置位于 `aicoding-admin/src/main/resources/application*.yml`。数据库、Redis、模型服务地址通过环境变量注入，敏感信息不写入仓库。

## 模块

`aicoding-admin` 为启动模块，`aicoding-common` 提供基础能力，`aicoding-modules` 提供系统、聊天、工作流和知识库业务，`aicoding-extend` 提供扩展服务。
