package com.example.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.aware.AttachCallback
import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishConfig
import android.net.wifi.aware.PublishDiscoverySession
import android.net.wifi.aware.SubscribeConfig
import android.net.wifi.aware.SubscribeDiscoverySession
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.aware.WifiAwareNetworkSpecifier
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket

data class MeshPeer(
    val id: String,
    val name: String,
    val rssi: Int,
    val fingerprint: String,
    val verificationPin: String,
    val isSimulated: Boolean = false,
    val peerHandle: PeerHandle? = null
)

class WifiAwareMeshManager(private val context: Context) {
    private val tag = "WifiAwareMeshManager"
    private val wifiAwareManager = context.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var wifiAwareSession: android.net.wifi.aware.WifiAwareSession? = null
    private var publishSession: PublishDiscoverySession? = null
    private var subscribeSession: SubscribeDiscoverySession? = null

    private val _peers = MutableStateFlow<List<MeshPeer>>(emptyList())
    val peers: StateFlow<List<MeshPeer>> = _peers.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var activeSocket: Socket? = null
    private var simulationJob: Job? = null

    sealed class ConnectionState {
        object Idle : ConnectionState()
        object Discovering : ConnectionState()
        data class Connected(val socket: Socket, val peer: MeshPeer) : ConnectionState()
        data class Error(val message: String, val isPermissionError: Boolean = false) : ConnectionState()
    }

