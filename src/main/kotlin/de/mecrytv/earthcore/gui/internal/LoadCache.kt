package de.mecrytv.earthcore.gui.internal

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

internal class LoadCache(
    private val runAsync: (Runnable) -> Unit,
    private val onDone: () -> Unit,
    private val onError: (String, Throwable) -> Unit,
) {

    private val werte = ConcurrentHashMap<String, Any>()
    private val laufend = ConcurrentHashMap.newKeySet<String>()

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String, loader: Supplier<T>): T? {
        werte[key]?.let { return if (it === NICHTS) null else it as T }
        if (!laufend.add(key)) return null

        runAsync {
            try {
                werte[key] = loader.get() ?: NICHTS
            } catch (ex: Throwable) {
                werte[key] = NICHTS
                onError(key, ex)
            } finally {
                laufend.remove(key)
                onDone()
            }
        }
        return null
    }

    fun invalidate(key: String) {
        werte.remove(key)
    }

    fun clear() {
        werte.clear()
        laufend.clear()
    }

    val pending: Boolean get() = laufend.isNotEmpty()

    private companion object {

        val NICHTS = Any()
    }
}
