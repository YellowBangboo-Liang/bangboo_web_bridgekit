package com.lianghonglu.bridgekit.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Native 主动调用 JS 时的回调登记测试。
 *
 * Author: 梁鸿禄
 */
class CallbackRegistryTest {
    @Test
    fun `生成的回调标识唯一且使用 cb 前缀`() {
        val registry = CallbackRegistry()
        val ids = (1..100).map { registry.newId() }

        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.startsWith("cb_") })
    }

    @Test
    fun `消费回调后仅执行一次并从待处理集合移除`() {
        val registry = CallbackRegistry()
        val id = registry.newId()
        var received: BridgeResponse? = null
        registry.register(id) { received = it }

        registry.consume(id)?.invoke(BridgeResponse(callbackId = id, result = "handled"))

        assertEquals("handled", received?.result)
        assertNull(registry.consume(id))
        assertEquals(0, registry.size())
    }

    @Test
    fun `clear 移除全部尚未回传的回调`() {
        val registry = CallbackRegistry()
        registry.register(registry.newId()) { }
        registry.register(registry.newId()) { }

        registry.clear()

        assertEquals(0, registry.size())
    }
}
