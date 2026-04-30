package com.formykids

import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object WebSocketManager {

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var shouldReconnect = false
    private var reconnectAttempts = 0

    private var idToken: String? = null
    private var serverUrl: String = App.DEFAULT_SERVER_URL
    private var onAuthOk: ((String) -> Unit)? = null
    private var tokenRefresher: (suspend () -> String?)? = null

    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onTextMessage: ((String) -> Unit)? = null
    var onBinaryMessage: ((ByteString) -> Unit)? = null

    fun connectWithAuth(
        serverUrl: String,
        idToken: String,
        tokenRefresher: (suspend () -> String?)? = null,
        onAuthOk: (familyId: String) -> Unit
    ) {
        this.serverUrl = serverUrl
        this.idToken = idToken
        this.tokenRefresher = tokenRefresher
        this.onAuthOk = onAuthOk
        shouldReconnect = true
        reconnectAttempts = 0
        openSocket()
    }

    private fun openSocket() {
        val request = Request.Builder().url(serverUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                reconnectJob?.cancel()
                reconnectAttempts = 0
                val token = idToken
                if (token != null) {
                    ws.send("""{"type":"auth","idToken":"$token"}""")
                }
                onConnected?.invoke()
            }

            override fun onMessage(ws: WebSocket, text: String) {
                val msg = runCatching { JSONObject(text) }.getOrNull()
                if (msg?.optString("type") == "auth_ok") {
                    onAuthOk?.invoke(msg.getString("familyId"))
                }
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
        reconnectAttempts = 0
        reconnectJob?.cancel()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        reconnectJob = scope.launch {
            val delayMs = minOf(1000L shl reconnectAttempts, 30000L)
            reconnectAttempts++
            delay(delayMs)
            if (!shouldReconnect) return@launch
            val fresh = tokenRefresher?.invoke()
            if (fresh != null) idToken = fresh
            openSocket()
        }
    }
}
