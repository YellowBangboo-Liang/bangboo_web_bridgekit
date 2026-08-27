package com.lianghonglu.bridgekit.container

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.lianghonglu.bridgekit.bridge.BridgeResponse
import com.lianghonglu.bridgekit.bridge.JsBridge
import com.lianghonglu.bridgekit.bridge.NativeBridge
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.ByteArrayInputStream
import java.util.ArrayDeque

/**
 * 可复用且只加载可信内置内容的 WebView / JSBridge 容器。
 *
 * Author: 梁鸿禄
 * 职责：受信任导航、离线 H5 拦截、JS 注入、双向事件转换和释放生命周期。
 * 业务能力必须由外部通过 [jsBridge] 注册，容器本身不含具体业务代码。
 */
class BridgeWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : WebView(context, attrs) {

    /** 接收 jsbridge://action?param=... 的宿主回调，例如展示分享 Toast。 */
    var onSchemeAction: ((action: String, parameter: String?) -> Unit)? = null

    private val json = Json { ignoreUnknownKeys = true }
    private val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()
    // 仅在 WebView UI 线程访问：页面 reload / bootstrap 尚未完成时暂存 Native 事件。
    private val pendingNativeMessages = ArrayDeque<String>()
    private var bridgeReady = false

    /** 供宿主注册 JS → Native 能力，也负责 Native → JS 主动调用。 */
    val jsBridge = JsBridge(::dispatchNativeMessage)

    init {
        configureSettings()
        webViewClient = bridgeClient()
        // 仅加载容器允许的本地 Assets 页面，避免把敏感能力暴露给外部站点。
        addJavascriptInterface(NativeBridge(jsBridge), NATIVE_BRIDGE_NAME)
    }

    /** 加载 `assets/h5/app/` 下的离线应用入口；使用 AssetLoader 的 HTTPS origin 以支持 Fetch API。 */
    fun loadLocalH5(assetPath: String = DEFAULT_ASSET) {
        require(isSafeAssetPath(assetPath)) { "Invalid local asset path" }
        bridgeReady = false
        loadUrl("https://$ASSET_HOST/assets/h5/app/$assetPath")
    }

    /** Native → JS 事件；H5 可监听 nativeEvent，并可通过 callback 得到处理结果。 */
    fun callJsMethod(
        method: String,
        params: Map<String, String> = emptyMap(),
        callback: ((BridgeResponse) -> Unit)? = null,
    ): String? = jsBridge.callJs(method, params, callback)

    override fun onResume() {
        super.onResume()
        resumeTimers()
    }

    override fun onPause() {
        pauseTimers()
        super.onPause()
    }

    /**
     * 宿主页面销毁时调用。清空回调和 JavaScript 接口后再销毁 WebView，减少泄漏机会。
     */
    fun release() {
        jsBridge.release()
        removeJavascriptInterface(NATIVE_BRIDGE_NAME)
        stopLoading()
        loadUrl("about:blank")
        clearHistory()
        removeAllViews()
        destroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureSettings() {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
    }

    private fun bridgeClient(): WebViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url
            return when (url.scheme) {
                SCHEME_BRIDGE -> {
                    url.host?.let { action -> onSchemeAction?.invoke(action, url.getQueryParameter("param")) }
                    true
                }
                "https" -> !(url.host == ASSET_HOST && isSafeLocalUrl(url))
                // Demo 是离线、可信页面，阻止外跳即可保证接口不会被第三方页面使用。
                else -> true
            }
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? {
            val url = request.url
            // addJavascriptInterface 会被所有 frame 看到；阻断远程子资源/iframe，
            // 防止受信任的离线页面意外引入第三方内容后扩大接口暴露面。
            if (url.scheme == "https" && url.host == ASSET_HOST && isSafeLocalUrl(url)) {
                return assetLoader.shouldInterceptRequest(url)
            }
            // 连 favicon 这类浏览器自动请求也返回空数据，避免回退到真实网络。
            return if (url.scheme == "http" || url.scheme == "https") emptyResponse() else null
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            if (url.startsWith("https://$ASSET_HOST/assets/h5/app/")) injectBootstrap()
        }
    }

    /** 注入脚本具有幂等保护；只在受信任的本地页面加载完成后执行。 */
    private fun injectBootstrap() {
        evaluateJavascript(BRIDGE_BOOTSTRAP_JS) {
            bridgeReady = true
            while (pendingNativeMessages.isNotEmpty()) dispatchNow(pendingNativeMessages.removeFirst())
        }
    }

