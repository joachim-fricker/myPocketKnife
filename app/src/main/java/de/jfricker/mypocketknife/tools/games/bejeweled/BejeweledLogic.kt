package de.jfricker.mypocketknife.tools.games.bejeweled

import de.jfricker.mypocketknife.tools.games.From
import de.jfricker.mypocketknife.tools.games.Grid
import de.jfricker.mypocketknife.tools.games.Piece
import kotlin.random.Random

/**
 * Bejeweled klassisch:
 * - zwei benachbarte Steine tauschen; nur erlaubt, wenn dadurch 3+ gleiche in einer Reihe/Spalte entstehen
 * - Treffer verschwinden, Steine fallen nach, neue kommen von oben; Kettenreaktionen geben Multiplikator
 * - Punkte pro Stein = 10 · Kettenstufe (1, 2, 3 …)
 * - Spielende, wenn kein Zug mehr möglich ist
 */
object BejeweledLogic {
    const val SIZE = 8
    const val COLORS = 7
    const val POINTS_PER_GEM = 10

    /** Vergibt fortlaufende ids für neue Steine. */
    class Ids(private var next: Int = 0) {
        fun next() = next++
    }

    /** Neues Feld ohne fertige Dreier, aber mit mindestens einem möglichen Zug. */
    fun newGrid(random: Random = Random.Default, ids: Ids = Ids()): Grid {
        while (true) {
            val cells = arrayOfNulls<Piece>(SIZE * SIZE)
            for (i in cells.indices) {
                val r = i / SIZE
                val c = i % SIZE
                val forbidden = buildSet {
                    if (c >= 2 && cells[i - 1]?.color == cells[i - 2]?.color) cells[i - 1]?.let { add(it.color) }
                    if (r >= 2 && cells[i - SIZE]?.color == cells[i - 2 * SIZE]?.color) cells[i - SIZE]?.let { add(it.color) }
                }
                val allowed = (0 until COLORS).filter { it !in forbidden }
                cells[i] = Piece(ids.next(), allowed[random.nextInt(allowed.size)])
            }
            val grid = Grid(SIZE, SIZE, cells.toList())
            if (findHint(grid) != null) return grid
        }
    }

    fun swapped(grid: Grid, a: Int, b: Int): Grid {
        val cells = grid.cells.toMutableList()
        cells[a] = grid[b]
        cells[b] = grid[a]
        return Grid(grid.rows, grid.cols, cells)
    }

    /** Alle Felder, die Teil einer waagrechten oder senkrechten Reihe von 3+ gleichen Steinen sind. */
    fun findMatches(grid: Grid): Set<Int> {
        val result = mutableSetOf<Int>()
        fun scan(line: List<Int>) {
            var start = 0
            while (start < line.size) {
                val color = grid[line[start]]?.color
                var end = start + 1
                while (end < line.size && color != null && grid[line[end]]?.color == color) end++
                if (color != null && end - start >= 3) result += line.subList(start, end)
                start = end
            }
        }
        for (r in 0 until grid.rows) scan((0 until grid.cols).map { grid.index(r, it) })
        for (c in 0 until grid.cols) scan((0 until grid.rows).map { grid.index(it, c) })
        return result
    }

    /** Ein möglicher Zug (zwei Felder) oder null, wenn keiner mehr geht. */
    fun findHint(grid: Grid): Pair<Int, Int>? {
        for (i in grid.cells.indices) {
            val r = grid.rowOf(i)
            val c = grid.colOf(i)
            if (c < grid.cols - 1 && findMatches(swapped(grid, i, i + 1)).isNotEmpty()) return i to i + 1
            if (r < grid.rows - 1 && findMatches(swapped(grid, i, i + grid.cols)).isNotEmpty()) return i to i + grid.cols
        }
        return null
    }

    /**
     * Entfernt [matched], lässt die Steine fallen und füllt oben neue auf.
     * Liefert neues Feld + Herkunft aller bewegten bzw. neuen Steine (neue starten oberhalb des Felds).
     */
    fun collapse(grid: Grid, matched: Set<Int>, random: Random, ids: Ids): Pair<Grid, Map<Int, From>> {
        val cells = arrayOfNulls<Piece>(grid.rows * grid.cols)
        val moves = mutableMapOf<Int, From>()
        for (c in 0 until grid.cols) {
            var target = grid.rows - 1
            for (r in grid.rows - 1 downTo 0) {
                val i = grid.index(r, c)
                val piece = grid[i] ?: continue
                if (i in matched) continue
                cells[grid.index(target, c)] = piece
                if (target != r) moves[piece.id] = From(r.toFloat(), c.toFloat())
                target--
            }
            val newCount = target + 1
            for (r in target downTo 0) {
                val piece = Piece(ids.next(), random.nextInt(COLORS))
                cells[grid.index(r, c)] = piece
                moves[piece.id] = From((r - newCount).toFloat(), c.toFloat())
            }
        }
        return Grid(grid.rows, grid.cols, cells.toList()) to moves
    }
}
