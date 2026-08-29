package io.github.mouse233.localsendkotlin.model

/** Platform-independent ordered state used by the pending-send queue. */
class PendingSendQueueState<T> {
    private val values = LinkedHashMap<String, T>()

    fun replace(entries: List<Pair<String, T>>) {
        values.clear()
        entries.forEach { (key, value) -> values[key] = value }
    }

    fun remove(key: String): Boolean = values.remove(key) != null

    fun clear() = values.clear()

    fun snapshot(): List<T> = values.values.toList()
}
