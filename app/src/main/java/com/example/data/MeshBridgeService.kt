package com.example.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest

class MeshBridgeService : Service() {
    private val tag = "MeshBridgeService"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    companion object {
        const val CHANNEL_ID = "MeshBridgeChannel"
        const val NOTIFICATION_ID = 1001

        @Volatile
        var activePort: Int = 8888

        private val _transferProgress = MutableStateFlow<TransferProgress>(TransferProgress.Idle)
        val transferProgress: StateFlow<TransferProgress> = _transferProgress.asStateFlow()

        fun updateProgress(progress: TransferProgress) {
            _transferProgress.value = progress
        }
    }

    sealed class TransferProgress {
        object Idle : TransferProgress()
        data class Transferring(val fileName: String, val bytesTransferred: Long, val totalBytes: Long, val speedMBs: Float) : TransferProgress() {
            val percentage: Int
                get() = if (totalBytes > 0) ((bytesTransferred * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
        }
        data class Completed(val message: String) : TransferProgress()
        data class Error(val error: String) : TransferProgress()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("MeshDrop Bridge Active")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (!isRunning) {
            isRunning = true
            startServer()
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MeshDrop Bridge Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MeshDrop P2P Service")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .build()
    }

    private fun startServer() {
        scope.launch {
            try {
                serverSocket = try {
                    ServerSocket(8888).also { activePort = 8888 }
                } catch (e: Exception) {
                    ServerSocket(0).also { activePort = it.localPort }
                }
                Log.i(tag, "MeshBridge Server listening on port $activePort")
                while (isRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    handleClientConnection(clientSocket)
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(tag, "Server error: ${e.message}")
                }
            }
        }
    }

    private fun handleClientConnection(socket: Socket) {
        scope.launch(Dispatchers.IO) {
            try {
                val input = DataInputStream(socket.getInputStream())
                val output = DataOutputStream(socket.getOutputStream())

                while (socket.isConnected && !socket.isClosed) {
                    val frameType = try {
                        input.readByte()
                    } catch (e: EOFException) {
                        break
                    }
                    val payloadLength = input.readLong()
                    if (payloadLength <= 0 || payloadLength > 50 * 1024 * 1024) {
                        Log.w(tag, "Invalid payload length received: $payloadLength")
                        break
                    }

                    val encryptedPayload = ByteArray(payloadLength.toInt())
                    input.readFully(encryptedPayload)

                    try {
                        val payload = P2PSecureChannel.shared.decrypt(encryptedPayload)
                        when (frameType.toInt()) {
                            1 -> { // FILE / PHOTO / APK FRAME
                                handleIncomingFile(payload, output)
                            }
                            3 -> { // ACK
                                Log.d(tag, "Received ACK frame")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(tag, "Security Alert: Decryption failed - unauthorized interception attempt blocked: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.d(tag, "Client connection finished: ${e.message}")
                }
            } finally {
                try {
                    socket.close()
                } catch (ignored: Exception) {}
            }
        }
    }

    private fun handleIncomingFile(payload: ByteArray, output: DataOutputStream) {
        try {
            if (payload.size < 4) {
                updateProgress(TransferProgress.Error("Invalid file payload size"))
                return
            }
            val buffer = java.nio.ByteBuffer.wrap(payload)
            val nameLen = buffer.int
            if (nameLen <= 0 || nameLen > buffer.remaining()) {
                updateProgress(TransferProgress.Error("Corrupted file payload header"))
                return
            }
            val nameBytes = ByteArray(nameLen)
            buffer.get(nameBytes)
            val fileName = String(nameBytes)

            val fileBytes = ByteArray(buffer.remaining())
            buffer.get(fileBytes)

            val targetFile = File(cacheDir, fileName)
            FileOutputStream(targetFile).use { it.write(fileBytes) }

            val sha256 = calculateSha256(targetFile)
            Log.i(tag, "Received file $fileName successfully. SHA-256: $sha256")

            updateProgress(TransferProgress.Completed("Received $fileName successfully (${fileBytes.size} bytes)"))

            if (fileName.endsWith(".apk", ignoreCase = true)) {
                installApk(targetFile)
            }

            try {
                output.writeByte(3)
                output.writeLong(0L)
                output.flush()
            } catch (ignored: Exception) {}
        } catch (e: Exception) {
            updateProgress(TransferProgress.Error("File receive failed: ${e.message}"))
        }
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun installApk(apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val packageInstaller = packageManager.packageInstaller
                val params = android.content.pm.PackageInstaller.SessionParams(
                    android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL
                )
                val sessionId = packageInstaller.createSession(params)
                val session = packageInstaller.openSession(sessionId)

                session.openWrite("package", 0, apkFile.length()).use { out ->
                    FileInputStream(apkFile).use { input ->
                        input.copyTo(out)
                    }
                    session.fsync(out)
                }

                val intent = Intent(this, MainActivity::class.java).let {
                    android.app.PendingIntent.getActivity(this, 0, it, android.app.PendingIntent.FLAG_IMMUTABLE)
                }
                session.commit(intent.intentSender)
                Log.i(tag, "PackageInstaller session committed for zero-click APK installation")
            }
        } catch (e: Exception) {
            Log.e(tag, "APK installation failed: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(tag, "Error closing server socket", e)
        }
    }
}
