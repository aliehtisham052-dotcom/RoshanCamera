package com.innovation313.roshancamera.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads grid-sized thumbnails, never full frames.
 *
 * A twelve-megapixel photo decoded at full resolution is roughly 48 MB in
 * memory. A three-column grid holding a dozen of those will stutter on a
 * mid-range phone and eventually run out of heap — and mid-range phones are
 * exactly who this app is for. Android 10 and above can hand back a
 * pre-generated thumbnail; below that, the image is subsampled while decoding
 * so the full bitmap is never allocated in the first place.
 */
class ThumbnailLoader(private val context: Context) {

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    suspend fun load(uri: Uri, targetPx: Int): Bitmap? {
        cache.get(uri.toString())?.let { return it }

        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(uri, Size(targetPx, targetPx), null)
                } else {
                    decodeSubsampled(uri, targetPx)
                }
            }.getOrNull()
        } ?: return null

        cache.put(uri.toString(), bitmap)
        return bitmap
    }

    private fun decodeSubsampled(uri: Uri, targetPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetPx &&
            bounds.outHeight / (sample * 2) >= targetPx
        ) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private companion object {
        /** An eighth of the heap — generous for thumbnails, invisible against the budget. */
        val CACHE_BYTES = (Runtime.getRuntime().maxMemory() / 8).toInt()
    }
}
