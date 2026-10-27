# AI 编程助手桌面端

桌面端复用 `web` 用户端页面。开发时先启动 Web 用户端，再执行 `pnpm dev`；打包执行 `pnpm dist`。

生产环境通过 `AI_CODING_WEB_URL` 指定业务地址。签名证书由 CI 注入 electron-builder 配置，自动更新地址在 `package.json` 的 `publish` 字段配置。

