package de.mecrytv.earthcore.cooldown.internal

import de.mecrytv.earthcore.database.annotations.PrimaryKey
import de.mecrytv.earthcore.database.annotations.Table
import java.util.UUID

@Table("cooldowns")
data class CooldownRecord(
    @PrimaryKey val id: String,
    val subject: UUID,
    val key: String,
    val expiresAt: Long,
) {

    companion object {

        fun id(subject: UUID, key: String): String = "$subject:$key"
    }
}
