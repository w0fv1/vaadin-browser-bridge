# Vaadin Remote Browser

把本机真实 Chrome 嵌入 Vaadin 网页：服务端维持一个持久化 Chrome 会话，浏览器端通过 WebSocket 接收实时画面并转发鼠标、键盘、滚轮与标签页操作。

适合远程协助、受控演示、可视化自动化和需要真实浏览器环境的内部工具。

## 特性

- **真实 Chrome 内核**：直接启动本机安装的 Chrome/Edge 进程，不依赖 Playwright、Puppeteer 或 Selenium，因此不会带上自动化启动器注入的调试特征。
- **纯 CDP 控制**：画面采集、输入下发、标签页与视口全部走 DevTools 协议，不开启 `Runtime.enable`、不注入页面脚本。
- **统一运行模式**：headed 与 headless 共用同一 Chrome 通道、持久化 profile、会话、采帧与输入实现；headless 不需要图形桌面。
- **持久化配置**：每个配置目录保留 cookie、localStorage 与登录状态，重启会话不丢失。
- **Profile 租约**：命名 profile 在一次运行中只能被一个活动会话占用，避免多个 Chrome 进程争用同一目录。
- **环境画像**：每个 Profile 可固定语言、时区、色彩方案、设备像素比和权限，也可保持原生环境。
- **多页面共享**：同一 profile 的多个页面复用同一 Chrome 会话，最后一个页面释放后才关闭浏览器。
- **窗口与画布尺寸锁定**：前端画布通过 `ResizeObserver` 上报尺寸，服务端用 CDP 把 Chrome 内容区锁定为同一尺寸；画面平铺拉伸，输入坐标按实际内容区比例映射。
- **稳定采帧**：直接通过 CDP `Page.captureScreenshot` 抓取合成器表面，不触发视口覆盖和窗口闪烁。
- **多标签页**：新建、切换、关闭标签页，支持 `window.open` 弹窗。
- **画面参数**：JPEG 质量、帧间隔、带宽与延迟模拟可在页面内调整。
- **实时遥测**：连接、服务器资源、浏览器版本、帧率、流量、输入队列状态和最近一次浏览器操作错误。

## 反自动化检测约束

以下不变量由 [`BrowserStealthTest.java`](src/test/java/dev/w0fv1/remotebrowser/BrowserStealthTest.java) 守护，改动时不要绕过：

- 启动参数只有 [`BrowserRuntime#arguments`](src/main/java/dev/w0fv1/remotebrowser/BrowserRuntime.java) 一个来源；其中不出现 `--enable-automation`，并用 `--disable-blink-features=AutomationControlled` 保持 `navigator.webdriver === false`。
- 不开启 `Runtime.enable`，不使用 `Page.addScriptToEvaluateOnNewDocument`，不注入任何页面脚本；标签页地址与标题取自 `Target.getTargetInfo`。
- 页面环境只用 Chrome 启动参数与 `Emulation` 域的浏览器原生覆盖实现，见 [`BrowserPageEnvironment.java`](src/main/java/dev/w0fv1/remotebrowser/BrowserPageEnvironment.java)。
- 输入按真实鼠标、键盘语义下发：携带修饰键位掩码与点击计数，键位带 `code`、`keyCode`、`location`，不伪造事件时间戳。
- 关闭浏览器先发 `Browser.close` 并等待进程自行退出，保证 profile 落盘。

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
$env:BROWSER_REMOTE_MODE = 'headless'
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

`test` 运行 headless 契约；`headedTest` 运行需要图形桌面的真实 headed Chrome 契约。[`BrowserSessionContractTest.java`](src/test/java/dev/w0fv1/remotebrowser/BrowserSessionContractTest.java) 使用本机真实 Chrome 和回环 HTTP 页面，在两种模式下执行同一组功能契约。

## 配置

| 配置 | 系统属性 | 环境变量 | 默认值 |
| --- | --- | --- | --- |
| 运行模式 | `browser.remote.mode` | `BROWSER_REMOTE_MODE` | `headed` |
| 浏览器通道 | `browser.remote.channel` | `BROWSER_REMOTE_CHANNEL` | `chrome` |
| 配置根目录 | `browser.remote.profile-root` | `BROWSER_REMOTE_PROFILE_ROOT` | `~/.vaadin-remote-browser/profiles` |
| 可执行文件 | `browser.remote.executable` | `BROWSER_REMOTE_EXECUTABLE` | 按通道自动定位 |

通道只接受 `chrome` 与 `msedge`，运行模式只接受 `headed` 与 `headless`。两种模式都使用本机安装的 Chrome/Edge；无头模式的 Chrome 版本、profile 和内容区尺寸由同一套配置控制。

两种模式只保证应用层行为一致。无头模式没有真实操作系统窗口，GPU、媒体解码、字体和窗口外观等能力仍取决于运行环境。需要完整的 headed 窗口与图形行为时，应在隔离的虚拟显示环境运行 headed，而不是在 headless 上伪装浏览器环境。

## 浏览器环境画像

每个 Profile 的 Chrome 数据目录可以直接放置 `environment.json`：

```text
~/.vaadin-remote-browser/profiles/<profile-id>/
├── environment.json
└── Chrome 配置、Cookie 与本地存储
```

没有 `environment.json` 时使用 `NATIVE` 模式，完整继承真实 Chrome 与操作系统环境。需要固定环境时写入：

