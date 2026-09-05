package de.mecrytv.earthcore.registry.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Cooldown(
    val seconds: Long = 0,
    val minutes: Long = 0,
    val hours: Long = 0,
    val key: String = "",
    val bypassPermission: String = "",
    val messageKey: String = "cooldown.active",
)
