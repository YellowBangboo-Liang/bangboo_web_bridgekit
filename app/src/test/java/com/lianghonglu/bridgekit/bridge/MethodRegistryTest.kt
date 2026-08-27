package com.lianghonglu.bridgekit.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 原生能力注册表测试。
 *
 * Author: 梁鸿禄
 * 用真实 handler 验证分发行为，避免测试只验证 mock 调用次数。
 */
class MethodRegistryTest {
    @Test
    fun `注册后调用会将参数交给对应 handler`() {
        val registry = MethodRegistry()
        registry.register("echo") { params -> params["text"] }

        assertEquals("你好", registry.handle("echo", mapOf("text" to "你好")))
    }

    @Test
    fun `通知类 handler 可以返回 null`() {
        val registry = MethodRegistry()
        registry.register("event.track") { null }

        assertNull(registry.handle("event.track", emptyMap()))
    }

    @Test(expected = MethodNotFoundException::class)
    fun `未注册方法抛出明确的协议异常`() {
        MethodRegistry().handle("missing", emptyMap())
    }

    @Test
    fun `注销和清空会移除已注册 handler`() {
        val registry = MethodRegistry()
        registry.register("one") { "1" }
        registry.register("two") { "2" }

        registry.unregister("one")
        assertFalse(registry.has("one"))
        assertTrue(registry.has("two"))

        registry.clear()
        assertFalse(registry.has("two"))
    }
}
