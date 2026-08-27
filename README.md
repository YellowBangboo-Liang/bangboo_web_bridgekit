# BridgeKit

> 一个以 Jetpack Compose + MVVM 构建的原创 Android WebView / JSBridge Demo。

BridgeKit 用一个可直接运行的离线 H5 页面，展示 Hybrid 容器开发中最常见、也最容易出问题的能力边界：协议设计、双向异步调用、能力注入、资源拦截、生命周期与安全控制。首页将 H5 演示嵌入 Compose 页面；底部的“关于我”则是通往独立离线作品集的彩蛋入口。

作者 / Author: **梁鸿禄**

## What it demonstrates

| 能力 | 实现位置 |
| --- | --- |
| JS → Native Promise | `JsBridge.handleJsCall` + `window.JSBridge.call` |
| Native → JS 事件与回调 | `JsBridge.callJs` + `nativeEvent` |
| 通用能力注册 | `MethodRegistry` / `BridgeMethodHandler` |
| 一次性异步回调 | `CallbackRegistry`，消费后立即释放 |
| Android 注入适配 | `NativeBridge` 的 `@JavascriptInterface` |
| URL Scheme | `jsbridge://action?param=…` 拦截与宿主回调 |
| 首页内嵌 H5 | `demo.html`：设备信息、Toast 与 Native → JS 事件展示 |
| 离线作品集彩蛋 | 首页底部“关于我”→ 独立加载 Vue 作品集 `index.html` |
| 本地离线资源 | `WebViewAssetLoader` → `https://appassets.androidplatform.net/assets/...` |
| Compose + MVVM | `DemoRoute`、`DemoViewModel`、`DemoScreenPreview` |

## Project structure

```text
app/src/main/java/com/lianghonglu/bridgekit/
├── bridge/       # 纯 Kotlin 协议、方法与回调，不依赖 Android，可 JVM 单测
├── container/    # BridgeWebView：注入、导航保护、资源拦截与释放
├── feature/demo/ # Compose UI、Preview 和 MVVM 状态
└── MainActivity.kt
app/src/main/assets/h5/app/
├── demo.html                       # 首页内嵌的轻量 JSBridge H5 演示
├── index.html                      # “关于我”页的离线 Vue 作品集
└── portfolio.json                  # 作品集数据
```

## Run

1. 用 Android Studio 打开项目根目录。
2. 选择 API 24+ 的模拟器或真机，运行 `app`。
3. 或在 Windows 命令行中执行：

```powershell
$env:JAVA_HOME = 'C:\Path\To\JDK-17'
.\gradlew.bat assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

启动后可按下面顺序体验：

- 首页内嵌 H5 的“设备信息”和 Toast 按钮（JS → Native）；
- 首页的“Native → JS 主动事件”按钮，H5 会显示事件内容；
- 从 H5 卡片区域纵向拖动，仍可滚动整个首页；
- 底部“关于我”进入离线 Vue 作品集，并可从顶部返回；
- Compose 中记录的 Native 侧事件日志；
- Android Studio 中的 `DemoScreenPreview`（不需要 WebView 也能预览 UI）。

## Protocol

```jsonc
// JS → Native
{ "method": "device.info", "params": {}, "callbackId": "cb_js_1" }

// Native → JS result
{ "callbackId": "cb_js_1", "result": "Android 15 · Pixel" }

// Native → JS event
{ "method": "native.greeting", "params": { "message": "Hello" }, "callbackId": "cb_0" }
```

## Security notes

`@JavascriptInterface` 只能暴露给可信内容。本 Demo 只允许 `https://appassets.androidplatform.net/assets/` 下的内置资源加载；所有其他导航都会被拦截。Native→JS 的参数使用 JSON 字符串字面量编码，而不是拼接不可信的可执行 JavaScript。实际项目还应结合业务场景加入域名白名单、方法权限、签名校验和离线包完整性校验。

## Tests

```powershell
$env:JAVA_HOME = 'C:\Path\To\JDK-17'
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

JVM 测试覆盖消息序列化、能力注册、回调消费、双向协议门面和 ViewModel 状态顺序。

## License

Released under the [MIT License](LICENSE).
