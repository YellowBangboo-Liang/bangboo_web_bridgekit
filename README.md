<div align="center">

# BridgeKit

### 从一次 WebView 调用开始，拆解 Android JSBridge 的双向交互

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Android-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Android API](https://img.shields.io/badge/minSdk-24-3DDC84?logo=android&logoColor=white)](https://developer.android.com/about/versions/nougat)
[![License](https://img.shields.io/badge/License-MIT-00A98F.svg)](LICENSE)

**一个面向 Android 与前端开发者的 WebView / JSBridge 技术案例。**<br />
双向通信 · Promise 回调 · 可信离线 H5 · 请求拦截 · 生命周期治理

[快速体验](#运行与验证) · [阅读案例](#先看最终链路) · [代码地图](#代码地图从案例回到工程)

</div>

> [!NOTE]
> 这不是 SDK，也不试图成为通用框架。BridgeKit 的目标是用一条可以跑通的双向调用链，讲清协议、安全与生命周期的工程取舍。

<br />

**作者：** 梁鸿禄 · **定位：** Android Native × Web / Hybrid 交互实践

当一个 Android 页面内嵌 H5 时，真正困难的往往不是“让 JavaScript 调一下 Android”，而是回答这些问题：

- H5 请求 Native 后，为什么不能直接拿到业务返回值？
- 方法名、参数、错误和回调应该怎样约定，才不会越写越乱？
- Native 主动通知 H5 时，如何知道页面是否处理完成？
- `addJavascriptInterface` 放进去以后，外部网页会不会也拿到能力？
- 页面重载、Activity 切后台或销毁时，没完成的回调怎么办？

这个仓库用一个很小的可运行 Demo 回答这些问题。首页包含 H5 发起的“设备信息 / Toast”调用与 Native 主动事件；底部“关于我”是一个独立离线作品集入口。它们共享同一个受控 WebView 容器，但业务能力由宿主页面注册。

| 你会看到什么 | 对应实现 |
| --- | --- |
| JS → Native | JSON 请求、方法白名单、Promise resolve / reject |
| Native → JS | DOM `CustomEvent` 与可选的一次性回调 |
| 可信 WebView 容器 | AssetLoader、导航控制、子资源拦截与 JS 注入 |
| Compose 宿主集成 | 生命周期释放、事件日志与嵌套滚动手势交接 |

## 先看最终链路

```text
H5: await JSBridge.call('device.info')
       │  JSON 请求 + callbackId
       ▼
NativeBridge.call()  (@JavascriptInterface)
       │
       ▼
JsBridge → MethodRegistry → Android 能力处理器
       │  JSON 响应
       ▼
WebView.evaluateJavascript()
       │
       ▼
JSBridge._onCallback() → resolve / reject Promise
```

反方向则是：

```text
Native: bridge.callJs('native.greeting', params, callback)
       │
       ▼
WebView.evaluateJavascript() → JSBridge.onNativeEvent()
       │
       ├─ window.dispatchEvent(new CustomEvent('nativeEvent'))
       └─ NativeBridge.onCallback()（可选的处理结果回传）
```

下面不从 API 清单开始，而是从几个在实际 Hybrid 开发里会遇到的 case 开始。

## Case 1：H5 为什么不能直接调用 Android 方法？

浏览器里的 JavaScript 与 Android Native 是两个不同的运行环境。H5 需要设备信息、登录态、文件选择、分享或支付能力时，不能直接 import Kotlin 类；Native 也不能直接操作网页里的函数。

JSBridge 的作用就是建立一条受控的消息通道：H5 只表达“我要调用哪个能力、附带什么参数”，Native 决定是否提供该能力以及如何执行。这个仓库的入口是 `NativeBridge`：它只暴露 `call(message)` 与 `onCallback(message)` 两个带 `@JavascriptInterface` 的方法，把协议处理交给不依赖 Android UI 的 `JsBridge`。

```kotlin
class NativeBridge(private val bridge: JsBridge) {
    @JavascriptInterface
    fun call(message: String): String = bridge.handleJsCall(message)

    @JavascriptInterface
    fun onCallback(message: String) = bridge.handleJsCallback(message)
}
```

这样做的重点不是“少写一个类”，而是把 WebView 的 Android 适配层变薄。协议解析、方法分发与回调管理可以用普通 JVM 单测验证，而不是只能靠真机点击。

相关代码：[`NativeBridge.kt`](app/src/main/java/com/lianghonglu/bridgekit/bridge/NativeBridge.kt)、[`JsBridge.kt`](app/src/main/java/com/lianghonglu/bridgekit/bridge/JsBridge.kt)。

## Case 2：既然 `@JavascriptInterface` 有返回值，为什么还要回调？

`NativeBridge.call()` 的同步返回值只表示“请求已收到”（`accepted` 或 `rejected`），**不承载业务结果**。真正的结果通过 `callbackId` 回传给 Promise。

原因是 Native 能力天然可能异步：读取权限、打开系统页面、等待网络、切回主线程，或者等用户做完选择。若把协议设计成同步 return，短期 Demo 看似可行，一旦能力变异步，接口语义就会被迫推翻。

H5 侧先生成一个回调 ID，并保存 Promise 的 `resolve/reject`：

```javascript
const id = 'cb_js_' + nextId++;
callbacks[id] = { resolve, reject };
window.NativeBridge.call(JSON.stringify({
  method: 'device.info', params: {}, callbackId: id
}));
```

Native 处理结束后通过 `evaluateJavascript` 调用 `JSBridge._onCallback(...)`。H5 用 `callbackId` 找回并删除对应 Promise；有 `error` 就 reject，否则 resolve。删除这一步很关键：它让每个回调最多消费一次，也避免页面长期累积闭包。

因此业务代码始终是自然的异步写法：

```javascript
try {
  const device = await window.JSBridge.call('device.info');
  console.log(device);
} catch (error) {
  console.error(error.message);
}
```

相关代码：[`BridgeWebView.kt`](app/src/main/java/com/lianghonglu/bridgekit/container/BridgeWebView.kt)、[`CallbackRegistry.kt`](app/src/main/java/com/lianghonglu/bridgekit/bridge/CallbackRegistry.kt)。

## Case 3：接口怎么约定，才不会让 JS 调到任意 Native 代码？

先约定一个很小的 JSON 信封，而不是把整段 JavaScript 或 Kotlin 方法名暴露给另一端：

```jsonc
// JS → Native request
{ "method": "device.info", "params": {}, "callbackId": "cb_js_1" }

// Native → JS response
{ "callbackId": "cb_js_1", "result": "Android 13 · emulator" }

// 出错时
{ "callbackId": "cb_js_1", "error": { "code": "METHOD_NOT_FOUND", "message": "..." } }
```

Native 侧不反射调用任意方法，而是通过 `MethodRegistry` 显式注册白名单能力：

```kotlin
jsBridge.registerMethod("device.info") { _ ->
    "${Build.VERSION.RELEASE} · ${Build.MODEL}"
}
```

找不到方法时统一返回 `METHOD_NOT_FOUND`；处理器发生异常时只返回稳定的 `NATIVE_ERROR`，不把堆栈或业务敏感信息带给 H5。这个约束让接口成为可审计的契约：新增能力必须在 Native 显式注册，参数和错误也有固定形状。

相关代码：[`MethodRegistry.kt`](app/src/main/java/com/lianghonglu/bridgekit/bridge/MethodRegistry.kt)、[`DemoScreen.kt`](app/src/main/java/com/lianghonglu/bridgekit/feature/demo/DemoScreen.kt)。

## Case 4：Native 想主动推送消息给 H5，只有事件够吗？

例如 Android 登录态变化、下载完成或宿主按钮被点击时，Native 需要主动通知页面。这里把消息包装为 `nativeEvent`，由 H5 再分发为标准 DOM `CustomEvent`：

```javascript
window.addEventListener('nativeEvent', event => {
  const { method, params } = event.detail;
  // 例如处理 native.greeting
});
```

这会让业务 H5 不依赖 Android 注入对象；它只监听浏览器事件。若 Native 希望知道该事件是否被页面接受，还可以携带自己的 `callbackId`。`onNativeEvent` 分发后调用 `NativeBridge.onCallback(...)`，Native 的 `CallbackRegistry` 原子地取出并移除一次性回调。

Demo 首页的“Native → JS 主动事件”按钮就是这个 case：点击 Native Compose 按钮，H5 卡片会显示 `native.greeting` 和参数内容。

## Case 5：WebView 为什么不能只 `loadUrl()` 再打开 JavaScript？

因为 `addJavascriptInterface` 会暴露给当前 WebView 可见的 frame。若允许页面随意跳转、加载第三方 iframe 或远程资源，原本只为离线页面准备的 Native 能力就可能扩大暴露面。

本项目的 `BridgeWebView` 做了这几层约束：

1. 使用 `WebViewAssetLoader`，把 APK assets 映射为 HTTPS origin：`https://appassets.androidplatform.net/assets/h5/app/...`。这样既保持同源语义，也能正常使用 Fetch API。
2. `loadLocalH5()` 只接受受控后缀、拒绝 `..`、绝对路径与非法字符；URL 还必须没有 query。
3. `shouldOverrideUrlLoading()` 只放行该可信 host 下的本地路径；其他外跳一律拦截。`jsbridge://` 作为单独的宿主 action 通道处理。
4. `shouldInterceptRequest()` 只交给 AssetLoader 处理可信 assets；其他 HTTP/HTTPS 请求返回空响应，避免远程子资源绕过导航规则。
5. 禁用文件与 Content URI 访问，禁止 mixed content。

这并不是通用安全方案的终点。生产环境还应按业务补充域名白名单、登录态校验、方法级权限、签名校验、离线包完整性校验和敏感操作二次确认。

相关代码：[`BridgeWebView.kt`](app/src/main/java/com/lianghonglu/bridgekit/container/BridgeWebView.kt)。

## Case 6：页面重载或退出时，回调和 WebView 如何收尾？

WebView 的问题常常不是第一次调用，而是第二次。H5 reload 后桥接脚本可能尚未注入；Activity 暂停后 WebView timer 需要暂停；页面销毁时，未回来的 callback 可能还持有 ViewModel 或页面引用。

这个案例里的处理是：

- 每次加载本地 H5 时将 `bridgeReady` 置为 false；Native 事件先进入队列，待 `onPageFinished()` 注入桥接脚本后再依次发送。
- 桥接脚本发出 `bridgeReady` 事件，H5 Demo 在收到前禁用调用按钮，避免注入时机竞态。
- Compose 生命周期同步调用 `WebView.onResume()` / `onPause()`；离开页面时调用 `release()`。
- `release()` 清空 callback 与方法注册、移除 JavaScript interface、停止加载并销毁 WebView。

首页的 H5 卡片并不自己滚动；明确的纵向拖动会转交给外层 Compose `ScrollState`，所以读者可以从 H5 区域持续滑到“关于我”入口。这是一个很小但常见的嵌套手势冲突处理例子。

## 代码地图：从案例回到工程

```text
app/src/main/java/com/lianghonglu/bridgekit/
├── bridge/
│   ├── JsBridge.kt          # JSON 协议、双向转发、错误映射
│   ├── MethodRegistry.kt    # Native 能力白名单
│   ├── CallbackRegistry.kt  # 一次性回调 ID 与原子消费
│   └── NativeBridge.kt      # @JavascriptInterface 薄适配层
├── container/
│   └── BridgeWebView.kt     # WebView 配置、可信资源、安全拦截、JS 注入
└── feature/demo/
    └── DemoScreen.kt        # Compose 宿主、能力注册、首页与“关于我”入口

app/src/main/assets/h5/app/
├── demo.html                # 首页内嵌 JSBridge 交互 Demo
└── index.html               # “关于我”离线 Vue 作品集
```

## 运行与验证

1. 用 Android Studio 打开项目根目录，选择 API 24+ 的模拟器或真机，运行 `app`。
2. 点击首页 H5 的“JS → Native：设备信息”，观察 Promise 返回设备信息。
3. 点击“JS → Native：Toast”，观察 Native Toast。
4. 点击 Compose 的“Native → JS 主动事件”，观察 H5 卡片收到 `native.greeting`。
5. 从 H5 卡片区域向上拖动，确认首页可继续滚动并看到“关于我”。

命令行构建：

```powershell
$env:JAVA_HOME = 'C:\Path\To\JDK-17'
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 这个仓库不是什么

它不是发布到 Maven 的 JSBridge SDK，也没有试图替所有业务抽象一层框架。这里故意保留了小规模和可读性：读者可以从一个具体的 `device.info` 请求一路跟到 Kotlin handler、JSON 回调与 H5 Promise，再看到同一条边界如何处理事件、安全加载和销毁。

如果这套模式要进入真实业务，请把“可调用什么、谁可以调用、何时有效、失败怎么恢复”写成团队协议，而不要只复制几段桥接代码。

## License

Released under the [MIT License](LICENSE).
