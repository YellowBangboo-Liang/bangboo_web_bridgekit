package com.lianghonglu.bridgekit.bridge

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 双向 JsBridge 门面测试。
 *
 * Author: 梁鸿禄
 * 不依赖 WebView，直接锁定协议分发与回调行为。
 */
class JsBridgeTest {
    private val json = Json

    @Test
    fun `JS 调用已注册方法会经异步通道回传结果`() {
        val sent = mutableListOf<String>()
        val bridge = JsBridge(sent::add)
        bridge.registerMethod("echo") { it["text"] }

        val acknowledgement = bridge.handleJsCall(
            """{"method":"echo","params":{"text":"hello"},"callbackId":"cb_js"}""",
        )

        assertEquals(JsBridge.ACK_ACCEPTED, acknowledgement)
        val response = json.decodeFromString(BridgeResponse.serializer(), sent.single())
        assertEquals("cb_js", response.callbackId)
        assertEquals("hello", response.result)
        assertNull(response.error)
    }

    @Test
    fun `JS 调用未知方法会收到稳定错误代码`() {
        val sent = mutableListOf<String>()
        val bridge = JsBridge(sent::add)

        bridge.handleJsCall("""{"method":"unknown","callbackId":"cb_js"}""")

        val response = json.decodeFromString(BridgeResponse.serializer(), sent.single())
        assertEquals("METHOD_NOT_FOUND", response.error?.code)
    }

    @Test
    fun `Native 调用 JS 会登记回调并在 JS 回传后消费`() {
        val sent = mutableListOf<String>()
        val bridge = JsBridge(sent::add)
        var result: String? = null

        val callbackId = bridge.callJs("native.greet", mapOf("greeting" to "你好")) { response ->
            result = response.result
        }

        assertNotNull(callbackId)
        val event = json.decodeFromString(BridgeResponse.serializer(), sent.single())
        assertEquals("native.greet", event.method)
        assertEquals("你好", event.params["greeting"])
        assertEquals(callbackId, event.callbackId)

        bridge.handleJsCallback("""{"callbackId":"$callbackId","result":"handled"}""")

        assertEquals("handled", result)
        assertEquals(0, bridge.pendingCallbackCount())
    }

    @Test
    fun `格式错误的 JS 消息不会抛出并返回拒绝确认`() {
        val bridge = JsBridge(sendToJs = { })

        assertEquals(JsBridge.ACK_REJECTED, bridge.handleJsCall("not json"))
        assertTrue(bridge.pendingCallbackCount() == 0)
    }
}
