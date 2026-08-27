package com.lianghonglu.bridgekit.bridge

import android.webkit.JavascriptInterface

/**
 * 注入到 window.NativeBridge 的极薄 Android 适配器。
 *
 * Author: 梁鸿禄
 * 此类不承载业务逻辑；所有协议处理交由 [JsBridge]，方便测试并减少 WebView 耦合。
 */
class NativeBridge(private val bridge: JsBridge) {
    /** JS → Native：传入 JSON 请求，返回接收确认。Promise 的最终结果走异步回调。 */
    @JavascriptInterface
    fun call(message: String): String = bridge.handleJsCall(message)

    /** JS → Native：接收对 Native 主动调用的 JSON 回传。 */
    @JavascriptInterface
    fun onCallback(message: String) {
        bridge.handleJsCallback(message)
    }
}
