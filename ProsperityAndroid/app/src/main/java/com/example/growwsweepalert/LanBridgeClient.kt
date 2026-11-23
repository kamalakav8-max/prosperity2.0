package com.example.growwsweepalert

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/** Receives the authenticated Prosperity LAN bridge SSE stream. */
class LanBridgeClient {
    interface Listener {
        fun onState(state: String)
        fun onData(payload: JSONObject)
    }

    private val running = AtomicBoolean(false)
    @Volatile private var connection: HttpURLConnection? = null
    private val main = Handler(Looper.getMainLooper())
    private var worker: Thread? = null

    fun connect(baseUrl: String, token: String, listener: Listener) {
        close()
        val cleanUrl = baseUrl.trim().trimEnd('/')
        if (!(cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) || token.isBlank()) {
            listener.onState("Enter a valid bridge URL and token")
            return
        }
        running.set(true)
        worker = Thread {
            var delayMs = 1500L
            while (running.get()) {
                try {
                    main.post { listener.onState("Connecting…") }
                    val conn = (URL(cleanUrl + "/v1/stream").openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 8000
                        readTimeout = 30000
                        setRequestProperty("Accept", "text/event-stream")
                        setRequestProperty("Authorization", "Bearer " + token)
                    }
                    connection = conn
                    conn.connect()
                    if (conn.responseCode !in 200..299) {
                        throw IllegalStateException("Bridge HTTP " + conn.responseCode)
                    }
                    delayMs = 1500L
                    main.post { listener.onState("Connected to Windows bridge") }
                    BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
                        var line: String?
                        while (running.get() && reader.readLine().also { line = it } != null) {
                            val value = line ?: continue
                            if (value.startsWith("data:")) {
                                val json = value.removePrefix("data:").trim()
                                if (json.isNotEmpty()) {
                                    val payload = JSONObject(json)
                                    main.post { listener.onData(payload) }
                                }
                            }
                        }
                    }
                    conn.disconnect()
                    connection = null
                    if (running.get()) throw IllegalStateException("Bridge stream ended")
                } catch (e: Exception) {
                    connection?.disconnect()
                    connection = null
                    if (running.get()) {
                        val message = e.message ?: "Connection interrupted"
                        main.post { listener.onState("Disconnected: " + message + " — retrying") }
                        try { Thread.sleep(delayMs) } catch (_: InterruptedException) { }
                        delayMs = (delayMs * 2).coerceAtMost(10000L)
                    }
                }
            }
        }.apply { name = "Prosperity-LAN-Bridge"; isDaemon = true; start() }
    }

    fun close() {
        running.set(false)
        connection?.disconnect()
        connection = null
        worker?.interrupt()
        worker = null
    }
}
