package com.organizeur.app.camera

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.organizeur.app.MainActivity
import com.organizeur.app.R
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class MjpegStreamingService : LifecycleService() {

    private var mjpegServer: MjpegServer? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private var currentLensFacing = CameraSelector.LENS_FACING_BACK
    private var currentResolution = Size(1280, 720)
    private var currentQuality = 80
    private var previewView: PreviewView? = null

    @Volatile
    var isStreaming = false
        private set

    val clientCount: Int get() = mjpegServer?.clientCount ?: 0

    inner class LocalBinder : Binder() {
        val service: MjpegStreamingService get() = this@MjpegStreamingService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_STOP) {
            stopStreaming()
            stopSelf()
            return START_NOT_STICKY
        }

        val port = intent?.getIntExtra(EXTRA_PORT, CameraManager.DEFAULT_PORT) ?: CameraManager.DEFAULT_PORT
        val quality = intent?.getIntExtra(EXTRA_QUALITY, CameraManager.DEFAULT_QUALITY) ?: CameraManager.DEFAULT_QUALITY
        val lensFacing = intent?.getIntExtra(EXTRA_LENS_FACING, CameraSelector.LENS_FACING_BACK) ?: CameraSelector.LENS_FACING_BACK
        val resIndex = intent?.getIntExtra(EXTRA_RESOLUTION, CameraManager.DEFAULT_RESOLUTION_INDEX) ?: CameraManager.DEFAULT_RESOLUTION_INDEX

        val res = CameraManager.RESOLUTIONS.getOrElse(resIndex) { Pair(1280, 720) }
        currentResolution = Size(res.first, res.second)
        currentQuality = quality
        currentLensFacing = lensFacing

        createNotificationChannel()
        val notification = buildNotification(port)
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)

        startStreaming(port)

        return START_NOT_STICKY
    }

    fun setPreviewView(view: PreviewView?) {
        previewView = view
        if (isStreaming && view != null) {
            preview?.setSurfaceProvider(view.surfaceProvider)
        }
    }

    fun switchCamera(lensFacing: Int) {
        currentLensFacing = lensFacing
        if (isStreaming) {
            bindCamera()
        }
    }

    private fun startStreaming(port: Int) {
        if (isStreaming) return
        isStreaming = true

        mjpegServer = MjpegServer(port).also { it.start() }
        bindCamera()
    }

    fun stopStreaming() {
        isStreaming = false
        cameraProvider?.unbindAll()
        mjpegServer?.stop()
        mjpegServer = null
    }

    private fun bindCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            cameraProvider?.unbindAll()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(currentLensFacing)
                .build()

            preview = Preview.Builder().build().also {
                previewView?.let { pv -> it.setSurfaceProvider(pv.surfaceProvider) }
            }

            imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(currentResolution)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()

            imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
                try {
                    val nv21 = imageProxyToNv21(imageProxy)

                    val yuvImage = YuvImage(
                        nv21,
                        ImageFormat.NV21,
                        imageProxy.width,
                        imageProxy.height,
                        null
                    )

                    val outputStream = ByteArrayOutputStream()
                    yuvImage.compressToJpeg(
                        Rect(0, 0, imageProxy.width, imageProxy.height),
                        currentQuality,
                        outputStream
                    )

                    mjpegServer?.sendFrame(outputStream.toByteArray())
                } catch (e: Exception) {
                    Log.e(TAG, "Frame processing error", e)
                } finally {
                    imageProxy.close()
                }
            }

            try {
                cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind error", e)
            }
        }, mainExecutor)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Caméra réseau",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notification du streaming caméra"
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(port: Int): Notification {
        val ip = NetworkUtils.getWifiIpAddress(this) ?: "???"
        val url = "http://$ip:$port/"

        val stopIntent = Intent(this, MjpegStreamingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.camera_notification_title))
            .setContentText(getString(R.string.camera_notification_text, url))
            .setContentIntent(openPending)
            .addAction(0, getString(R.string.camera_notification_stop), stopPending)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopStreaming()
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    private fun imageProxyToNv21(imageProxy: ImageProxy): ByteArray {
        val width = imageProxy.width
        val height = imageProxy.height
        val yPlane = imageProxy.planes[0]
        val uPlane = imageProxy.planes[1]
        val vPlane = imageProxy.planes[2]
        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        val nv21 = ByteArray(width * height * 3 / 2)

        // Copy Y plane row by row (rowStride may differ from width)
        val yBuffer = yPlane.buffer
        if (yRowStride == width) {
            yBuffer.get(nv21, 0, width * height)
        } else {
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                yBuffer.get(nv21, row * width, width)
            }
        }

        // Copy UV planes as NV21 (VU interleaved)
        // Always use absolute get per pixel — avoids BufferUnderflowException
        // on last UV row where remaining bytes = width - 1
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        var pos = width * height

        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val uvIndex = row * uvRowStride + col * uvPixelStride
                nv21[pos++] = vBuffer.get(uvIndex)
                nv21[pos++] = uBuffer.get(uvIndex)
            }
        }

        return nv21
    }

    companion object {
        private const val TAG = "MjpegStreamingService"
        private const val CHANNEL_ID = "camera_streaming"
        private const val NOTIFICATION_ID = 2001
        const val ACTION_STOP = "com.organizeur.app.camera.STOP"
        const val EXTRA_PORT = "port"
        const val EXTRA_QUALITY = "quality"
        const val EXTRA_LENS_FACING = "lens_facing"
        const val EXTRA_RESOLUTION = "resolution_index"
    }
}