    fun isWifiAwareSupported(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE) && wifiAwareManager?.isAvailable == true
        } else {
            false
        }
    }

    fun hasRequiredPermissions(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val locationGranted = fineLocation || coarseLocation

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val nearby = ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
            nearby || locationGranted
        } else {
            locationGranted
        }
    }

    fun startDiscoveryAndPublish(deviceName: String) {
        if (isWifiAwareSupported() && wifiAwareManager != null) {
            if (!hasRequiredPermissions()) {
                _connectionState.value = ConnectionState.Error(
                    "Nearby Devices & Location permissions are required to scan for physical Wi-Fi Aware peers.",
                    isPermissionError = true
                )
                return
            }

            _connectionState.value = ConnectionState.Discovering

            try {
                wifiAwareManager.attach(object : AttachCallback() {
                    override fun onAttached(session: android.net.wifi.aware.WifiAwareSession) {
                        wifiAwareSession = session
                        startPublish(deviceName)
                        startSubscribe()
                    }

                    override fun onAttachFailed() {
                        Log.w(tag, "Wi-Fi Aware attach failed. Falling back to simulation mode.")
                        startSimulationMode(deviceName)
                    }
                }, null)
            } catch (e: Exception) {
                Log.e(tag, "Exception attaching Wi-Fi Aware: ${e.message}", e)
                startSimulationMode(deviceName)
            }
        } else {
            // Emulator or device without hardware Wi-Fi Aware: start discovery in loopback simulation mode
            Log.i(tag, "Wi-Fi Aware hardware not available. Using local mesh simulation mode.")
            _connectionState.value = ConnectionState.Discovering
            startSimulationMode(deviceName)
        }
    }

    fun forceSimulationMode(deviceName: String) {
        _connectionState.value = ConnectionState.Discovering
        startSimulationMode(deviceName)
    }

    fun setPermissionDeniedState() {
        _connectionState.value = ConnectionState.Error(
            "Nearby device permissions denied. Tap 'Grant Access' or continue in Demo Mode.",
            isPermissionError = true
        )
    }

    private fun startPublish(deviceName: String) {
        try {
            val config = PublishConfig.Builder()
                .setServiceName("MeshDrop_${deviceName}")
                .build()

            wifiAwareSession?.publish(config, object : DiscoverySessionCallback() {
                override fun onPublishStarted(session: PublishDiscoverySession) {
                    publishSession = session
                }
            }, null)
        } catch (e: Exception) {
            Log.e(tag, "Error starting publish: ${e.message}")
        }
    }

    private fun startSubscribe() {
        try {
            val config = SubscribeConfig.Builder()
                .setServiceName("MeshDrop_")
                .build()

            wifiAwareSession?.subscribe(config, object : DiscoverySessionCallback() {
                override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
                    subscribeSession = session
                }

                override fun onServiceDiscovered(peerHandle: PeerHandle, serviceSpecificInfo: ByteArray?, serviceMatchFilter: List<ByteArray>?) {
                    val peerName = serviceSpecificInfo?.let { String(it) } ?: "Mesh Peer ${peerHandle.hashCode() % 1000}"
                    val peer = MeshPeer(
                        id = peerHandle.hashCode().toString(),
                        name = peerName,
                        rssi = -55,
                        fingerprint = generateFingerprint(peerHandle.hashCode().toString()),
                        verificationPin = generatePin(peerHandle.hashCode().toString()),
                        isSimulated = false,
                        peerHandle = peerHandle
                    )
                    updateDiscoveredPeer(peer)
                }
            }, null)
        } catch (e: Exception) {
            Log.e(tag, "Error starting subscribe: ${e.message}")
        }
    }

    private fun updateDiscoveredPeer(newPeer: MeshPeer) {
        val currentList = _peers.value.toMutableList()
        if (currentList.none { it.id == newPeer.id }) {
            currentList.add(newPeer)
            _peers.value = currentList
        }
    }

    private fun startSimulationMode(deviceName: String) {
        simulationJob?.cancel()
        simulationJob = scope.launch {
            val mockPeers = listOf(
                MeshPeer("sim_1", "Nexus Pixel Pro (Simulator)", -48, "A4-F2-99", "4829", true),
                MeshPeer("sim_2", "Galaxy Tab S9 (Simulator)", -62, "B7-C1-3D", "9173", true)
            )
            _peers.value = mockPeers

            while (true) {
                delay(5000)
                _peers.value = _peers.value.map { peer ->
                    peer.copy(rssi = peer.rssi + ((-3..3).random()))
                }
            }
        }
    }

    fun connectToPeer(peer: MeshPeer) {
        scope.launch {
            if (peer.isSimulated) {
                try {
                    val port = MeshBridgeService.activePort
                    var clientSocket: Socket? = null
                    for (attempt in 1..5) {
                        try {
                            clientSocket = Socket("127.0.0.1", port)
                            break
                        } catch (e: Exception) {
                            delay(200)
                        }
                    }
                    if (clientSocket != null) {
                        activeSocket = clientSocket
                        _connectionState.value = ConnectionState.Connected(clientSocket, peer)
                    } else {
                        _connectionState.value = ConnectionState.Error("MeshBridge local service unavailable")
                    }
                } catch (e: Exception) {
                    _connectionState.value = ConnectionState.Error("Simulation connection failed: ${e.message}")
                }
            } else if (peer.peerHandle != null && wifiAwareSession != null) {
                try {
                    val specifier = WifiAwareNetworkSpecifier.Builder(subscribeSession!!, peer.peerHandle)
                        .setPskPassphrase("MeshDropSecureKey2026")
                        .build()

                    val request = NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
                        .setNetworkSpecifier(specifier)
                        .build()

                    connectivityManager.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val socket = network.socketFactory.createSocket()
                                    socket.connect(InetSocketAddress("192.168.45.1", MeshBridgeService.activePort), 5000)
                                    activeSocket = socket
                                    _connectionState.value = ConnectionState.Connected(socket, peer)
                                } catch (e: Exception) {
                                    _connectionState.value = ConnectionState.Error("Socket connection over NAN failed: ${e.message}")
                                }
                            }
                        }

                        override fun onUnavailable() {
                            _connectionState.value = ConnectionState.Error("Wi-Fi Aware data path unavailable")
                        }
                    })
                } catch (e: Exception) {
                    _connectionState.value = ConnectionState.Error("Failed to request network: ${e.message}")
                }
            }
        }
    }

    fun disconnect() {
        try {
            activeSocket?.close()
            publishSession?.close()
            subscribeSession?.close()
            wifiAwareSession?.close()
        } catch (e: Exception) {
            Log.e(tag, "Error closing connection: ${e.message}")
        }
        activeSocket = null
        publishSession = null
        subscribeSession = null
        wifiAwareSession = null
        simulationJob?.cancel()
        _connectionState.value = ConnectionState.Idle
    }

    private fun generateFingerprint(seed: String): String {
        val hash = seed.hashCode().let { if (it < 0) -it else it }
        val hex = String.format("%06X", hash)
        return "${hex.substring(0, 2)}-${hex.substring(2, 4)}-${hex.substring(4, 6)}"
    }

    private fun generatePin(seed: String): String {
        val hash = seed.hashCode().let { if (it < 0) -it else it }
        return String.format("%04d", hash % 10000)
    }
}
