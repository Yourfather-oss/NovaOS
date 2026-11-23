package com.nova.os.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

const val WALLPAPER_FILE = "wallpaper.jpg"

/**
 * Copies a picked photo into NOVA's private storage, scaled to screen size and rotated upright,
 * so the wallpaper keeps working even if the original is moved or deleted.
 */
suspend fun importWallpaper(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longSide = maxOf(bounds.outWidth, bounds.outHeight)
        if (longSide <= 0) return@runCatching false

        var sample = 1
        while (longSide / (sample * 2) >= 2400) sample *= 2
        val decode = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decode) }
            ?: return@runCatching false

        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val upright = if (rotation != 0f) {
            Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height,
                Matrix().apply { postRotate(rotation) }, true
            )
        } else bitmap

        File(context.filesDir, WALLPAPER_FILE).outputStream().use {
            upright.compress(Bitmap.CompressFormat.JPEG, 88, it)
        }
        true
    }.getOrDefault(false)
}
