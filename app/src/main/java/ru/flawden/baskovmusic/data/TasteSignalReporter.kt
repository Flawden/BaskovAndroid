package ru.flawden.baskovmusic.data

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import ru.flawden.baskovmusic.model.TasteSignal
import ru.flawden.baskovmusic.model.TasteSignalSource
import ru.flawden.baskovmusic.model.TasteSignalType
import java.util.UUID

/**
 * Durable phone-side taste queue.
 *
 * Playback never depends on connectivity: signals are committed locally first and then flushed
 * to Baskov Product API in bounded batches. A process-wide Mutex prevents UI and MediaSession
 * reporters from sending the same persisted rows concurrently.
 */
class TasteSignalReporter(
    context: Context,
    private val repository: BaskovRepository,
) {
    private val store = TasteSignalStore(context.applicationContext)

    fun enqueue(guildId: String, event: TasteSignal) {
        if (guildId.isBlank()) return
        store.append(guildId, event)
    }

    suspend fun flush() {
        FLUSH_MUTEX.withLock {
            while (true) {
                val batch = store.peekBatch(MAX_BATCH) ?: return
                val sent = runCatching {
                    repository.recordTasteSignals(batch.guildId, batch.rows.map { it.event })
                }.isSuccess
                if (!sent) return
                store.remove(batch.rows.mapTo(hashSetOf()) { it.id })
            }
        }
    }

    internal companion object {
        const val MAX_BATCH = 50
        const val MAX_QUEUED = 500
        private val FLUSH_MUTEX = Mutex()
    }
}

private data class QueuedTasteSignal(
    val id: String,
    val guildId: String,
    val event: TasteSignal,
)

private data class QueuedTasteBatch(
    val guildId: String,
    val rows: List<QueuedTasteSignal>,
)

private class TasteSignalStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun append(guildId: String, event: TasteSignal) {
        val rows = load().toMutableList()
        rows += QueuedTasteSignal(UUID.randomUUID().toString(), guildId, event)
        while (rows.size > TasteSignalReporter.MAX_QUEUED) rows.removeAt(0)
        persist(rows)
    }

    @Synchronized
    fun peekBatch(limit: Int): QueuedTasteBatch? {
        val rows = load()
        val first = rows.firstOrNull() ?: return null
        val selected = rows.asSequence()
            .filter { it.guildId == first.guildId }
            .take(limit.coerceIn(1, TasteSignalReporter.MAX_BATCH))
            .toList()
        return QueuedTasteBatch(first.guildId, selected)
    }

    @Synchronized
    fun remove(ids: Set<String>) {
        if (ids.isEmpty()) return
        persist(load().filterNot { it.id in ids })
    }

    private fun load(): List<QueuedTasteSignal> {
        val raw = prefs.getString(KEY_QUEUE, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.toQueued()?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persist(rows: List<QueuedTasteSignal>) {
        val array = JSONArray()
        rows.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_QUEUE, array.toString()).commit()
    }

    private fun QueuedTasteSignal.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("guildId", guildId)
        .put("type", event.type.name)
        .put("source", event.source.name)
        .apply {
            event.stableKey?.takeIf(String::isNotBlank)?.let { put("stableKey", it) }
        }
        .put("artist", event.artist)
        .put("title", event.title)
        .put("completionRatio", event.completionRatio)

    private fun JSONObject.toQueued(): QueuedTasteSignal? = runCatching {
        QueuedTasteSignal(
            id = getString("id"),
            guildId = getString("guildId"),
            event = TasteSignal(
                type = TasteSignalType.valueOf(getString("type")),
                source = TasteSignalSource.valueOf(getString("source")),
                stableKey = optString("stableKey").takeIf(String::isNotBlank),
                artist = getString("artist"),
                title = getString("title"),
                completionRatio = optDouble("completionRatio", 0.0).coerceIn(0.0, 1.0),
            ),
        )
    }.getOrNull()

    private companion object {
        const val PREFS_NAME = "baskov_taste_signals"
        const val KEY_QUEUE = "queue_v1"
    }
}
