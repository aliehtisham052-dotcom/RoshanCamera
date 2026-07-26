package com.innovation313.roshancamera.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes finished photos to the device's shared picture store.
 *
 * They land in a named album so they show up in any gallery app without this
 * app having to be running — a delivery rider handing over evidence should not
 * need to open Roshan Camera to find it.
 */
class PhotoStore(private val context: Context) {

    data class Saved(val uri: Uri, val fileName: String, val bytes: ByteArray) {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Saved && uri == other.uri && fileName == other.fileName)

        override fun hashCode(): Int = 31 * uri.hashCode() + fileName.hashCode()
    }

    suspend fun save(bitmap: Bitmap): Saved = withContext(Dispatchers.IO) {
        val fileName = "RC_${TIMESTAMP.format(Date())}.jpg"

        val bytes = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            stream.toByteArray()
        }

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(fileName, bytes)
        } else {
            saveToPublicDirectory(fileName, bytes)
        }

        Saved(uri, fileName, bytes)
    }

    private fun saveViaMediaStore(fileName: String, bytes: ByteArray): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore refused to create an entry for $fileName")

        resolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: error("MediaStore returned no stream for $fileName")

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    @Suppress("DEPRECATION")
    private fun saveToPublicDirectory(fileName: String, bytes: ByteArray): Uri {
        val album = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            ALBUM
        )
        if (!album.exists()) album.mkdirs()

        val file = File(album, fileName)
        FileOutputStream(file).use { it.write(bytes) }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATA, file.absolutePath)
        }
        return context.contentResolver
            .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: Uri.fromFile(file)
    }

    /** Lists photos this app has written, newest first. */
    suspend fun list(limit: Int = 200): List<Uri> = withContext(Dispatchers.IO) {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection: String
        val args: Array<String>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            args = arrayOf("%$ALBUM%")
        } else {
            selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
            args = arrayOf("RC_%")
        }

        val results = mutableListOf<Uri>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext() && results.size < limit) {
                results += Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    cursor.getLong(idColumn).toString()
                )
            }
        }
        results
    }

    companion object {
        const val ALBUM = "Roshan Camera"
        private const val JPEG_QUALITY = 92
        private val TIMESTAMP = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
    }
}
