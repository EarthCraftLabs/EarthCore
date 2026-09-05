package de.mecrytv.earthcore.version.internal

import de.mecrytv.earthcore.version.api.CoreVersion

class PluginCoreVersion(override val version: String) : CoreVersion {

    private val eigene = SemanticVersion.of(version)

    override fun isAtLeast(required: String): Boolean = eigene >= SemanticVersion.of(required)

    override fun requireAtLeast(required: String) {
        if (isAtLeast(required)) return
        error("EarthCore $version ist zu alt - mindestens $required wird benoetigt")
    }
}
