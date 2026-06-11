package com.korvus.pocketvoice.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

private const val INDEX_FILE = "voices_index.json"
private val json = Json { ignoreUnknownKeys = true }

class VoiceStore(ctx: Context) {
    private val dir = File(ctx.filesDir, "voices").apply { mkdirs() }
    private val indexFile = File(ctx.filesDir, INDEX_FILE)
    private val mutex = Mutex()
    private val _items = MutableStateFlow<List<Voice>>(emptyList())
    val items: StateFlow<List<Voice>> = _items.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _items.value = try {
                if (indexFile.exists())
                    json.decodeFromString(ListSerializer(Voice.serializer()), indexFile.readText())
                else emptyList()
            } catch (_: Throwable) { emptyList() }
        }
    }

    suspend fun seedFromAssetsIfEmpty(ctx: Context) = withContext(Dispatchers.IO) {
        if (_items.value.isNotEmpty()) return@withContext
        try {
            val list = ctx.assets.list("voices") ?: emptyArray()
            val voices = mutableListOf<Voice>()
            for (asset in list) {
                if (!asset.endsWith(".mp3") && !asset.endsWith(".wav")) continue
                val dst = File(dir, asset)
                if (!dst.exists()) {
                    ctx.assets.open("voices/$asset").use { input ->
                        dst.outputStream().use { input.copyTo(it) }
                    }
                }
                val baseName = asset.substringBeforeLast('.')
                val name = baseName.replace('_', ' ').replaceFirstChar { it.uppercase() }
                voices.add(
                    Voice(
                        id = "preset_$baseName",
                        name = name,
                        emoji = emojiForName(baseName),
                        filename = asset,
                        sizeBytes = dst.length(),
                        builtin = true,
                    )
                )
            }
            if (voices.isNotEmpty()) {
                mutex.withLock {
                    _items.value = voices
                    persist()
                }
            }
        } catch (_: Throwable) {}
    }

    private fun emojiForName(name: String): String = when {
        name.contains("woman", true) || name.contains("female", true) -> "👩"
        name.contains("man", true) || name.contains("male", true) -> "👨"
        name.contains("child", true) || name.contains("kid", true) -> "🧒"
        name.contains("robot", true) -> "🤖"
        name.contains("old", true) -> "👴"
        name.contains("anime", true) -> "🌸"
        else -> "🎙"
    }

    suspend fun addFromUri(ctx: Context, uri: Uri, name: String, emoji: String = "🎙"): Voice = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val ext = mimeToExt(ctx.contentResolver.getType(uri)) ?: "mp3"
        val filename = "$id.$ext"
        val dst = File(dir, filename)
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            dst.outputStream().use { input.copyTo(it) }
        } ?: error("Не удалось прочитать файл")
        val v = Voice(
            id = id, name = name, emoji = emoji,
            filename = filename, sizeBytes = dst.length(), builtin = false,
        )
        mutex.withLock {
            _items.value = _items.value + v
            persist()
        }
        v
    }

    /** Добавить уже сохранённый файл (загруженный из HF Hub в voices/). */
    suspend fun addExisting(v: Voice) = withContext(Dispatchers.IO) {
        mutex.withLock {
            _items.value = _items.value + v
            persist()
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val v = _items.value.firstOrNull { it.id == id } ?: return@withLock
            val f = File(dir, v.filename)
            if (f.exists() && !v.builtin) f.delete()  // builtin keep on disk for re-seeding
            _items.value = _items.value.filterNot { it.id == id }
            persist()
        }
    }

    fun fileOf(v: Voice): File = File(dir, v.filename)

    private fun persist() {
        indexFile.writeText(json.encodeToString(ListSerializer(Voice.serializer()), _items.value))
    }

    private fun mimeToExt(mime: String?): String? = when {
        mime == null -> null
        mime.contains("mp3") || mime.contains("mpeg") -> "mp3"
        mime.contains("wav") -> "wav"
        mime.contains("ogg") -> "ogg"
        mime.contains("m4a") || mime.contains("aac") -> "m4a"
        else -> "mp3"
    }
}
