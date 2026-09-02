package de.mecrytv.earthcore.registry.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AutoCommand(
    val name: String,
    val description: String = "",
    val aliases: Array<String> = [],
    val permission: String = "",
    val requires: Array<String> = [],
)
