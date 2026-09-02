package de.mecrytv.earthcore.config

import com.google.gson.JsonElement
import java.io.File

interface ConfigService {

    val file: File

    fun reload()

    fun save()

    fun resetToDefaults()

    fun contains(path: String): Boolean

    fun find(path: String): JsonElement?

    fun keys(path: String = ""): Set<String>

    fun <T : Any> get(path: String, type: Class<T>): T?

    fun <T : Any> getOrDefault(path: String, type: Class<T>, fallback: T): T

    fun <T : Any> getList(path: String, type: Class<T>): List<T>

    fun getString(path: String, vararg placeholders: Pair<String, Any?>): String?

    fun getStringList(path: String, vararg placeholders: Pair<String, Any?>): List<String>

    fun getBoolean(path: String, fallback: Boolean = false): Boolean

    fun getInt(path: String, fallback: Int = 0): Int

    fun getLong(path: String, fallback: Long = 0L): Long

    fun getDouble(path: String, fallback: Double = 0.0): Double

    fun <T : Any> asModel(type: Class<T>): T

    fun set(path: String, value: Any?)

    fun delete(path: String): Boolean

    fun format(text: String, vararg placeholders: Pair<String, Any?>): String
}

inline fun <reified T : Any> ConfigService.get(path: String): T? = get(path, T::class.java)

inline fun <reified T : Any> ConfigService.getOrDefault(path: String, fallback: T): T =
    getOrDefault(path, T::class.java, fallback)

inline fun <reified T : Any> ConfigService.getList(path: String): List<T> = getList(path, T::class.java)

inline fun <reified T : Any> ConfigService.asModel(): T = asModel(T::class.java)
