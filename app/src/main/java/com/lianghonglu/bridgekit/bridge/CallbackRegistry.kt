package com.lianghonglu.bridgekit.bridge

import java.util.concurrent.atomic.AtomicLong

/**
 * Native → JS 异步回调注册表。
 *
 * Author: 梁鸿禄
 * 回调在消费时立即移除，避免同一结果多次触发，也便于容器销毁时统一清理。
 */
class CallbackRegistry {
    private val callbacks = mutableMapOf<String, (BridgeResponse) -> Unit>()
    private val next = AtomicLong(0)

    /** 在单个进程内生成唯一 callbackId；前缀同时让协议日志更易辨识。 */
    fun newId(): String = "cb_${next.getAndIncrement()}"

    fun register(id: String, callback: (BridgeResponse) -> Unit) {
        callbacks[id] = callback
    }

    /**
     * 一次性取出回调。调用者负责执行它，从而可以在需要时切换到 UI 线程。
     */
    fun consume(id: String): ((BridgeResponse) -> Unit)? = callbacks.remove(id)

    fun size(): Int = callbacks.size

    fun clear() {
        callbacks.clear()
    }
}
