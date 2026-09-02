package de.mecrytv.earthcore.database.api

import java.util.concurrent.CompletableFuture

interface DatabaseService {

    fun connect()

    fun close()

    fun registerModel(modelClass: Class<*>)

    suspend fun <T : Any> save(entity: T)

    suspend fun <T : Any> update(entity: T)

    suspend fun <T : Any> delete(entity: T)

    suspend fun <T : Any, ID : Any> findById(modelClass: Class<T>, id: ID): T?

    suspend fun <T : Any> findAll(modelClass: Class<T>): List<T>

    fun <T : Any> saveAsync(entity: T): CompletableFuture<Void>

    fun <T : Any> updateAsync(entity: T): CompletableFuture<Void>

    fun <T : Any> deleteAsync(entity: T): CompletableFuture<Void>

    fun <T : Any, ID : Any> findByIdAsync(modelClass: Class<T>, id: ID): CompletableFuture<T?>

    fun <T : Any> findAllAsync(modelClass: Class<T>): CompletableFuture<List<T>>
}

inline fun <reified T : Any> DatabaseService.registerModel() = registerModel(T::class.java)

suspend inline fun <reified T : Any, ID : Any> DatabaseService.findById(id: ID): T? = findById(T::class.java, id)

suspend inline fun <reified T : Any> DatabaseService.findAll(): List<T> = findAll(T::class.java)
