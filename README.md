# Vaadin Browser Bridge

把本机真实 Chrome 嵌入 Vaadin 网页：服务端维持一个持久化 Chrome 会话，浏览器端通过 WebSocket 接收实时画面并转发鼠标、键盘、滚轮与标签页操作。

适合远程协助、受控演示、可视化自动化和需要真实浏览器环境的内部工具。

## 特性

- **真实 Chrome 内核**：通过 Playwright `channel=chrome` 启动本机安装的 Chrome，而不是 Playwright 自带 Chromium。
- **持久化配置**：每个配置目录保留 cookie、localStorage 与登录状态，重启会话不丢失。
- **窗口与画布尺寸锁定**：前端画布通过 `ResizeObserver` 上报尺寸，服务端用 CDP 把 Chrome 内容区锁定为同一尺寸；画面平铺拉伸，输入坐标按实际内容区比例映射。
- **稳定采帧**：直接通过 CDP `Page.captureScreenshot` 抓取合成器表面，不触发视口覆盖和窗口闪烁。
- **多标签页**：新建、切换、关闭标签页，支持 `window.open` 弹窗。
- **画面参数**：JPEG 质量、帧间隔、带宽与延迟模拟可在页面内调整。
- **实时遥测**：连接、服务器资源、浏览器版本、帧率、流量、输入队列状态。

## 环境要求

- JDK 25
- 本机安装 Google Chrome（或 Microsoft Edge）
- Node.js 20+
- 有图形界面的系统。默认 `headed`，Chrome 窗口需要真实桌面。

## 运行

```powershell
.\gradlew.bat bootRun
```

打开 `http://localhost:8080/`，可用 `?url=` 指定首个标签页：

```text
http://localhost:8080/?url=https://example.com/
```

构建与测试：

```powershell
.\gradlew.bat build
.\gradlew.bat test
```

## 配置

| 配置 | 系统属性 | 环境变量 | 默认值 |
| --- | --- | --- | --- |
| 运行模式 | `browser.bridge.mode` | `BROWSER_BRIDGE_MODE` | `headed` |
| 浏览器通道 | `browser.bridge.channel` | `BROWSER_BRIDGE_CHANNEL` | `chrome` |
| 配置根目录 | `browser.bridge.profile-root` | `BROWSER_BRIDGE_PROFILE_ROOT` | `~/.browser-bridge/profiles` |

通道只接受 `chrome` 与 `msedge`。`BROWSER_BRIDGE_MODE=headless` 可用于无桌面环境，但会失去真实窗口尺寸与部分浏览器特征。

## 结构

| 职责 | 入口 |
| --- | --- |
| 浏览器启动与配置 | [`BrowserRuntimeConfiguration.java`](src/main/java/dev/w0fv1/browserbridge/BrowserRuntimeConfiguration.java) |
| 会话、标签页、采帧与输入 | [`BrowserSessionManager.java`](src/main/java/dev/w0fv1/browserbridge/BrowserSessionManager.java) |
| WebSocket 协议与遥测 | [`BrowserStreamHandler.java`](src/main/java/dev/w0fv1/browserbridge/BrowserStreamHandler.java) |
| 前端画面与输入 | [`browser-stream.js`](src/main/frontend/browser-stream.js) |
| Vaadin 页面 | [`BrowserBridgeView.java`](src/main/java/dev/w0fv1/browserbridge/BrowserBridgeView.java) |

前端到服务端使用 `viewport.resize`、`tab.*`、`stream.configure` 与指针/键盘消息；服务端返回带 12 字节帧头的 JPEG 分片、会话状态和 `telemetry` 消息。

## 安全

当前示例没有身份认证、会话归属、目标地址白名单和资源配额。**不要把它直接暴露到公网。** 对外部署前至少需要：

- 登录态与 WebSocket 鉴权
- 会话与用户绑定
- 目标 URL / scheme 策略
- 会话数量、标签页数量与带宽配额

## License

MIT
