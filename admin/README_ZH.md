# AI 编程助手管理后台

面向管理员和运维人员的业务管理端，提供模型配置、Prompt 版本、知识库、文档、用户角色、调用日志和异常反馈管理。

## 开发命令

```bash
pnpm install
pnpm --filter @vben/web-antd dev
pnpm --filter @vben/web-antd build:prod
```

管理端通过统一 Java 业务接口访问数据，生产环境地址由部署配置注入。
