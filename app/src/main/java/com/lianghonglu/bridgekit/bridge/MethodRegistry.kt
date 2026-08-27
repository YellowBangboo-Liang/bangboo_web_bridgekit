package com.lianghonglu.bridgekit.bridge

/**
 * JSBridge 原生能力注册表。
 *
 * Author: 梁鸿禄
 * 将业务能力与 WebView/JavaScript 隔离，调用方只需按方法名注册纯 Kotlin handler。
 */

/** 调用不存在的公开 bridge 方法时抛出，用于统一映射为协议错误。 */
class MethodNotFoundException(method: String) : Exception("Bridge method not found: $method")

/** 原生能力处理器；返回 null 表示该能力仅通知、不需要数据结果。 */
fun interface BridgeMethodHandler {
    fun handle(params: Map<String, String>): String?
}

/**
 * 不持有 Android Context 的方法注册中心。
 *
 * 业务层可在 Activity、Fragment 或 ViewModel 所管理的协调器中注册方法；容器仅负责转发。
 */
class MethodRegistry {
    private val handlers = mutableMapOf<String, BridgeMethodHandler>()

    fun register(method: String, handler: BridgeMethodHandler) {
        handlers[method] = handler
    }

    fun unregister(method: String) {
        handlers.remove(method)
    }

    fun has(method: String): Boolean = handlers.containsKey(method)

    fun handle(method: String, params: Map<String, String>): String? {
        val handler = handlers[method] ?: throw MethodNotFoundException(method)
        return handler.handle(params)
    }

    fun clear() {
        handlers.clear()
    }
}
