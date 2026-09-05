package de.mecrytv.earthcore.gui.api

class GuiMask(rows: List<String>) {

    private val zeilen: List<String> = rows.map { it.replace(" ", "") }

    val width: Int = zeilen.firstOrNull()?.length ?: 0

    val size: Int = zeilen.size * width

    init {
        require(zeilen.isNotEmpty()) { "Eine Maske braucht mindestens eine Zeile" }
        zeilen.forEachIndexed { index, zeile ->
            require(zeile.length == width) {
                "Alle Maskenzeilen muessen gleich lang sein - Zeile ${index + 1} hat ${zeile.length} statt $width"
            }
        }
    }

    fun slots(symbol: Char): List<Int> = buildList {
        zeilen.forEachIndexed { reihe, zeile ->
            zeile.forEachIndexed { spalte, zeichen ->
                if (zeichen == symbol) add(reihe * width + spalte)
            }
        }
    }

    fun symbols(): Set<Char> = zeilen.flatMap { it.toList() }.toSet()

    fun symbolAt(slot: Int): Char? {
        if (slot < 0 || slot >= size) return null
        return zeilen[slot / width][slot % width]
    }

    companion object {

        @JvmStatic
        fun of(vararg rows: String): GuiMask = GuiMask(rows.toList())
    }
}