    /** 将 JsBridge 的 JSON 协议转换为固定函数调用，不让不可信内容成为可执行脚本。 */
    private fun dispatchNativeMessage(rawMessage: String) {
        // JsBridge 可被 @JavascriptInterface 线程调用，切回 WebView 的 UI 线程再检查就绪状态。
        post {
            if (!bridgeReady) {
                pendingNativeMessages.addLast(rawMessage)
            } else {
                dispatchNow(rawMessage)
            }
        }
    }

    private fun dispatchNow(rawMessage: String) {
        val response = runCatching {
            json.decodeFromString(BridgeResponse.serializer(), rawMessage)
        }.getOrNull() ?: return
        if (response.method == null) {
            evaluateJavascript(
                "window.JSBridge&&window.JSBridge._onCallback(${quoted(rawMessage)});",
                null,
            )
        } else {
            val parameters = json.encodeToString(response.params)
            evaluateJavascript(
                "window.JSBridge&&window.JSBridge.onNativeEvent(" +
                    "${quoted(response.method)},${quoted(parameters)},${quoted(response.callbackId)});",
                null,
            )
        }
    }

    /** 利用 kotlinx.serialization 输出 JavaScript 可安全接收的 JSON 字符串字面量。 */
    private fun quoted(value: String?): String = json.encodeToString(value ?: "")

    /** 将受信任 Assets URL 映射为相对路径，拒绝 query、编码绕过和目录穿越。 */
    private fun localAssetPath(url: android.net.Uri): String? {
        if (url.host != ASSET_HOST || url.query != null) return null
        val encodedPath = url.encodedPath ?: return null
        if (!encodedPath.startsWith("/assets/h5/app/")) return null
        val path = encodedPath.removePrefix("/assets/h5/app/")
        return path.takeIf(::isSafeAssetPath)
    }

    private fun isSafeLocalUrl(url: android.net.Uri): Boolean = localAssetPath(url) != null

    private fun isSafeAssetPath(path: String): Boolean =
        path.matches(Regex("[A-Za-z0-9._/-]+\\.(html|js|css|json|md|png|jpg|jpeg)")) &&
            !path.contains("..") && !path.startsWith("/")

    private fun emptyResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))

    companion object {
        const val NATIVE_BRIDGE_NAME = "NativeBridge"
        const val SCHEME_BRIDGE = "jsbridge"
        /** AndroidX WebViewAssetLoader 的受信任本地资源域名。 */
        const val ASSET_HOST = "appassets.androidplatform.net"
        const val DEFAULT_ASSET = "index.html"

        /**
         * H5 侧 Promise 包装、Native 事件派发和结果回传。
         * 不使用 Kotlin 字符串模板符号，避免原始字符串意外插值。
         */
        private const val BRIDGE_BOOTSTRAP_JS = """
            (function () {
              if (window.JSBridge) return;
              var callbacks = {};
              var nextId = 1;
              window.JSBridge = {
                call: function (method, params) {
                  return new Promise(function (resolve, reject) {
                    var id = 'cb_js_' + (nextId++);
                    callbacks[id] = { resolve: resolve, reject: reject };
                    window.NativeBridge.call(JSON.stringify({
                      method: method, params: params || {}, callbackId: id
                    }));
                  });
                },
                _onCallback: function (raw) {
                  var message;
                  try { message = JSON.parse(raw); } catch (ignore) { return; }
                  var callback = callbacks[message.callbackId];
                  if (!callback) return;
                  delete callbacks[message.callbackId];
                  if (message.error) callback.reject(new Error(message.error.code + ': ' + message.error.message));
                  else callback.resolve(message.result);
                },
                onNativeEvent: function (method, paramsJson, callbackId) {
                  var params = {};
                  try { params = JSON.parse(paramsJson || '{}'); } catch (ignore) { }
                  var accepted = window.dispatchEvent(new CustomEvent('nativeEvent', {
                    detail: { method: method, params: params }, cancelable: true
                  }));
                  if (callbackId) window.NativeBridge.onCallback(JSON.stringify({
                    callbackId: callbackId, result: accepted ? 'handled' : 'cancelled'
                  }));
                }
              };
              window.dispatchEvent(new Event('bridgeReady'));
            })();
        """
    }
}
