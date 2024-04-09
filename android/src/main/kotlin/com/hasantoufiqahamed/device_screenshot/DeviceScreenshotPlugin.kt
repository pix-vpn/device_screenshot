package com.hasantoufiqahamed.device_screenshot

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import androidx.core.content.ContextCompat
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.WindowManager
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

inline fun <reified T> systemService(context: Context): T? =
    ContextCompat.getSystemService(context, T::class.java)

const val API_APPLICATION_OVERLAY = 26

/** DeviceScreenshotPlugin */
class DeviceScreenshotPlugin : FlutterPlugin, MethodCallHandler {
    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private var context: Context? = null
    private lateinit var channel: MethodChannel

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null


    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "device_screenshot")
        channel.setMethodCallHandler(this)
        context = flutterPluginBinding.applicationContext

        mediaProjectionManager =
            context?.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {

            "overTheAppPermissionCheck" -> {
                result.success(canDrawOverlaysCompat(context!!))
            }

            "requestOverlayPermission" -> {
                requestOverlayPermission()
            }

            "takeScreenshot" -> {
                captureScreen(object : ImageAndUriAvailableCallback {
                    override fun onImageAndUriAvailable(uri: Uri?) {
                        if (uri != null) {
                            result.success(uri.toString())
                        } else {
                            result.notImplemented()
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

    private fun canDrawOverlaysCompat(context: Context): Boolean {
        // Android 5 always allow overlay.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true

        // (Android 7+) if Settings.canDrawOverlays(context) == true, it's reliable.
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M && Settings.canDrawOverlays(context)) return true

        // Android 6 と Android 8, 8.1 はバグがあるので
        // 許可されていても Settings.canDrawOverlays(context) がfalseを返す場合がある

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // AppOpsManager.checkOp() は API 29 でdeprecated

            systemService<AppOpsManager>(context)?.let { manager ->
                try {
                    @Suppress("DEPRECATION")
                    val result = manager.checkOp(
                        AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                        Binder.getCallingUid(),
                        context.packageName
                    )
                    return result == AppOpsManager.MODE_ALLOWED
                } catch (_: Throwable) {
                }
            }
        }

        //id this fails, we definitely can't do it
        systemService<WindowManager>(context)?.let { manager ->
            try {
                val viewToAdd = View(context)
                val params = WindowManager.LayoutParams(
                    0,
                    0,
                    if (Build.VERSION.SDK_INT >= API_APPLICATION_OVERLAY) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                    },
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSPARENT
                )
                viewToAdd.layoutParams = params
                manager.addView(viewToAdd, params)
                manager.removeView(viewToAdd)
                return true
            } catch (_: Throwable) {
            }
        }

        return false

    }

    private fun requestOverlayPermission() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context!!.packageName}")
            )
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            intent.data = Uri.fromParts("package", context?.packageName, null)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context!!.startActivity(intent)
    }

    interface ImageAndUriAvailableCallback {
        fun onImageAndUriAvailable(uri: Uri?)
    }

    @SuppressLint("WrongConstant")
    private fun captureScreen(callback: ImageAndUriAvailableCallback) {
        try {
            val metrics = DisplayMetrics()
            val windowManager = context?.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            windowManager.defaultDisplay.getMetrics(metrics)
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
                        mediaProjection?.stop()
                    }
                },
                null,
            )
        } catch (ex: Throwable) {
            Log.e("take screenshot", "take screenshot error!")
        }
    }

    private fun Image.toBitmap(): Bitmap {
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride: Int = planes[0].pixelStride
        val rowStride: Int = planes[0].rowStride
        val rowPadding: Int = rowStride - pixelStride * width
        val bitmap =
            Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }
}