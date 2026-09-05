package de.mecrytv.earthcore.version.api

interface CoreVersion {

    val version: String

    fun isAtLeast(required: String): Boolean

    fun requireAtLeast(required: String)
}
