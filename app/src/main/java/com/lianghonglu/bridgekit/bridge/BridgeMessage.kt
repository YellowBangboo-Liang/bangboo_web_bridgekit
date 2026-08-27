package com.lianghonglu.bridgekit.bridge

import kotlinx.serialization.Serializable

/**
 * JSBridge 统一消息协议。
 *
 * Author: 梁鸿禄
 * 所有模型均不依赖 Android API，便于在 JVM 上稳定验证协议和复用于其他容器。
 */

/** JS 调用 Native 的请求。参数目前限定为字符串，保持示例协议清晰且易于跨端实现。 */
@Serializable
data class BridgeRequest(
    val method: String,
    val params: Map<String, String> = emptyMap(),
    val callbackId: String? = null,
)

/** Native 或 JS 回传的响应，也可承载 Native 主动发起的事件。 */
@Serializable
data class BridgeResponse(
    val callbackId: String? = null,
    val method: String? = null,
    val params: Map<String, String> = emptyMap(),
    val result: String? = null,
    val error: BridgeError? = null,
)

/** 协议级错误，使用稳定字符串代码，避免调用方依赖实现异常文本。 */
@Serializable
data class BridgeError(
    val code: String,
    val message: String,
)
