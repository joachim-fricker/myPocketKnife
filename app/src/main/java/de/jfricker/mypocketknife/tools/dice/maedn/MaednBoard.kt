package de.jfricker.mypocketknife.tools.dice.maedn

import androidx.compose.ui.graphics.Color

/** Feld auf dem 11×11-Raster des Spielbretts. */
data class BoardCell(val col: Int, val row: Int)

/**
 * Geometrie des klassischen Kreuz-Bretts. Spieler 0 = links, 1 = oben, 2 = rechts, 3 = unten (Zugrichtung im Uhrzeigersinn).
 */
object MaednBoard {
    const val SIZE = 11

    val NAMES = listOf("Rot", "Blau", "Grün", "Gelb")
    val COLORS = listOf(Color(0xFFD32F2F), Color(0xFF1E63D6), Color(0xFF2E9D3A), Color(0xFFF2C200))

    /** Die 40 Laufbahn-Felder, beginnend beim Startfeld von Spieler 0. */
    val TRACK: List<BoardCell> = buildList {
        // ein Viertel (10 Felder) für Spieler 0, die anderen durch Drehen um 90°
        val quarter = listOf(
            0 to 4, 1 to 4, 2 to 4, 3 to 4, 4 to 4,
            4 to 3, 4 to 2, 4 to 1, 4 to 0, 5 to 0,
        )
        repeat(4) { q -> quarter.forEach { (c, r) -> add(rotate(BoardCell(c, r), q)) } }
    }

    val GOAL: List<List<BoardCell>> = (0 until 4).map { q -> (1..4).map { rotate(BoardCell(it, 5), q) } }

    val HOME: List<List<BoardCell>> = (0 until 4).map { q ->
        listOf(BoardCell(0, 0), BoardCell(1, 0), BoardCell(0, 1), BoardCell(1, 1)).map { rotate(it, q) }
    }

    /** Drehung um 90° im Uhrzeigersinn, q-mal. */
    private fun rotate(c: BoardCell, q: Int): BoardCell {
        var cell = c
        repeat(q) { cell = BoardCell(SIZE - 1 - cell.row, cell.col) }
        return cell
    }

    fun cellOf(player: Int, rel: Int, piece: Int): BoardCell = when {
        rel == MaednLogic.HOME -> HOME[player][piece]
        rel < MaednLogic.TRACK -> TRACK[(MaednLogic.startOf(player) + rel) % MaednLogic.TRACK]
        else -> GOAL[player][rel - MaednLogic.TRACK]
    }
}
