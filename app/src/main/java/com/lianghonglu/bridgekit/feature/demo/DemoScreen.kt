package com.lianghonglu.bridgekit.feature.demo

import android.os.Build
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lianghonglu.bridgekit.bridge.BridgeMethodHandler
import com.lianghonglu.bridgekit.container.BridgeWebView
import com.lianghonglu.bridgekit.ui.theme.BridgeKitTheme

/**
 * Compose + MVVM 演示页面。
 *
 * Author: 梁鸿禄
 * Compose 负责声明式界面，ViewModel 负责状态，BridgeWebView 只负责混合容器能力。
 */
@Composable
fun DemoRoute(viewModel: DemoViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val webView = remember(context) {
        BridgeWebView(context).apply {
            registerDemoCapabilities(viewModel)
            onSchemeAction = { action, parameter ->
                viewModel.recordSchemeAction(action, parameter)
                post { Toast.makeText(context, "URL Scheme: $action → ${parameter.orEmpty()}", Toast.LENGTH_SHORT).show() }
            }
            loadLocalH5()
        }
    }
    DisposableEffect(webView) { onDispose { webView.release() } }
    DemoScreen(uiState, {
        viewModel.recordNativeEventSent("native.greeting")
        webView.callJsMethod("native.greeting", mapOf("message" to "Hello from Jetpack Compose")) {
            viewModel.recordNativeEventResult(it.result)
        }
    }) { AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize()) }
}

/** 可预览的纯 Compose 屏幕；以槽位隔离 Android WebView。 */
@Composable
fun DemoScreen(uiState: DemoUiState, onNativeToJs: () -> Unit, h5Content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("BridgeKit", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Jetpack Compose + MVVM · Android WebView · 双向 JSBridge", color = MaterialTheme.colorScheme.onSurfaceVariant)
        CapabilityGrid()
        Button(onClick = onNativeToJs, modifier = Modifier.fillMaxWidth()) { Text("Native → JS 主动事件（等待 H5 回传）") }
        Text("内置离线 H5 演示", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Card(Modifier.fillMaxWidth().height(390.dp)) { h5Content() }
        if (uiState.logs.isNotEmpty()) {
            Text("原生侧事件日志", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    uiState.logs.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable private fun CapabilityGrid() {
    val abilities = listOf("Promise 双向通信" to "callbackId + 异步回传", "能力注入" to "@JavascriptInterface", "URL Scheme" to "jsbridge:// 分发", "离线 H5" to "assets + 请求拦截", "生命周期" to "释放回调与 WebView", "安全边界" to "仅内置页面可访问接口")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { abilities.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { (title, detail) -> Card(Modifier.weight(1f)) { Column(Modifier.padding(10.dp)) { Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.size(3.dp)); Text(detail, style = MaterialTheme.typography.labelSmall) } } } }
    } }
}

/** 注册 Demo 专用能力；真实项目可移动到独立 capability 模块。 */
private fun BridgeWebView.registerDemoCapabilities(viewModel: DemoViewModel) {
    jsBridge.registerMethod("device.info", BridgeMethodHandler { "Android ${Build.VERSION.RELEASE} · ${Build.MANUFACTURER} ${Build.MODEL}" })
    jsBridge.registerMethod("crypto.base64", BridgeMethodHandler { params -> Base64.encodeToString((params["text"] ?: "").toByteArray(), Base64.NO_WRAP) })
    jsBridge.registerMethod("toast.show", BridgeMethodHandler { params -> post { Toast.makeText(context, params["message"].orEmpty(), Toast.LENGTH_SHORT).show() }; "shown" })
    jsBridge.registerMethod("demo.note", BridgeMethodHandler { params -> viewModel.recordSchemeAction("demo.note", params["message"]); "recorded" })
}

@Preview(showBackground = true, heightDp = 900)
@Composable private fun DemoScreenPreview() { BridgeKitTheme { DemoScreen(DemoUiState(listOf("Native → JS · native.greeting", "JS → Native 回传 · handled")), {}, { Box(Modifier.fillMaxSize().background(Color(0xFFF2F5F6)), contentAlignment = Alignment.Center) { Text("WebView / Assets H5 演示区域") } }) } }
