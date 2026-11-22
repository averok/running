package app.running.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** Lista persistida en un JSON local. Suficiente para esta versión; migrable a Room. */
class JsonListStore<T>(
    private val file: File,
    elementSerializer: KSerializer<T>,
    private val idOf: (T) -> String,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        classDiscriminator = "_t"
    }
    private val serializer = ListSerializer(elementSerializer)

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<T>> = _items.asStateFlow()

    private fun load(): List<T> = runCatching {
        if (file.exists()) json.decodeFromString(serializer, file.readText()) else emptyList()
    }.getOrDefault(emptyList())

    private fun persist() {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(serializer, _items.value))
        tmp.renameTo(file)
    }

    fun upsert(item: T) {
        _items.update { list ->
            if (list.any { idOf(it) == idOf(item) }) list.map { if (idOf(it) == idOf(item)) item else it }
            else list + item
        }
        persist()
    }

    fun addAll(items: List<T>) {
        _items.update { it + items }
        persist()
    }

    fun delete(id: String) {
        _items.update { list -> list.filterNot { idOf(it) == id } }
        persist()
    }
}
