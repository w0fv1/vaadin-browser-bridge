# Vaadin Browser Bridge

把本机真实 Chrome 嵌入 Vaadin 网页：服务端维持一个持久化 Chrome 会话，浏览器端通过 WebSocket 接收实时画面并转发鼠标、键盘、滚轮与标签页操作。

适合远程协助、受控演示、可视化自动化和需要真实浏览器环境的内部工具。

## 特性

- **真实 Chrome 内核**：通过 Playwright `channel=chrome` 启动本机安装的 Chrome，而不是 Playwright 自带 Chromium。
- **统一运行模式**：headed 与 headless 共用同一 Chrome 通道、持久化 context、会话、采帧与输入实现；headless 不需要图形桌面。
- **持久化配置**：每个配置目录保留 cookie、localStorage 与登录状态，重启会话不丢失。
- **Profile 租约**：命名 profile 在一次运行中只能被一个活动会话占用，避免多个 Chrome 进程争用同一目录。
- **多页面共享**：同一 profile 的多个页面复用同一 Chrome 会话，最后一个页面释放后才关闭浏览器。
- **窗口与画布尺寸锁定**：前端画布通过 `ResizeObserver` 上报尺寸，服务端用 CDP 把 Chrome 内容区锁定为同一尺寸；画面平铺拉伸，输入坐标按实际内容区比例映射。
- **稳定采帧**：直接通过 CDP `Page.captureScreenshot` 抓取合成器表面，不触发视口覆盖和窗口闪烁。
- **多标签页**：新建、切换、关闭标签页，支持 `window.open` 弹窗。
- **画面参数**：JPEG 质量、帧间隔、带宽与延迟模拟可在页面内调整。
- **实时遥测**：连接、服务器资源、浏览器版本、帧率、流量、输入队列状态和最近一次浏览器操作错误。

## 环境要求

- JDK 25
- 本机安装 Google Chrome（或 Microsoft Edge）
- Node.js 20+
- `headed` 需要图形桌面；`headless` 可在无桌面环境运行。

## 运行

```powershell
.\gradlew.bat bootRun
```

无桌面环境使用标准 headless：

```powershell
$env:BROWSER_BRIDGE_MODE = 'headless'
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
.\gradlew.bat headedTest
```

`test` 运行 headless 契约；`headedTest` 运行需要图形桌面的真实 headed Chrome 契约。[`BrowserSessionContractTest.java`](src/test/java/dev/w0fv1/browserbridge/BrowserSessionContractTest.java) 使用本机真实 Chrome 和回环 HTTP 页面，在两种模式下执行同一组功能契约。

## 配置

| 配置 | 系统属性 | 环境变量 | 默认值 |
| --- | --- | --- | --- |
| 运行模式 | `browser.bridge.mode` | `BROWSER_BRIDGE_MODE` | `headed` |
| 浏览器通道 | `browser.bridge.channel` | `BROWSER_BRIDGE_CHANNEL` | `chrome` |
| 配置根目录 | `browser.bridge.profile-root` | `BROWSER_BRIDGE_PROFILE_ROOT` | `~/.browser-bridge/profiles` |

通道只接受 `chrome` 与 `msedge`，运行模式只接受 `headed` 与 `headless`。两种模式都使用本机安装的 Chrome/Edge；无头模式的 Chrome 版本、profile 和内容区尺寸由同一套配置控制。

两种模式只保证应用层行为一致。无头模式没有真实操作系统窗口，GPU、媒体解码、字体和窗口外观等能力仍取决于运行环境。需要完整的 headed 窗口与图形行为时，应在隔离的虚拟显示环境运行 headed，而不是在 headless 上伪装浏览器环境。

## 结构

| 职责 | 入口 |
| --- | --- |
| 运行配置 | [`BrowserRuntimeProperties.java`](src/main/java/dev/w0fv1/browserbridge/BrowserRuntimeProperties.java) |
| Playwright 与 Chrome 生命周期 | [`BrowserRuntime.java`](src/main/java/dev/w0fv1/browserbridge/BrowserRuntime.java) |
| Profile 目录与租约 | [`BrowserProfileRegistry.java`](src/main/java/dev/w0fv1/browserbridge/BrowserProfileRegistry.java) |
| 会话、输入与失败状态 | [`BrowserSession.java`](src/main/java/dev/w0fv1/browserbridge/BrowserSession.java) |
| 标签页与采帧 | [`BrowserTab.java`](src/main/java/dev/w0fv1/browserbridge/BrowserTab.java) |
| Headed/headless 视口语义 | [`BrowserViewport.java`](src/main/java/dev/w0fv1/browserbridge/BrowserViewport.java) |
| 会话注册 | [`BrowserSessionManager.java`](src/main/java/dev/w0fv1/browserbridge/BrowserSessionManager.java) |
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
