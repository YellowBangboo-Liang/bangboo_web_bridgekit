package com.lianghonglu.bridgekit.feature.demo

import android.os.Build
import android.util.Base64
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.compose.BackHandler
import com.lianghonglu.bridgekit.bridge.BridgeMethodHandler
import com.lianghonglu.bridgekit.container.BridgeWebView
import com.lianghonglu.bridgekit.ui.theme.BridgeKitTheme
import kotlin.math.abs

/**
 * Compose + MVVM 演示页面。
 *
 * Author: 梁鸿禄
 * Compose 负责声明式界面，ViewModel 负责状态，BridgeWebView 只负责混合容器能力。
 */
@Composable
fun DemoRoute(viewModel: DemoViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var destination by rememberSaveable { mutableStateOf(DemoDestination.Home) }

    when (destination) {
        DemoDestination.Home -> HomeRoute(
            viewModel = viewModel,
            onOpenAbout = { destination = destination.openAbout() },
        )
        DemoDestination.About -> AboutRoute(
            viewModel = viewModel,
            onBack = { destination = destination.backToHome() },
        )
    }
}

/** 首页展示固定高度、无页面滚动的 JSBridge Demo，外层 Compose 保持可纵向滚动。 */
@Composable
private fun HomeRoute(viewModel: DemoViewModel, onOpenAbout: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val webView = rememberDemoWebView(viewModel, DemoDestination.Home.assetPath())
    DisposableEffect(webView, scrollState) {
        webView.enableHomeDemoGestureHandoff { delta -> scrollState.dispatchRawDelta(delta) }
        onDispose { webView.setOnTouchListener(null) }
    }
    DemoScreen(
        uiState = uiState,
        scrollState = scrollState,
        onNativeToJs = {
            viewModel.recordNativeEventSent("native.greeting")
            webView.callJsMethod("native.greeting", mapOf("message" to "Hello from Jetpack Compose")) {
                viewModel.recordNativeEventResult(it.result)
            }
        },
        onOpenAbout = onOpenAbout,
        h5Content = { AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize()) },
    )
}

/** “关于我”才加载完整的 Vue 作品集。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutRoute(viewModel: DemoViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val webView = rememberDemoWebView(viewModel, DemoDestination.About.assetPath())
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("关于我 · 离线作品集") },
            navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            actions = {
                TextButton(onClick = {
                    viewModel.recordNativeEventSent("native.greeting")
                    webView.callJsMethod("native.greeting", mapOf("message" to "Hello from Jetpack Compose")) {
                        viewModel.recordNativeEventResult(it.result)
                    }
                }) { Text("发送事件") }
            },
        )
        AndroidView(factory = { webView }, modifier = Modifier.weight(1f))
        if (uiState.logs.isNotEmpty()) {
            Text(
                text = uiState.logs.last(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 在组合生命周期内持有 WebView，离开对应页面时自动释放。 */
@Composable
private fun rememberDemoWebView(viewModel: DemoViewModel, assetPath: String): BridgeWebView {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val webView = remember(context, assetPath) {
        BridgeWebView(context).apply {
            registerDemoCapabilities(viewModel)
            onSchemeAction = { action, parameter ->
                viewModel.recordSchemeAction(action, parameter)
                post { Toast.makeText(context, "URL Scheme: $action → ${parameter.orEmpty()}", Toast.LENGTH_SHORT).show() }
            }
            loadLocalH5(assetPath)
        }
    }
    DisposableEffect(webView, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webView.onResume()
                Lifecycle.Event.ON_PAUSE -> webView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView.release()
        }
    }
    return webView
}

/** 首页是 BridgeKit 主展示，包含一个不可滚动的双向 JSBridge H5 Demo。 */
@Composable
fun DemoScreen(
    uiState: DemoUiState,
    scrollState: ScrollState,
    onNativeToJs: () -> Unit,
    onOpenAbout: () -> Unit,
    h5Content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("BridgeKit", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Jetpack Compose + MVVM · Android WebView · 双向 JSBridge", color = MaterialTheme.colorScheme.onSurfaceVariant)
        CapabilityGrid()
        Button(onClick = onNativeToJs, modifier = Modifier.fillMaxWidth()) { Text("Native → JS 主动事件") }
        Text("JSBridge H5 演示", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Card(Modifier.fillMaxWidth().height(300.dp)) { h5Content() }
        if (uiState.logs.isNotEmpty()) {
            Text("原生侧事件日志", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    uiState.logs.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) { Text("关于我") }
        Spacer(Modifier.height(12.dp))
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

/** 让首页 H5 卡片保留点击能力，同时把明确的纵向拖动还给外层 Compose 滚动容器。 */
private fun BridgeWebView.enableHomeDemoGestureHandoff(onVerticalDrag: (Float) -> Unit) {
    val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    var downX = 0f
    var downY = 0f
    var lastY = 0f
    var isHomeScroll = false
    setOnTouchListener { _, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastY = event.y
                isHomeScroll = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isHomeScroll && shouldHandOffHomeScroll(event.x - downX, event.y - downY, touchSlop)) {
                    isHomeScroll = true
                }
                if (isHomeScroll) {
                    onVerticalDrag(lastY - event.y)
                    lastY = event.y
                    return@setOnTouchListener true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val consumedByHome = isHomeScroll
                isHomeScroll = false
                return@setOnTouchListener consumedByHome
            }
        }
        false
    }
}

/** 纵向位移超过系统阈值且主导时，视为首页滚动而非 H5 内部操作。 */
internal fun shouldHandOffHomeScroll(deltaX: Float, deltaY: Float, touchSlop: Int): Boolean =
    abs(deltaY) > touchSlop && abs(deltaY) > abs(deltaX)

@Preview(showBackground = true, heightDp = 900)
@Composable private fun DemoScreenPreview() { BridgeKitTheme { DemoScreen(DemoUiState(listOf("Native → JS · native.greeting", "JS → Native 回传 · handled")), rememberScrollState(), {}, {}, { Box(Modifier.fillMaxSize()) }) } }
