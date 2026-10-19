package com.example.nodedemo.data

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

object DemoLog {
    const val TAG = "NodeDemo"
    private const val MAX_ENTRIES = 200

    enum class Level { INFO, WARN, ERROR }

    data class Entry(
        val id: Long,
        val timeMillis: Long,
        val level: Level,
        val message: String,
    )

    private val nextId = AtomicLong(0)
    private val lock = Any()
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    @Volatile
    private var store: PersistentLogStore? = null

    fun attachStore(persistentStore: PersistentLogStore) {
        synchronized(lock) {
            if (store != null) return
            store = persistentStore

            val restored = persistentStore.readRecent(MAX_ENTRIES)
            if (restored.isNotEmpty()) {
                val restoredEntries = restored.map { entry ->
                    entry.copy(id = nextId.incrementAndGet())
                }
                _entries.value = (_entries.value + restoredEntries).takeLast(MAX_ENTRIES)
            }
        }
    }

    fun info(message: String) = add(Level.INFO, message)

    fun warn(message: String) = add(Level.WARN, message)

    fun error(message: String) = add(Level.ERROR, message)

    fun add(level: Level, message: String) {
        when (level) {
            Level.INFO -> Log.i(TAG, message)
            Level.WARN -> Log.w(TAG, message)
            Level.ERROR -> Log.e(TAG, message)
        }
        synchronized(lock) {
            val entry = Entry(
                id = nextId.incrementAndGet(),
                timeMillis = System.currentTimeMillis(),
                level = level,
                message = message,
            )
            _entries.value = (_entries.value + entry).takeLast(MAX_ENTRIES)
            store?.append(entry)
        }
    }

    fun clear() {
        synchronized(lock) {
            _entries.value = emptyList()
            store?.clear()
        }
    }
}