```json
{
  "profileId": "demo",
  "mode": "MANAGED",
  "locale": "fr-CA",
  "timezone": "America/Toronto",
  "colorScheme": "DARK",
  "deviceScaleFactor": 1.25,
  "permissions": ["GEOLOCATION", "CLIPBOARD_READ"]
}
```

- `profileId` 必须与目录名一致，只允许字母、数字、下划线和连字符，最长 64 个字符。
- `mode` 为 `NATIVE` 或 `MANAGED`；`NATIVE` 不允许覆盖其他环境字段。
- `locale` 使用 BCP 47 标签，同时写入 Chrome 的 `--accept-lang` 与页面 locale 覆盖，保证 `navigator.language`、`navigator.languages` 与请求头一致。
- `timezone` 使用 IANA 时区标识，`colorScheme` 为 `LIGHT`、`DARK` 或 `NO_PREFERENCE`。
- `deviceScaleFactor` 范围为 `0.5` 至 `4.0`，通过 Chrome 的设备像素比启动参数应用。
- `permissions` 使用 [`BrowserPermission.java`](src/main/java/dev/w0fv1/remotebrowser/BrowserPermission.java) 中的枚举名，按 CDP 权限类型授权。

画像在 Profile 租约创建时读取，运行中不会热更新；修改后新建会话生效。同一 Profile 同时只允许一个活动会话。视口尺寸仍由页面 `viewport.resize` 流程管理，不写入画像。

环境画像不注入 stealth 脚本，也不修改 Canvas、WebGL、字体或浏览器原型；它只使用 Chrome 启动参数和 `Emulation` 域的原生覆盖，保持原型链与函数 `toString` 的原样输出。

## 结构

| 职责 | 入口 |
| --- | --- |
| 运行配置 | [`BrowserRuntimeProperties.java`](src/main/java/dev/w0fv1/remotebrowser/BrowserRuntimeProperties.java) |
| 浏览器进程与启动参数 | [`BrowserRuntime.java`](src/main/java/dev/w0fv1/remotebrowser/BrowserRuntime.java)、[`ChromeProcess.java`](src/main/java/dev/w0fv1/remotebrowser/ChromeProcess.java)、[`ChromeBrowser.java`](src/main/java/dev/w0fv1/remotebrowser/ChromeBrowser.java) |
| 可执行文件定位 | [`BrowserChannel.java`](src/main/java/dev/w0fv1/remotebrowser/BrowserChannel.java) |
| DevTools 协议传输 | [`CdpConnection.java`](src/main/java/dev/w0fv1/remotebrowser/CdpConnection.java)、[`CdpSession.java`](src/main/java/dev/w0fv1/remotebrowser/CdpSession.java) |
| Profile 目录与租约 | [`BrowserProfileRegistry.java`](src/main/java/dev/w0fv1/remotebrowser/BrowserProfileRegistry.java) |
| 环境画像与校验 | [`BrowserEnvironmentProfile.java`](src/main/java/dev/w0fv1/remotebrowser/BrowserEnvironmentProfile.java) |
| 页面环境覆盖 | [`BrowserPageEnvironment.java`](src/main/java/dev/w0fv1/remotebrowser/BrowserPageEnvironment.java) |
| 画像文件存储 | [`BrowserEnvironmentStore.java`](src/main/java/dev/w0fv1/remotebrowser/BrowserEnvironmentStore.java) |
| 会话、输入与失败状态 | [`BrowserSession.java`](src/main/java/dev/w0fv1/remotebrowser/BrowserSession.java) |
| 输入协议 | [`BrowserInput.java`](src/main/java/dev/w0fv1/remotebrowser/BrowserInput.java)、[`BrowserKeyInput.java`](src/main/java/dev/w0fv1/remotebrowser/BrowserKeyInput.java) |
| 标签页与采帧 | [`BrowserTab.java`](src/main/java/dev/w0fv1/remotebrowser/BrowserTab.java) |
| Headed/headless 视口语义 | [`BrowserViewport.java`](src/main/java/dev/w0fv1/remotebrowser/BrowserViewport.java) |
| 会话注册 | [`BrowserSessionManager.java`](src/main/java/dev/w0fv1/remotebrowser/BrowserSessionManager.java) |
| WebSocket 协议与遥测 | [`BrowserStreamHandler.java`](src/main/java/dev/w0fv1/remotebrowser/BrowserStreamHandler.java) |
| 前端画面与输入 | [`browser-stream.js`](src/main/frontend/browser-stream.js) |
| Vaadin 页面 | [`RemoteBrowserView.java`](src/main/java/dev/w0fv1/remotebrowser/RemoteBrowserView.java) |

前端到服务端使用 `viewport.resize`、`tab.*`、`stream.configure`、指针消息（`move`/`down`/`up`/`cancel`/`context`/`wheel`）、键盘消息（`key`）与输入法提交（`text`）；服务端返回带 12 字节帧头的 JPEG 分片、会话状态和 `telemetry` 消息。

## 安全

当前示例没有身份认证、会话归属、目标地址白名单和资源配额。**不要把它直接暴露到公网。** 对外部署前至少需要：

- 登录态与 WebSocket 鉴权
- 会话与用户绑定
- 目标 URL / scheme 策略
- 会话数量、标签页数量与带宽配额

## License

MIT
