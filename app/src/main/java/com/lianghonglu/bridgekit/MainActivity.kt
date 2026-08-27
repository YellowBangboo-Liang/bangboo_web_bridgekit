package com.lianghonglu.bridgekit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.lianghonglu.bridgekit.ui.theme.BridgeKitTheme

/**
 * BridgeKit 应用入口。
 *
 * Author: 梁鸿禄
 * GitHub demo: 用 Compose 承载可复用的 Android WebView / JSBridge 能力。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BridgeKitTheme {
                // 功能实现后由 DemoRoute 接管；这里保证项目骨架可独立构建。
            }
        }
    }
}
