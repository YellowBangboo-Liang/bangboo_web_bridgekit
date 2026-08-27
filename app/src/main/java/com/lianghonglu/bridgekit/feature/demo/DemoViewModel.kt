package com.lianghonglu.bridgekit.feature.demo

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * BridgeKit Demo 的 MVVM 状态持有者。
 *
 * Author: 梁鸿禄
 * ViewModel 不持有 WebView 或 Activity；它只将桥接事件转换为 Compose 可以渲染的不可变状态。
 */
class DemoViewModel : ViewModel() {
    private val mutableUiState = MutableStateFlow(DemoUiState())
    val uiState: StateFlow<DemoUiState> = mutableUiState.asStateFlow()

    fun recordNativeEventSent(method: String) {
        appendLog("Native → JS · $method")
    }

    fun recordNativeEventResult(result: String?) {
        appendLog("JS → Native 回传 · ${result ?: "无返回值"}")
    }

    fun recordSchemeAction(action: String, parameter: String?) {
        appendLog("URL Scheme · jsbridge://$action?param=${parameter.orEmpty()}")
    }

    private fun appendLog(message: String) {
        // JS 注入接口不保证主线程；update 以原子方式避免并发事件丢失日志。
        mutableUiState.update { state ->
            state.copy(logs = (state.logs + message).takeLast(MAX_LOGS))
        }
    }

    private companion object {
        const val MAX_LOGS = 5
    }
}

/** Compose 页面所需的纯展示状态，便于 Preview 和测试直接构造。 */
data class DemoUiState(
    val logs: List<String> = emptyList(),
)
