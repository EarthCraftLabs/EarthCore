package de.mecrytv.earthcore.cooldown.api

import java.time.Duration
import java.util.UUID

interface CooldownRegistry {

    fun start(subject: UUID, key: String, duration: Duration)

    fun extend(subject: UUID, key: String, by: Duration)

    fun clear(subject: UUID, key: String): Boolean

    fun clearAll(subject: UUID): Int

    fun isActive(subject: UUID, key: String): Boolean

    fun remaining(subject: UUID, key: String): Duration

    fun keys(subject: UUID): Set<String>
}
