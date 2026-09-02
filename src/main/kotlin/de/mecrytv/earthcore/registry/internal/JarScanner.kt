package de.mecrytv.earthcore.registry.internal

import java.io.File
import java.util.jar.JarFile

internal object JarScanner {

    fun scan(
        root: File,
        loader: ClassLoader,
        packages: List<String>,
        onError: (String, Throwable) -> Unit = { _, _ -> },
    ): List<Class<*>> = classNames(entryNames(root), packages).mapNotNull { name ->
        runCatching { Class.forName(name, false, loader) }
            .onFailure { onError(name, it) }
            .getOrNull()
    }

    fun classNames(entries: Sequence<String>, packages: List<String>): List<String> {
        val roots = packages.map { it.trim().trim('.') }.filter { it.isNotEmpty() }
        if (roots.isEmpty()) return emptyList()
        return entries
            .filter { it.endsWith(".class") }
            .map { it.removeSuffix(".class").replace('/', '.') }
            .filter { name ->
                val simple = name.substringAfterLast('.')
                simple != "module-info" && simple != "package-info" &&
                    name.substringAfterLast('$').toIntOrNull() == null &&
                    roots.any { name.startsWith("$it.") }
            }
            .distinct()
            .sorted()
            .toList()
    }

    private fun entryNames(root: File): Sequence<String> = when {
        root.isFile -> JarFile(root).use { jar -> jar.entries().asSequence().map { it.name }.toList() }.asSequence()
        root.isDirectory -> root.walkTopDown().filter { it.isFile }
            .map { it.relativeTo(root).invariantSeparatorsPath }
        else -> emptySequence()
    }
}
