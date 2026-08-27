package com.lianghonglu.bridgekit.feature.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Compose 演示页 ViewModel 的状态测试。
 *
 * Author: 梁鸿禄
 */
class DemoViewModelTest {
    @Test
    fun `Native 发送和 H5 回传会按顺序记录到 UI 日志`() {
        val viewModel = DemoViewModel()

        viewModel.recordNativeEventSent("native.greeting")
        viewModel.recordNativeEventResult("handled")

        val logs = viewModel.uiState.value.logs
        assertEquals("Native → JS · native.greeting", logs[0])
        assertEquals("JS → Native 回传 · handled", logs[1])
    }

    @Test
    fun `URL Scheme 分发记录动作和参数`() {
        val viewModel = DemoViewModel()

        viewModel.recordSchemeAction("share", "BridgeKit")

        assertTrue(viewModel.uiState.value.logs.single().contains("jsbridge://share?param=BridgeKit"))
    }
}
