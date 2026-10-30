package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MeshBridgeService
import com.example.data.MeshPeer
import com.example.data.P2PSecureChannel
import com.example.data.WifiAwareMeshManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.DataOutputStream
import java.net.Socket

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val meshManager = WifiAwareMeshManager(application)

    val peers: StateFlow<List<MeshPeer>> = meshManager.peers
    val connectionState: StateFlow<WifiAwareMeshManager.ConnectionState> = meshManager.connectionState
    val transferProgress: StateFlow<MeshBridgeService.TransferProgress> = MeshBridgeService.transferProgress

    private val _isPinVerified = MutableStateFlow(false)
    val isPinVerified: StateFlow<Boolean> = _isPinVerified.asStateFlow()

    private val _selectedPeerForTrust = MutableStateFlow<MeshPeer?>(null)
    val selectedPeerForTrust: StateFlow<MeshPeer?> = _selectedPeerForTrust.asStateFlow()

    init {
        startDiscovery("AndroidDevice_${(1000..9999).random()}")
    }

    fun startDiscovery(deviceName: String) {
        meshManager.startDiscoveryAndPublish(deviceName)
    }

    fun hasRequiredPermissions(): Boolean = meshManager.hasRequiredPermissions()

    fun onPermissionsResult(granted: Boolean) {
        if (granted || hasRequiredPermissions()) {
            startDiscovery("AndroidDevice_${(1000..9999).random()}")
        } else if (!meshManager.isWifiAwareSupported()) {
            meshManager.forceSimulationMode("AndroidDevice_${(1000..9999).random()}")
        } else {
            meshManager.setPermissionDeniedState()
        }
    }

    fun startSimulationMode() {
        meshManager.forceSimulationMode("AndroidDevice_${(1000..9999).random()}")
    }

    fun retryDiscovery() {
        startDiscovery("AndroidDevice_${(1000..9999).random()}")
    }

    fun onPeerClicked(peer: MeshPeer) {
        _selectedPeerForTrust.value = peer
    }

    fun dismissTrustDialog() {
        _selectedPeerForTrust.value = null
    }

    fun confirmAndConnect(peer: MeshPeer, autoVerify: Boolean = true) {
        _selectedPeerForTrust.value = null
        _isPinVerified.value = autoVerify
        meshManager.connectToPeer(peer)
    }

    fun confirmPinMatch() {
        _isPinVerified.value = true
    }

    fun verifyPin(enteredPin: String, expectedPin: String): Boolean {
        val matches = enteredPin.trim() == expectedPin.trim()
        if (matches) {
            _isPinVerified.value = true
        }
        return matches
    }

    fun disconnect() {
        _isPinVerified.value = false
        meshManager.disconnect()
    }

    fun sendFile(uri: Uri, socket: Socket) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (socket.isClosed || !socket.isConnected) {
                    MeshBridgeService.updateProgress(MeshBridgeService.TransferProgress.Error("Connection lost or socket closed"))
                    return@launch
                }
                val context = getApplication<Application>()
                val contentResolver = context.contentResolver
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    MeshBridgeService.updateProgress(MeshBridgeService.TransferProgress.Error("Unable to read selected file"))
                    return@launch
                }

                var fileName = "shared_file.bin"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        fileName = cursor.getString(nameIndex)
                    }
                }

                val fileBytes = inputStream.readBytes()
                inputStream.close()

                val nameBytes = fileName.toByteArray()
                val payloadBuffer = java.nio.ByteBuffer.allocate(4 + nameBytes.size + fileBytes.size)
                payloadBuffer.putInt(nameBytes.size)
                payloadBuffer.put(nameBytes)
                payloadBuffer.put(fileBytes)
                val rawPayload = payloadBuffer.array()

                val encryptedPayload = P2PSecureChannel.shared.encrypt(rawPayload)
                val totalEncryptedBytes = encryptedPayload.size.toLong()

                MeshBridgeService.updateProgress(MeshBridgeService.TransferProgress.Transferring(fileName, 0L, totalEncryptedBytes, 0f))

                val output = DataOutputStream(socket.getOutputStream())
                output.writeByte(1)
                output.writeLong(totalEncryptedBytes)

                val chunkSize = 16384
                var sentBytes = 0L
                val startTime = System.currentTimeMillis()

                val inputStreamEncrypted = java.io.ByteArrayInputStream(encryptedPayload)
                val buffer = ByteArray(chunkSize)
                var read: Int
                while (inputStreamEncrypted.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    sentBytes += read
                    val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
                    val speed = (sentBytes.toFloat() / (1024 * 1024)) / (elapsed / 1000f)
                    MeshBridgeService.updateProgress(MeshBridgeService.TransferProgress.Transferring(fileName, sentBytes, totalEncryptedBytes, speed))
                }
                output.flush()

                MeshBridgeService.updateProgress(MeshBridgeService.TransferProgress.Completed("Encrypted & Sent $fileName successfully (100%)"))
            } catch (e: Exception) {
                MeshBridgeService.updateProgress(MeshBridgeService.TransferProgress.Error("Send failed: ${e.message}"))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (MeshBridgeService.transferProgress.value !is MeshBridgeService.TransferProgress.Transferring) {
            meshManager.disconnect()
        }
    }
}
