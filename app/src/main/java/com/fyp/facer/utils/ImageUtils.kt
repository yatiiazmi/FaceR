package com.fyp.facer.utils

import android.graphics.*
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/** Convert ImageProxy (YUV_420_888) to ARGB Bitmap */
fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val yBuffer: ByteBuffer = image.planes[0].buffer
    val uBuffer: ByteBuffer = image.planes[1].buffer
    val vBuffer: ByteBuffer = image.planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()
    val nv21 = ByteArray(ySize + uSize + vSize)

    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 90, out)
    val yuv = out.toByteArray()
    var bmp = BitmapFactory.decodeByteArray(yuv, 0, yuv.size)

    // Apply rotation
    val matrix = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
    bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    return bmp
}

fun cropFace(source: Bitmap, box: Rect, padRatio: Float = 0.25f, size: Int = 160): Bitmap {
    val cx = box.centerX().toFloat()
    val cy = box.centerY().toFloat()
    val half = (maxOf(box.width(), box.height()) * (1f + padRatio) / 2f)

    val left = (cx - half).toInt().coerceAtLeast(0)
    val top = (cy - half).toInt().coerceAtLeast(0)
    val right = (cx + half).toInt().coerceAtMost(source.width - 1)
    val bottom = (cy + half).toInt().coerceAtMost(source.height - 1)

    val w = (right - left).coerceAtLeast(1)
    val h = (bottom - top).coerceAtLeast(1)

    val cropped = Bitmap.createBitmap(source, left, top, w, h)
    return Bitmap.createScaledBitmap(cropped, size, size, true)
}