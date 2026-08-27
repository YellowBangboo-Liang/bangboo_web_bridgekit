package com.lianghonglu.bridgekit.bridge

import kotlinx.serialization.json.Json

/**
 * 可复用的双向 JSBridge 协调器。
 *
 * Author: 梁鸿禄
 * 该类只处理 JSON 协议、方法分发和回调生命周期；WebView 细节由 container 层适配，
 * 因此可在 JVM 单元测试中验证核心行为。
 */
class JsBridge(
    private val sendToJs: (String) -> Unit,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val methods = MethodRegistry()
    private val callbacks = CallbackRegistry()

    fun registerMethod(method: String, handler: BridgeMethodHandler) {
        methods.register(method, handler)
    }

    fun unregisterMethod(method: String) {
        methods.unregister(method)
    }

    /**
     * 处理 JS 注入接口传来的调用。带 callbackId 的请求始终异步经 sendToJs 回传，
     * 使 JavaScript 端维持 Promise 语义。
     */
    fun handleJsCall(rawMessage: String): String {
        val request = runCatching {
            json.decodeFromString(BridgeRequest.serializer(), rawMessage)
        }.getOrElse { return ACK_REJECTED }

        val response = try {
            BridgeResponse(
                callbackId = request.callbackId,
                result = methods.handle(request.method, request.params),
            )
        } catch (_: MethodNotFoundException) {
            BridgeResponse(
                callbackId = request.callbackId,
                error = BridgeError("METHOD_NOT_FOUND", "No handler registered for ${request.method}"),
            )
        } catch (_: Throwable) {
            // 不把异常堆栈或业务敏感信息泄漏给 H5，仅公开稳定错误码。
            BridgeResponse(
                callbackId = request.callbackId,
                error = BridgeError("NATIVE_ERROR", "Native bridge handler failed"),
            )
        }

        if (request.callbackId != null) {
            sendToJs(json.encodeToString(BridgeResponse.serializer(), response))
        }
        return ACK_ACCEPTED
    }

    /**
     * Native 主动向 JS 发送事件。若提供 callback，则返回可用于日志关联的 callbackId。
     */
    fun callJs(
        method: String,
        params: Map<String, String> = emptyMap(),
        callback: ((BridgeResponse) -> Unit)? = null,
    ): String? {
        val callbackId = callback?.let {
            callbacks.newId().also { id -> callbacks.register(id, it) }
        }
        sendToJs(
            json.encodeToString(
                BridgeResponse.serializer(),
                BridgeResponse(callbackId = callbackId, method = method, params = params),
            ),
        )
        return callbackId
    }

    /** 接收 H5 对 Native 主动调用的回传；无效 JSON 或未知 ID 都安全忽略。 */
    fun handleJsCallback(rawMessage: String) {
        val response = runCatching {
            json.decodeFromString(BridgeResponse.serializer(), rawMessage)
        }.getOrNull() ?: return
        response.callbackId?.let { id -> callbacks.consume(id)?.invoke(response) }
    }

    fun pendingCallbackCount(): Int = callbacks.size()

    /** 容器释放时调用，避免闭包长期持有 Activity / ViewModel。 */
    fun release() {
        callbacks.clear()
        methods.clear()
    }

    companion object {
        const val ACK_ACCEPTED = "accepted"
        const val ACK_REJECTED = "rejected"
    }
}
