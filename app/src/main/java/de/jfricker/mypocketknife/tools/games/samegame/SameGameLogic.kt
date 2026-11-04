package de.jfricker.mypocketknife.tools.games.samegame

import de.jfricker.mypocketknife.tools.games.From
import de.jfricker.mypocketknife.tools.games.Grid
import de.jfricker.mypocketknife.tools.games.Piece
import kotlin.random.Random

/**
 * SameGame im Jawbreaker-Stil:
 * - Gruppen ab 2 gleichfarbigen, waagrecht/senkrecht verbundenen Steinen können entfernt werden
 * - Punkte = n · (n − 1)
 * - Steine fallen nach unten, leere Spalten werden geschlossen (Steine rücken nach rechts, wie bei Jawbreaker)
 * - Spielende, wenn keine Gruppe mehr da ist; leeres Feld gibt [CLEAR_BONUS]
 */
object SameGameLogic {
    const val COLS = 10
    const val ROWS = 12
    const val COLORS = 5
    const val CLEAR_BONUS = 1000

    /** true = Spalten rücken nach rechts zusammen (Jawbreaker), false = nach links (klassisches SameGame). */
    const val SHIFT_RIGHT = true

    fun newGrid(random: Random = Random.Default): Grid =
        Grid(ROWS, COLS, List(ROWS * COLS) { Piece(it, random.nextInt(COLORS)) })

    fun points(groupSize: Int): Int = if (groupSize < 2) 0 else groupSize * (groupSize - 1)

    /** Alle mit [start] verbundenen Felder gleicher Farbe (leer, wenn das Feld leer ist). */
    fun group(grid: Grid, start: Int): Set<Int> {
        val color = grid[start]?.color ?: return emptySet()
        val result = mutableSetOf(start)
        val todo = ArrayDeque(listOf(start))
        while (todo.isNotEmpty()) {
            val i = todo.removeFirst()
            for (n in grid.neighbours(i)) {
                if (n !in result && grid[n]?.color == color) {
                    result += n
                    todo += n
                }
            }
        }
        return result
    }

    fun hasMoves(grid: Grid): Boolean =
        grid.cells.indices.any { i ->
            val p = grid[i] ?: return@any false
            grid.neighbours(i).any { grid[it]?.color == p.color }
        }

    /** Entfernt [group], lässt Steine fallen und schliesst leere Spalten. Liefert neues Feld + Herkunft bewegter Steine. */
    fun remove(grid: Grid, group: Set<Int>): Pair<Grid, Map<Int, From>> {
        val rows = grid.rows
        val cols = grid.cols
        // Spalten von unten nach oben einsammeln (ohne die entfernten Steine)
        val columns: List<List<Pair<Piece, Int>>> = (0 until cols).map { c ->
            (rows - 1 downTo 0).mapNotNull { r ->
                val i = grid.index(r, c)
                if (i in group) null else grid[i]?.let { it to i }
            }
        }
        val filled = columns.filter { it.isNotEmpty() }
        val empty = cols - filled.size
        val cells = arrayOfNulls<Piece>(rows * cols)
        val moves = mutableMapOf<Int, From>()
        filled.forEachIndexed { k, column ->
            val c = if (SHIFT_RIGHT) empty + k else k
            column.forEachIndexed { h, (piece, oldIndex) ->
                val newIndex = grid.index(rows - 1 - h, c)
                cells[newIndex] = piece
                if (newIndex != oldIndex) {
                    moves[piece.id] = From(grid.rowOf(oldIndex).toFloat(), grid.colOf(oldIndex).toFloat())
                }
            }
        }
        return Grid(rows, cols, cells.toList()) to moves
    }
}
