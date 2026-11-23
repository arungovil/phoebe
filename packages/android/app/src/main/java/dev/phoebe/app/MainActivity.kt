package dev.phoebe.app

import android.app.Activity
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

@Suppress("DEPRECATION")
class MainActivity : Activity() {
    private val client = OkHttpClient()
    private var socket: WebSocket? = null
    private lateinit var nsd: NsdManager
    private var discovery: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var hostUrl: String? = null
    private var hostName: String? = null
    private var resolving = false
    private var generation = 0
    private lateinit var status: TextView
    private lateinit var message: TextView
    private lateinit var connectButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nsd = getSystemService(Context.NSD_SERVICE) as NsdManager
        status = TextView(this).apply { text = "Looking for a Phoebe host on your network..." }
        message = TextView(this).apply { text = "No messages yet" }
        connectButton = Button(this).apply {
            text = "Connect"
            isEnabled = false
            setOnClickListener { connect() }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            addView(status)
            addView(connectButton)
            addView(message)
        })
    }

    override fun onStart() {
        super.onStart()
        startDiscovery()
    }

    private fun startDiscovery() {
        val currentGeneration = ++generation
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("phoebe-discovery").apply { acquire() }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onServiceFound(service: NsdServiceInfo) {
                runOnUiThread {
                    if (generation != currentGeneration || hostUrl != null || resolving) return@runOnUiThread
                    resolving = true
                    nsd.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                            runOnUiThread { if (generation == currentGeneration) resolving = false }
                        }
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            runOnUiThread {
                                if (generation != currentGeneration) return@runOnUiThread
                                resolving = false
                                val address = info.host?.hostAddress ?: return@runOnUiThread
                                val host = if (address.contains(":")) "[$address]" else address
                                hostUrl = "ws://$host:${info.port}/ws"
                                hostName = info.serviceName
                                if (socket == null) {
                                    status.text = "Phoebe host found. Ready to connect."
                                    connectButton.isEnabled = true
                                }
                            }
                        }
                    })
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                runOnUiThread {
                    if (generation != currentGeneration || service.serviceName != hostName) return@runOnUiThread
                    hostUrl = null
                    hostName = null
                    connectButton.isEnabled = false
                    if (socket == null) status.text = "Host not found. Looking on your network..."
                }
            }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                runOnUiThread {
                    if (generation == currentGeneration) status.text = "Could not search for a host (error $errorCode). Reopen the app to retry."
                }
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
        }
        discovery = listener
        try {
            nsd.discoverServices("_phoebe._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: RuntimeException) {
            status.text = "Could not search for a host. Reopen the app to retry."
        }
    }

    private fun connect() {
        val url = hostUrl ?: return
        connectButton.isEnabled = false
        status.text = "Connecting..."
        socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                runOnUiThread { if (socket === webSocket) status.text = "Connected" }
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                runOnUiThread { if (socket === webSocket) message.text = text }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                disconnected(webSocket, "Connection failed. Looking for host...")
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                disconnected(webSocket, "Disconnected. Looking for host...")
            }
        })
    }

    private fun disconnected(webSocket: WebSocket, text: String) {
        runOnUiThread {
            if (socket !== webSocket) return@runOnUiThread
            socket = null
            status.text = if (hostUrl == null) text else "Disconnected. Host found; tap Connect to retry."
            connectButton.isEnabled = hostUrl != null
        }
    }

    override fun onStop() {
        generation++
        socket?.cancel()
        socket = null
        hostUrl = null
        hostName = null
        resolving = false
        connectButton.isEnabled = false
        status.text = "Looking for a Phoebe host on your network..."
        discovery?.let { try { nsd.stopServiceDiscovery(it) } catch (_: IllegalArgumentException) {} }
        discovery = null
        multicastLock?.let { if (it.isHeld) it.release() }
        multicastLock = null
        super.onStop()
    }

    override fun onDestroy() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        super.onDestroy()
    }
}
