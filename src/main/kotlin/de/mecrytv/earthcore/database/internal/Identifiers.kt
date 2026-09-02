package de.mecrytv.earthcore.database.internal

internal object Identifiers {

    private val ALLOWED = Regex("[A-Za-z0-9_]{1,64}")

    fun check(raw: String): String {
        require(ALLOWED.matches(raw)) {
            "'$raw' ist kein gueltiger Bezeichner - erlaubt sind 1-64 Zeichen aus A-Z, a-z, 0-9 und _"
        }
        return raw
    }
}
