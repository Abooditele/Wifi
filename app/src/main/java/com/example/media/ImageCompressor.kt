package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object ImageCompressor {
    private const val TAG = "ImageCompressor"
    private const val MAX_DIMENSION = 1280

    /**
     * Compresses image from Uri, saves locally, and returns local file path and Base64 string.
     */
    fun compressAndSaveImage(context: Context, uri: Uri): Pair<String, String>? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return null
            inputStream.close()

            val scaledBitmap = scaleBitmap(originalBitmap, MAX_DIMENSION)

            val baos = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            val imageBytes = baos.toByteArray()

            val fileName = "img_${System.currentTimeMillis()}.jpg"
            val outputFile = File(context.filesDir, fileName)
            val fos = FileOutputStream(outputFile)
            fos.write(imageBytes)
            fos.flush()
            fos.close()

            val base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            Pair(outputFile.absolutePath, base64)
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing image", e)
            null
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val targetWidth: Int
        val targetHeight: Int

        if (width > height) {
            targetWidth = maxDimension
            targetHeight = (maxDimension / ratio).toInt()
        } else {
            targetHeight = maxDimension
            targetWidth = (maxDimension * ratio).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    fun saveBase64ImageToFile(context: Context, base64Data: String, fileName: String): String? {
        return try {
            val bytes = Base64.decode(base64Data, Base64.NO_WRAP)
            val outputFile = File(context.filesDir, fileName)
            val fos = FileOutputStream(outputFile)
            fos.write(bytes)
            fos.flush()
            fos.close()
            outputFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving decoded Base64 image", e)
            null
        }
    }
}
