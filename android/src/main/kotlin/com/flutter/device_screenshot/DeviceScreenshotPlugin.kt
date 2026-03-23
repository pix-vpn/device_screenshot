package com.flutter.device_screenshot

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.flutter.device_screenshot.src.MediaProjectionService
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceScreenshotPlugin : FlutterPlugin, MethodCallHandler, ActivityAware,
    PluginRegistry.ActivityResultListener {
    private var context: Context? = null
    private lateinit var channel: MethodChannel
    private var activity: Activity? = null
    private val requestCodeForegroundService = 145758
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var activityBinding: ActivityPluginBinding? = null
    private val screenshotLock = Any()
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            sharedMediaProjection = null
            mediaProjection = null
            Log.w("MediaProjection", "Authorization stopped by user or system")
        }
    }

    companion object {
        @Volatile
        var sharedMediaProjection: MediaProjection? = null
    }


    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "device_screenshot")
        channel.setMethodCallHandler(this)
        context = flutterPluginBinding.applicationContext

        mediaProjectionManager =
            context?.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {

            "checkMediaProjectionService" -> {
                val isRunning = isServiceRunning(context!!, MediaProjectionService::class.java)
                val mps = mediaProjection.hashCode()
                Log.d("MPS", "mps is $mps")
                result.success(isRunning)
            }

            "stopMediaProjectionService" -> {
                val stopIntent = Intent(context, MediaProjectionService::class.java)
                stopIntent.action = MediaProjectionService.ACTION_STOP_SERVICE
                activity?.startService(stopIntent)
            }

            "requestMediaProjection" -> {
                requestMediaProjection()
            }

            "takeScreenshot" -> {
                captureScreen(object : ImageAndUriAvailableCallback {
                    override fun onImageAndUriAvailable(uri: Uri?) {
                        if (uri != null) {
                            result.success(uri.path)
                        } else {
                            result.notImplemented()
                        }
                    }
                })
            }

            "take" -> {
                val width: Int? = call.argument("width")
                val height: Int? = call.argument("height")
                if (height != null) {
                    if (width != null) {
                        captureScreen1(width,height,object : ImageAndUriAvailableCallback {
                            override fun onImageAndUriAvailable(uri: Uri?) {
                                if (uri != null) {
                                    result.success(uri.path)
                                } else {
                                    result.notImplemented()
                                }
                            }
                        })
                    }
                }
            }

            "takeScreenshotAsBytes" -> {
                captureScreenAsBytes(object : ImageBytesAvailableCallback {
                    override fun onImageBytesAvailable(bytes: ByteArray?) {
                        if (bytes != null) {
                            result.success(bytes)
                        } else {
                            result.notImplemented()
                        }
                    }
                })
            }

            "takeScreenshotInBackground" -> {
                takeScreenshotInBackground(object : ImageBytesAvailableCallback {
                    override fun onImageBytesAvailable(bytes: ByteArray?) {
                        if (bytes != null) {
                            result.success(bytes)
                        } else {
                            result.error("UNAVAILABLE", "MediaProjection not initialized. Please request permission first in foreground.", null)
                        }
                    }
                })
            }

            else -> {
                result.notImplemented()
            }
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    interface ImageAndUriAvailableCallback {
        fun onImageAndUriAvailable(uri: Uri?)
    }

    interface ImageBytesAvailableCallback {
        fun onImageBytesAvailable(bytes: ByteArray?)
    }

    @SuppressLint("WrongConstant")
    private fun captureScreen(callback: ImageAndUriAvailableCallback) {
        try {
            val metrics = DisplayMetrics()
            val windowManager = context?.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            val density = metrics.densityDpi
            val width = metrics.widthPixels
            val height = metrics.heightPixels

            val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

            val virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                null
            )

            var singleTimeComplete = false
            imageReader.setOnImageAvailableListener(
                { reader ->
                    if (!singleTimeComplete) {
                        singleTimeComplete = true
                        val image = reader.acquireLatestImage()
                        val bitmap = image?.toBitmap()

                        val dateFormat =
                            SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())
                        val currentTimeStamp = dateFormat.format(Date())
                        val imageName = "screenshot_${currentTimeStamp}_"

                        val tempFile = File.createTempFile(imageName, ".png")

                        val bytes = ByteArrayOutputStream()
                        bitmap?.compress(Bitmap.CompressFormat.PNG, 100, bytes)
                        val bitmapData = bytes.toByteArray()

                        val fileOutPut = FileOutputStream(tempFile)
                        fileOutPut.write(bitmapData)
                        fileOutPut.flush()
                        fileOutPut.close()
                        val uri = Uri.fromFile(tempFile)
                        callback.onImageAndUriAvailable(uri)

                        image?.close()
                        virtualDisplay?.release()
//                        mediaProjection?.stop()
                    }
                },
                null,
            )
        } catch (ex: Throwable) {
            Log.e("take screenshot", "take screenshot error!")
        }
    }


    @SuppressLint("WrongConstant")
    private fun captureScreen1(width: Int,height: Int,callback: ImageAndUriAvailableCallback) {
        try {
            val metrics = DisplayMetrics()
            val windowManager = context?.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            val density = metrics.densityDpi
            val width = width
            val height = height

            val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

            val virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                null
            )


            var singleTimeComplete = false
            imageReader.setOnImageAvailableListener(
                { reader ->
                    if (!singleTimeComplete) {
                        singleTimeComplete = true
                        val image = reader.acquireLatestImage()
                        val bitmap = image?.toBitmap()

                        val imageName = "screenshot"

                        val tempFile = File.createTempFile(imageName, ".png")
                        if(tempFile.exists()){
                            tempFile.delete();
                        }
                        val bytes = ByteArrayOutputStream()
                        bitmap?.compress(Bitmap.CompressFormat.PNG, 75, bytes)
                        val bitmapData = bytes.toByteArray()

                        val fileOutPut = FileOutputStream(tempFile)
                        fileOutPut.write(bitmapData)
                        fileOutPut.flush()
                        fileOutPut.close()
                        val uri = Uri.fromFile(tempFile)
                        callback.onImageAndUriAvailable(uri)

                        image?.close()
                        virtualDisplay?.release()
//                        mediaProjection?.stop()
                    }
                },
                null,
            )
        } catch (ex: Throwable) {
            ex.printStackTrace();
            Log.e("take screenshot", "take screenshot error!")
        }
    }

    private fun Image.toBitmap(): Bitmap {
        val buffer: ByteBuffer = planes[0].buffer
        val rowStride: Int = planes[0].rowStride
        val pixelStride: Int = planes[0].pixelStride
        val rowPadding: Int = rowStride - pixelStride * width
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        
        if (rowPadding == 0) {
            bitmap.copyPixelsFromBuffer(buffer)
        } else {
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val position = y * rowStride + x * pixelStride
                    buffer.position(position)
                    val r = buffer.get().toInt() and 0xFF
                    val g = buffer.get().toInt() and 0xFF
                    val b = buffer.get().toInt() and 0xFF
                    val a = 0xFF
                    pixels[y * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        }
        return bitmap
    }

    @SuppressLint("WrongConstant")
    private fun captureScreenAsBytes(callback: ImageBytesAvailableCallback) {
        captureScreenAsBytesWithProjection(mediaProjection, callback)
    }

    @SuppressLint("WrongConstant")
    private fun captureScreenAsBytesWithProjection(
        projection: MediaProjection?,
        callback: ImageBytesAvailableCallback
    ) {
        if (projection == null) {
            callback.onImageBytesAvailable(null)
            return
        }

        synchronized(screenshotLock) {
            try {
                val metrics = DisplayMetrics()
                val windowManager = context?.getSystemService(Context.WINDOW_SERVICE) as WindowManager

                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealMetrics(metrics)
                val density = metrics.densityDpi
                val width = metrics.widthPixels
                val height = metrics.heightPixels

                val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

                val virtualDisplay = projection.createVirtualDisplay(
                    "ScreenCapture",
                    width,
                    height,
                    density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.surface,
                    null,
                    null
                )

                var singleTimeComplete = false
                imageReader.setOnImageAvailableListener(
                    { reader ->
                        if (!singleTimeComplete) {
                            singleTimeComplete = true
                            val image = reader.acquireLatestImage()
                            val bitmap = image?.toBitmap()

                            val bytes = ByteArrayOutputStream()
                            bitmap?.compress(Bitmap.CompressFormat.PNG, 100, bytes)
                            val bitmapData = bytes.toByteArray()

                            callback.onImageBytesAvailable(bitmapData)

                            try {
                                image?.close()
                            } catch (e: Exception) {
                                Log.e("take screenshot", "Error closing image: $e")
                            }
                            try {
                                virtualDisplay?.release()
                            } catch (e: Exception) {
                                Log.e("take screenshot", "Error releasing virtualDisplay: $e")
                            }
                            try {
                                imageReader.close()
                            } catch (e: Exception) {
                                Log.e("take screenshot", "Error closing imageReader: $e")
                            }
                        }
                    },
                    null,
                )
            } catch (ex: Throwable) {
                ex.printStackTrace()
                Log.e("take screenshot", "take screenshot error!")
                callback.onImageBytesAvailable(null)
            }
        }
    }

    @SuppressLint("WrongConstant")
    fun takeScreenshotInBackground(callback: ImageBytesAvailableCallback) {
        captureScreenAsBytesWithProjection(sharedMediaProjection, callback)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activityBinding = binding
        activityBinding?.addActivityResultListener(this)
        this.activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activityBinding?.removeActivityResultListener(this)
        activityBinding = null
        this.activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        TODO("Not yet implemented")
    }

    override fun onDetachedFromActivity() {
        this.activity = null
    }

    private fun requestMediaProjection() {
        val serviceIntent = Intent(activity, MediaProjectionService::class.java)
        serviceIntent.action = "com.example.action.START_CAPTURE"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(activity!!, serviceIntent)
        } else {
            activity?.startService(serviceIntent)
        }

        activityBinding?.activity?.startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(),
            requestCodeForegroundService
        )
    }

    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        for (service in manager?.getRunningServices(Int.MAX_VALUE) ?: emptyList()) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        when (requestCode) {
            requestCodeForegroundService -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
                    mediaProjection?.registerCallback(projectionCallback, null)
                    sharedMediaProjection = mediaProjection
                } else {
                    val stopIntent = Intent(activity!!, MediaProjectionService::class.java)
                    stopIntent.action = MediaProjectionService.ACTION_STOP_SERVICE
                    activity?.startService(stopIntent)
                    mediaProjection?.stop()
                }
            }
        }
        return false
    }
}
