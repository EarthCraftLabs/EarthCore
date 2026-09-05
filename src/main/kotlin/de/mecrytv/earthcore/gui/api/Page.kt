package de.mecrytv.earthcore.gui.api

class Page private constructor(
    val index: Int,
    val perPage: Int,
    val total: Int,
) {

    val count: Int = if (perPage <= 0) 1 else maxOf(1, (total + perPage - 1) / perPage)

    val first: Boolean get() = index == 0

    val last: Boolean get() = index >= count - 1

    val from: Int get() = index * perPage

    val to: Int get() = minOf(from + perPage, total)

    fun <T> slice(entries: List<T>): List<T> =
        if (from >= entries.size) emptyList() else entries.subList(from, minOf(to, entries.size))

    fun next(): Page = of(index + 1, perPage, total)

    fun previous(): Page = of(index - 1, perPage, total)

    override fun toString(): String = "Seite ${index + 1}/$count"

    companion object {

        @JvmStatic
        fun of(index: Int, perPage: Int, total: Int): Page {
            val roh = Page(0, perPage, total)
            return Page(index.coerceIn(0, roh.count - 1), perPage, total)
        }
    }
}
