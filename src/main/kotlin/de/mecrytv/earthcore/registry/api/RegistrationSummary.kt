package de.mecrytv.earthcore.registry.api

data class RegistrationSummary(
    val entries: List<RegisteredEntry> = emptyList(),
    val skipped: List<String> = emptyList(),
) {

    val models: Int get() = count(RegisteredEntry.Kind.MODEL)

    val listeners: Int get() = count(RegisteredEntry.Kind.LISTENER)

    val commands: Int get() = count(RegisteredEntry.Kind.COMMAND)

    val total: Int get() = entries.size

    fun of(kind: RegisteredEntry.Kind): List<RegisteredEntry> = entries.filter { it.kind == kind }

    private fun count(kind: RegisteredEntry.Kind) = entries.count { it.kind == kind }

    override fun toString(): String =
        "$models Models, $listeners Listener, $commands Commands" +
            if (skipped.isEmpty()) "" else " (${skipped.size} uebersprungen)"
}
