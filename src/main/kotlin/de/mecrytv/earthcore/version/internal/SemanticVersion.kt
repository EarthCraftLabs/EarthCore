package de.mecrytv.earthcore.version.internal

internal class SemanticVersion private constructor(
    private val teile: List<Int>,
    private val vorabversion: String?,
) : Comparable<SemanticVersion> {

    override fun compareTo(other: SemanticVersion): Int {
        for (index in 0 until maxOf(teile.size, other.teile.size)) {
            val links = teile.getOrElse(index) { 0 }
            val rechts = other.teile.getOrElse(index) { 0 }
            if (links != rechts) return links.compareTo(rechts)
        }
        return when {
            vorabversion == other.vorabversion -> 0
            vorabversion == null -> 1
            other.vorabversion == null -> -1
            else -> vorabversion.compareTo(other.vorabversion)
        }
    }

    override fun toString(): String = teile.joinToString(".") + (vorabversion?.let { "-$it" } ?: "")

    companion object {

        fun of(raw: String): SemanticVersion {
            val ohneAufbau = raw.trim().substringBefore('+')
            val vorabversion = ohneAufbau.substringAfter('-', "").takeIf { it.isNotEmpty() }
            val zahlen = ohneAufbau.substringBefore('-').split('.')

            require(zahlen.isNotEmpty() && zahlen.all { it.isNotEmpty() }) {
                "'$raw' ist keine gueltige Version"
            }
            return SemanticVersion(
                zahlen.map { teil ->
                    teil.toIntOrNull() ?: throw IllegalArgumentException("'$raw' ist keine gueltige Version")
                },
                vorabversion,
            )
        }
    }
}
