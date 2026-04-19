package com.formykids

import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit

object WebSocketManager {

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var shouldReconnect = false

    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onTextMessage: ((String) -> Unit)? = null
    var onBinaryMessage: ((ByteString) -> Unit)? = null

    fun connect() {
        shouldReconnect = true
        openSocket()
    }

    private fun openSocket() {
        val request = Request.Builder().url(App.SERVER_URL).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                reconnectJob?.cancel()
                onConnected?.invoke()
            }
            override fun onMessage(ws: WebSocket, text: String) {
                onTextMessage?.invoke(text)
            }
            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                onBinaryMessage?.invoke(bytes)
            }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                onDisconnected?.invoke()
                scheduleReconnect()
            }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                onDisconnected?.invoke()
                scheduleReconnect()
            }
        })
    }

    fun send(text: String): Boolean = webSocket?.send(text) ?: false

    fun send(bytes: ByteArray): Boolean =
        webSocket?.send(bytes.toByteString()) ?: false

    fun disconnect() {
        shouldReconnect = false
        reconnectJob?.cancel()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        reconnectJob = scope.launch {
            delay(3000)
            if (shouldReconnect) openSocket()
        }
    }
}
