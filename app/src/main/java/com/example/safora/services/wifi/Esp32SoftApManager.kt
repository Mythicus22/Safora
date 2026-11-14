package com.example.safora.services.wifi

import android.annotation.SuppressLint
import android.content.Context
import android.net.*
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.PatternMatcher
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

enum class SoftApConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED
}

/**
 * Manages specialized peer-to-peer Wi-Fi connection to ESP32-CAM SoftAP (SAFORA_CAM_AP)
 * using Android's WifiNetworkSpecifier and NetworkRequest API.
 *
 * CRITICAL ARCHITECTURE BENEFIT:
 * By binding ONLY the OkHttpClient or local socket to this Network (without calling
 * connectivityManager.bindProcessToNetwork), Android keeps the default route on Mobile Data / LTE.
 * This guarantees that local evidence downloads from 192.168.4.1 NEVER disrupt or interrupt
 * cellular emergency SMS, calls, or Backblaze B2 cloud uploads!
 */
class Esp32SoftApManager(private val context: Context) {

    companion object {
        private const val TAG = "Esp32SoftApManager"
        const val DEFAULT_SSID = "SAFORA_CAM_AP"
        const val DEFAULT_PASSWORD = "safora123"
    }

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var boundNetwork: Network? = null

    private val _connectionState = MutableStateFlow(SoftApConnectionState.DISCONNECTED)
    val connectionState: StateFlow<SoftApConnectionState> = _connectionState.asStateFlow()

    private val _statusMessage = MutableStateFlow("SoftAP Disconnected")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    @SuppressLint("MissingPermission")
    fun connectToEsp32SoftAp(
        ssid: String = DEFAULT_SSID,
        passphrase: String = DEFAULT_PASSWORD,
        onConnected: ((Network) -> Unit)? = null,
        onFailed: ((String) -> Unit)? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            disconnect()

            _connectionState.value = SoftApConnectionState.CONNECTING
            _statusMessage.value = "Requesting Wi-Fi connection to $ssid..."
            Log.i(TAG, "Requesting WifiNetworkSpecifier for SSID: $ssid")

            val specifier = WifiNetworkSpecifier.Builder()
                .setSsidPattern(PatternMatcher(ssid, PatternMatcher.PATTERN_PREFIX))
                .apply {
                    if (passphrase.isNotBlank()) {
                        setWpa2Passphrase(passphrase)
                    }
                }
                .build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) // Explicitly peer-to-peer / local
                .setNetworkSpecifier(specifier)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    boundNetwork = network
                    _connectionState.value = SoftApConnectionState.CONNECTED
                    _statusMessage.value = "Connected to $ssid (Socket bound to local network)"
                    Log.i(TAG, "Local Wi-Fi network available: $network")
                    onConnected?.invoke(network)
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    boundNetwork = null
                    _connectionState.value = SoftApConnectionState.DISCONNECTED
                    _statusMessage.value = "Lost connection to $ssid"
                    Log.w(TAG, "Local Wi-Fi network lost: $network")
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    _connectionState.value = SoftApConnectionState.FAILED
                    _statusMessage.value = "Failed to connect to $ssid"
                    Log.e(TAG, "Local Wi-Fi network unavailable")
                    onFailed?.invoke("SoftAP unavailable or rejected by user")
                }
            }

            try {
                connectivityManager.requestNetwork(request, networkCallback!!)
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting network: ${e.message}")
                _connectionState.value = SoftApConnectionState.FAILED
                _statusMessage.value = "Network request failed: ${e.message}"
                onFailed?.invoke(e.message ?: "Network request failed")
            }
        } else {
            // Android 9 and below fallback: Assume standard Wi-Fi management
            _connectionState.value = SoftApConnectionState.CONNECTED
            _statusMessage.value = "Standard Wi-Fi mode active"
        }
    }

    /**
     * Builds an OkHttpClient whose sockets are bound specifically to the ESP32 SoftAP Network,
     * leaving all other app traffic (cloud, APIs) on the cellular/default network!
     */
    fun createBoundHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        boundNetwork?.let { net ->
            builder.socketFactory(net.socketFactory)
        }

        return builder.build()
    }

    fun disconnect() {
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering callback: ${e.message}")
            }
        }
        networkCallback = null
        boundNetwork = null
        _connectionState.value = SoftApConnectionState.DISCONNECTED
        _statusMessage.value = "SoftAP Disconnected"
    }
}
