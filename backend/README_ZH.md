# AI 编程助手后端

AI 编程助手的 Java 业务服务，负责用户认证、权限控制、会话消息、模型配置、Prompt 版本、知识库和反馈统计。

## 技术栈

Java 21、Spring Boot、Spring Security、MyBatis-Plus、Redis、PostgreSQL、pgvector 和 SSE。

## 启动

```bash
mvn -DskipTests package
java -jar aicoding-admin/target/aicoding-admin.jar
```

运行配置位于 `aicoding-admin/src/main/resources/application*.yml`。请通过环境变量配置数据库、Redis、对象存储和模型密钥。

## 工程结构

- `aicoding-admin`：应用启动与接口聚合
- `aicoding-common`：安全、缓存、日志、Web、SSE 等基础能力
- `aicoding-modules`：系统、聊天、工作流、知识库等业务模块
- `aicoding-extend`：可选扩展服务
