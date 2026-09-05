package de.mecrytv.earthcore.config

data class ConfigVersioning(
    val current: Int = 1,
    val key: String = "configVersion",
    val steps: Map<Int, ConfigMigration> = emptyMap(),
) {

    init {
        require(current >= 1) { "Die Config-Version muss mindestens 1 sein, war $current" }
        steps.keys.forEach { step ->
            require(step in 2..current) {
                "Migrationsschritt $step liegt ausserhalb von 2..$current"
            }
        }
    }

    val enabled: Boolean get() = current > 1 || steps.isNotEmpty()

    fun pending(from: Int): List<Pair<Int, ConfigMigration>> =
        ((from + 1)..current).mapNotNull { schritt -> steps[schritt]?.let { schritt to it } }

    companion object {

        val NONE = ConfigVersioning()
    }
}
