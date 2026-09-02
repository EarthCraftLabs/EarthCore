package de.mecrytv.earthcore.registry.internal

import java.lang.reflect.Modifier

internal object Instantiator {

    fun create(type: Class<*>, owner: Any): Any {
        singleton(type)?.let { return it }

        val constructors = type.constructors
        constructors.firstOrNull { it.parameterCount == 1 && it.parameterTypes[0].isInstance(owner) }
            ?.let { return it.newInstance(owner) }

        return constructors.firstOrNull { it.parameterCount == 0 }?.newInstance()
            ?: error("braucht einen parameterlosen Konstruktor oder einen mit der Plugin-Instanz")
    }

    private fun singleton(type: Class<*>): Any? = runCatching {
        type.getDeclaredField("INSTANCE")
            .takeIf { Modifier.isStatic(it.modifiers) && it.type == type }
            ?.also { it.isAccessible = true }
            ?.get(null)
    }.getOrNull()
}
