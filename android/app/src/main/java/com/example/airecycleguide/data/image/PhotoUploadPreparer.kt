package com.example.airecycleguide.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.roundToInt

class ImagePreparationException(
    val code: String,
    message: String,
) : IOException(message)

class PhotoUploadPreparer(private val cacheDirectory: File) {
    fun prepare(source: File): File {
        if (!source.isFile || source.length() == 0L) {
            throw ImagePreparationException("invalid_image", "촬영한 사진을 읽을 수 없습니다.")
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw ImagePreparationException("invalid_image", "지원하지 않는 이미지 형식입니다.")
        }

        val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
        val decoded = BitmapFactory.decodeFile(
            source.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: throw ImagePreparationException("invalid_image", "촬영한 사진을 읽을 수 없습니다.")

        var bitmap = applyExifOrientation(source, decoded)
        if (bitmap !== decoded) decoded.recycle()
        val output = File(cacheDirectory, "upload-${UUID.randomUUID()}.jpg")
        var completed = false

        try {
            var quality = 88
            while (true) {
                val compressed = FileOutputStream(output).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                }
                if (!compressed) {
                    throw ImagePreparationException("image_processing_failed", "사진을 전송용으로 준비하지 못했습니다.")
                }
                if (output.length() in 1L..MAX_UPLOAD_BYTES) {
                    completed = true
                    return output
                }

                if (quality > MIN_JPEG_QUALITY) {
                    quality = (quality - QUALITY_STEP).coerceAtLeast(MIN_JPEG_QUALITY)
                } else {
                    val largestSide = maxOf(bitmap.width, bitmap.height)
                    if (largestSide <= MIN_IMAGE_SIDE) {
                        throw ImagePreparationException("image_too_large", "사진 크기를 줄여도 서버 전송 한도를 넘습니다.")
                    }
                    val scale = RESIZE_FACTOR
                    val resized = Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                        (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                        true,
                    )
                    if (resized !== bitmap) bitmap.recycle()
                    bitmap = resized
                    quality = INITIAL_JPEG_QUALITY
                }
            }
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
            if (!completed) output.delete()
        }
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        while (maxOf(width / sampleSize, height / sampleSize) > MAX_IMAGE_SIDE) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun applyExifOrientation(source: File, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(source.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private companion object {
        const val MAX_UPLOAD_BYTES = 10L * 1024L * 1024L
        const val MAX_IMAGE_SIDE = 2560
        const val MIN_IMAGE_SIDE = 640
        const val INITIAL_JPEG_QUALITY = 88
        const val MIN_JPEG_QUALITY = 50
        const val QUALITY_STEP = 10
        const val RESIZE_FACTOR = 0.75f
    }
}
