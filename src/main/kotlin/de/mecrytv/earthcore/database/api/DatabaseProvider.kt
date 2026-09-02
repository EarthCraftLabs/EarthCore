package de.mecrytv.earthcore.database.api

interface DatabaseProvider {

    fun of(name: String): DatabaseService

    fun names(): Set<String>
}
