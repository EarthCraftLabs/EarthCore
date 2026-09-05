package de.mecrytv.earthcore.logging.api

import java.util.logging.Level

enum class LogLevel(val julLevel: Level, val color: Int) {

    DEBUG(Level.FINE, 0x95A5A6),
    INFO(Level.INFO, 0x3498DB),
    WARN(Level.WARNING, 0xE67E22),
    ERROR(Level.SEVERE, 0xE74C3C);

    fun atLeast(minimum: LogLevel): Boolean = ordinal >= minimum.ordinal
}
