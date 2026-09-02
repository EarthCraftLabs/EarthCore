package de.mecrytv.earthcore.config

fun interface PlaceholderResolver {

    fun resolve(input: String, placeholders: Map<String, Any?>): String
}

class PatternPlaceholderResolver(
    prefix: String = "%",
    suffix: String = "%",
) : PlaceholderResolver {

    private val pattern = Regex("${Regex.escape(prefix)}([A-Za-z0-9_.-]+)${Regex.escape(suffix)}")

    override fun resolve(input: String, placeholders: Map<String, Any?>): String {
        if (input.isEmpty() || placeholders.isEmpty()) return input
        return pattern.replace(input) { match ->
            val key = match.groupValues[1]
            if (placeholders.containsKey(key)) placeholders[key]?.toString().orEmpty() else match.value
        }
    }
}
