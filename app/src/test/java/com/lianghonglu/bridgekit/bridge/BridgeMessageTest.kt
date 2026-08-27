package com.lianghonglu.bridgekit.bridge

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Bridge 消息协议的 JVM 单元测试。
 *
 * Author: 梁鸿禄
 * 这些测试固定 JS 与 Native 之间最小、可公开说明的 JSON 契约。
 */
class BridgeMessageTest {
    private val json = Json

    @Test
    fun `请求序列化往返后保留方法 参数和回调标识`() {
        val request = BridgeRequest(
            method = "device.info",
            params = mapOf("source" to "demo"),
            callbackId = "cb_1",
        )

        val decoded = json.decodeFromString(
            BridgeRequest.serializer(),
            json.encodeToString(BridgeRequest.serializer(), request),
        )

        assertEquals("device.info", decoded.method)
        assertEquals("demo", decoded.params["source"])
        assertEquals("cb_1", decoded.callbackId)
    }

    @Test
    fun `缺省参数会解码为空映射 缺省回调为 null`() {
        val decoded = json.decodeFromString(BridgeRequest.serializer(), """{"method":"toast.show"}""")

        assertEquals(emptyMap<String, String>(), decoded.params)
        assertNull(decoded.callbackId)
    }

    @Test
    fun `错误响应可安全地往返序列化`() {
        val response = BridgeResponse(
            callbackId = "cb_7",
            error = BridgeError(code = "METHOD_NOT_FOUND", message = "No handler registered"),
        )

        val decoded = json.decodeFromString(
            BridgeResponse.serializer(),
            json.encodeToString(BridgeResponse.serializer(), response),
        )

        assertEquals("cb_7", decoded.callbackId)
        assertEquals("METHOD_NOT_FOUND", decoded.error?.code)
        assertEquals("No handler registered", decoded.error?.message)
        assertNull(decoded.result)
    }
}
