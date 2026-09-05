package de.mecrytv.fremd

import de.mecrytv.earthcore.config.ConfigDefaults
import de.mecrytv.earthcore.config.JsonConfigService

class FremdesPlugin {

    fun laden(): String =
        ConfigDefaults.resource("fremd.json").load(JsonConfigService.defaultGson()).toString()
}
