package de.mecrytv.earthcore.registry.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AutoListener(
    val name: String = "",
    val description: String = "",
    val requires: Array<String> = [],
)
