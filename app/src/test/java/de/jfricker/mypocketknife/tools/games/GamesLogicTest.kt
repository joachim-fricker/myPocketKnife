package de.jfricker.mypocketknife.tools.games

import de.jfricker.mypocketknife.tools.games.bejeweled.BejeweledLogic
import de.jfricker.mypocketknife.tools.games.samegame.SameGameLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GamesLogicTest {

    /** Feld aus Text: Ziffer = Farbe, '.' = leer. */
    private fun grid(vararg lines: String): Grid {
        var id = 0
        val cells = lines.flatMap { line -> line.map { ch -> if (ch == '.') null.also { id++ } else Piece(id++, ch - '0') } }
        return Grid(lines.size, lines[0].length, cells)
    }

    private fun Grid.text(): List<String> =
        (0 until rows).map { r -> (0 until cols).joinToString("") { c -> this[r, c]?.color?.toString() ?: "." } }

    // ---------- Bestenliste ----------

    @Test
    fun scoreBoardKeepsBestFive() {
        var list = emptyList<ScoreEntry>()
        listOf(50, 10, 80, 30, 60, 20).forEachIndexed { i, s ->
            list = ScoreBoard.insert(list, ScoreEntry(s, i.toLong())).first
        }
        assertEquals(listOf(80, 60, 50, 30, 20), list.map { it.score })
        val (same, rank) = ScoreBoard.insert(list, ScoreEntry(5, 99))
        assertNull(rank)
        assertEquals(list, same)
        val (_, rank2) = ScoreBoard.insert(list, ScoreEntry(60, 99))
        assertEquals(3, rank2) // gleiche Punkte: älteres Ergebnis bleibt vorne
        assertNull(ScoreBoard.insert(emptyList(), ScoreEntry(0, 1)).second)
        assertEquals(1, ScoreBoard.insert(emptyList(), ScoreEntry(1, 1)).second)
    }

    @Test
    fun scoreBoardLowerIsBetter() {
        var list = emptyList<ScoreEntry>()
        listOf(30, 25, 40, 25).forEachIndexed { i, r ->
            list = ScoreBoard.insert(list, ScoreEntry(r, i.toLong()), lowerIsBetter = true).first
        }
        assertEquals(listOf(25, 25, 30, 40), list.map { it.score })
        assertEquals(listOf(1L, 3L), list.take(2).map { it.at }) // gleiche Runden: älteres vorne
        assertEquals(1, ScoreBoard.insert(list, ScoreEntry(20, 9), lowerIsBetter = true).second)
    }

    // ---------- SameGame ----------

    @Test
    fun sameGameGroupAndPoints() {
        val g = grid(
            "112",
            "122",
            "333",
        )
        assertEquals(setOf(0, 1, 3), SameGameLogic.group(g, 0))
        assertEquals(setOf(2, 4, 5), SameGameLogic.group(g, 5))
        assertEquals(6, SameGameLogic.points(3))
        assertEquals(0, SameGameLogic.points(1))
        assertTrue(SameGameLogic.hasMoves(g))
        assertFalse(SameGameLogic.hasMoves(grid("12", "21")))
    }

    @Test
    fun sameGameGravityAndColumnShift() {
        val g = grid(
            "1.2",
            "132",
            "142",
        )
        // Gruppe "2" (rechte Spalte) entfernen → Spalte leer → Rest rückt nach rechts
        val (after, moves) = SameGameLogic.remove(g, SameGameLogic.group(g, 2))
        val expected = if (SameGameLogic.SHIFT_RIGHT) listOf(".1.", ".13", ".14") else listOf("1..", "13.", "14.")
        assertEquals(expected, after.text())
        if (SameGameLogic.SHIFT_RIGHT) {
            // Stein oben links (id 0) wandert von (0,0) nach (0,1)
            assertEquals(From(0f, 0f), moves[0])
        }
        // Mitte unten "4" entfernen → "3" fällt
        val g2 = grid("1.2", "132", "142")
        val (after2, moves2) = SameGameLogic.remove(g2, setOf(7))
        assertEquals(listOf("1.2", "1.2", "132"), after2.text())
        assertEquals(From(1f, 1f), moves2[4])
    }

    @Test
    fun sameGameRandomGamesTerminate() {
        val rnd = Random(1)
        repeat(50) {
            var g = SameGameLogic.newGrid(rnd)
            var guard = 0
            while (SameGameLogic.hasMoves(g)) {
                val start = g.cells.indices.first { i -> g[i] != null && SameGameLogic.group(g, i).size >= 2 }
                val grp = SameGameLogic.group(g, start)
                val before = g.pieceCount
                g = SameGameLogic.remove(g, grp).first
                assertEquals(before - grp.size, g.pieceCount)
                // keine "schwebenden" Steine
                for (c in 0 until g.cols) for (r in 0 until g.rows - 1) {
                    if (g[r, c] != null) assertNotNull(g[r + 1, c])
                }
                assertTrue(++guard < 200)
            }
        }
    }

    // ---------- Bejeweled ----------

    @Test
    fun bejeweledMatches() {
        val g = grid(
            "1112",
            "3456",
            "3456",
            "3000",
        )
        assertEquals(setOf(0, 1, 2, 4, 8, 12, 13, 14, 15), BejeweledLogic.findMatches(g))
    }

    @Test
    fun bejeweledNewGridHasNoMatchesButAMove() {
        val rnd = Random(7)
        repeat(200) {
            val g = BejeweledLogic.newGrid(rnd)
            assertTrue(BejeweledLogic.findMatches(g).isEmpty())
            assertNotNull(BejeweledLogic.findHint(g))
            assertEquals(64, g.cells.map { it!!.id }.toSet().size)
        }
    }

    @Test
    fun bejeweledHint() {
        val g = grid(
            "1211",
            "4564",
            "5645",
        )
        assertEquals(0 to 1, BejeweledLogic.findHint(g))
        assertNull(BejeweledLogic.findHint(grid("123", "456", "123")))
    }

    @Test
    fun bejeweledCollapse() {
        val g = grid(
            "123",
            "456",
            "111",
        )
        val ids = BejeweledLogic.Ids(100)
        val (after, moves) = BejeweledLogic.collapse(g, setOf(6, 7, 8), Random(3), ids)
        // alte Steine fallen um eine Zeile
        assertEquals(listOf("123", "456"), after.text().drop(1))
        assertEquals(From(0f, 0f), moves[0])
        assertEquals(From(1f, 0f), moves[3])
        // neue Steine kommen von oberhalb
        val newPiece = after[0, 0]!!
        assertTrue(newPiece.id >= 100)
        assertEquals(From(-1f, 0f), moves[newPiece.id])
        assertTrue(after.cells.all { it != null })
    }
}
