package de.mecrytv.earthcore.registry.api

data class RegisteredEntry(
    val kind: Kind,
    val name: String,
    val description: String,
    val type: String,
) {

    enum class Kind { MODEL, LISTENER, COMMAND }

    override fun toString(): String =
        "$kind $name" + if (description.isEmpty()) "" else " - $description"
}
