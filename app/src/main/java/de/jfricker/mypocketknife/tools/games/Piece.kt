package de.jfricker.mypocketknife.tools.games

/** Ein Spielstein. Die id bleibt beim Fallen/Verschieben gleich, damit er animiert werden kann. */
data class Piece(val id: Int, val color: Int)

/** Herkunft eines Steins für die Animation (Zeile/Spalte, darf ausserhalb des Felds liegen). */
data class From(val row: Float, val col: Float)

/** Unveränderliches Spielfeld, Index = Zeile · Spalten + Spalte, Zeile 0 = oben. */
class Grid(val rows: Int, val cols: Int, val cells: List<Piece?>) {
    init {
        require(cells.size == rows * cols)
    }

    fun index(row: Int, col: Int) = row * cols + col
    fun rowOf(index: Int) = index / cols
    fun colOf(index: Int) = index % cols
    operator fun get(index: Int) = cells[index]
    operator fun get(row: Int, col: Int): Piece? =
        if (row in 0 until rows && col in 0 until cols) cells[index(row, col)] else null

    /** Waagrecht/senkrecht benachbarte Felder. */
    fun neighbours(index: Int): List<Int> {
        val r = rowOf(index)
        val c = colOf(index)
        return buildList {
            if (r > 0) add(index - cols)
            if (r < rows - 1) add(index + cols)
            if (c > 0) add(index - 1)
            if (c < cols - 1) add(index + 1)
        }
    }

    fun isAdjacent(a: Int, b: Int) = b in neighbours(a)

    val pieceCount: Int get() = cells.count { it != null }
}
