package com.innovation313.roshancamera.proof

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Records what was true about each photo at the moment it was saved.
 *
 * A flat JSON file rather than a database: the record count is bounded by how
 * many photos a person takes, each row is tiny, and the whole file is read only
 * on the verify screen. Room would add a compiler plugin and roughly a
 * megabyte to an app whose selling point is that it is about eight.
 *
 * The file lives in the app's private storage, so a user editing a photo in a
 * gallery app cannot quietly edit its record to match.
 */
class ProofLedger(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val lock = Any()

    suspend fun record(entry: ProofRecord) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val array = readArray()
            array.put(entry.toJson())
            file.writeText(array.toString())
        }
    }

    /** Returns the record whose stamped-file hash matches, or null. */
    suspend fun findByStampedHash(hash: String): ProofRecord? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val array = readArray()
            for (i in 0 until array.length()) {
                val record = ProofRecord.fromJson(array.optJSONObject(i) ?: continue)
                if (record != null && record.stampedHash == hash) return@withContext record
            }
            null
        }
    }

    suspend fun all(): List<ProofRecord> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val array = readArray()
            (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.let(ProofRecord::fromJson)
            }
        }
    }

    private fun readArray(): JSONArray = runCatching {
        if (file.exists()) JSONArray(file.readText()) else JSONArray()
    }.getOrElse { JSONArray() }

    private companion object {
        const val FILE_NAME = "proof-ledger.json"
    }
}

data class ProofRecord(
    val savedAtEpochSeconds: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMetres: Int,
    val address: String,
    /** SHA-256 of the original frame, before any stamp was drawn. */
    val sourceHash: String,
    /** SHA-256 of the finished file as written to storage. */
    val stampedHash: String,
    val fileName: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("t", savedAtEpochSeconds)
        put("lat", latitude)
        put("lon", longitude)
        put("acc", accuracyMetres)
        put("addr", address)
        put("src", sourceHash)
        put("out", stampedHash)
        put("name", fileName)
    }

    companion object {
        fun fromJson(json: JSONObject): ProofRecord? = runCatching {
            ProofRecord(
                savedAtEpochSeconds = json.getLong("t"),
                latitude = json.getDouble("lat"),
                longitude = json.getDouble("lon"),
                accuracyMetres = json.getInt("acc"),
                address = json.optString("addr"),
                sourceHash = json.getString("src"),
                stampedHash = json.getString("out"),
                fileName = json.optString("name")
            )
        }.getOrNull()
    }
}
