package de.jfricker.mypocketknife.tools.games

import de.jfricker.mypocketknife.tools.games.snake.Cell
import de.jfricker.mypocketknife.tools.games.snake.Dir
import de.jfricker.mypocketknife.tools.games.snake.SnakeGame
import de.jfricker.mypocketknife.tools.games.snake.SnakeLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SnakeLogicTest {

    private val rnd = Random(1)

    @Test
    fun movesAndCannotReverse() {
        val g = SnakeGame(listOf(Cell(5, 5), Cell(4, 5), Cell(3, 5)), Dir.RIGHT, Cell(0, 0))
        val g1 = SnakeLogic.step(g, Dir.LEFT, rnd) // Umkehren wird ignoriert
        assertEquals(listOf(Cell(6, 5), Cell(5, 5), Cell(4, 5)), g1.body)
        val g2 = SnakeLogic.step(g1, Dir.UP, rnd)
        assertEquals(Cell(6, 4), g2.body.first())
        assertEquals(Dir.UP, g2.dir)
    }

    @Test
    fun eatingGrowsAndScores() {
        val g = SnakeGame(listOf(Cell(5, 5), Cell(4, 5), Cell(3, 5)), Dir.RIGHT, Cell(6, 5))
        val g1 = SnakeLogic.step(g, null, rnd)
        assertEquals(4, g1.body.size)
        assertEquals(10, g1.score)
        assertTrue(g1.alive)
        assertFalse(g1.food in g1.body)
        assertNotEquals(Cell(6, 5), g1.food)
    }

    @Test
    fun wallKills() {
        val g = SnakeGame(listOf(Cell(SnakeLogic.COLS - 1, 0), Cell(SnakeLogic.COLS - 2, 0)), Dir.RIGHT, Cell(0, 5))
        assertFalse(SnakeLogic.step(g, null, rnd).alive)
        assertFalse(SnakeLogic.step(g, Dir.UP, rnd).alive)
    }

    @Test
    fun selfKillsButFollowingTheTailIsFine() {
        // Quadrat aus 4 Gliedern: Kopf folgt dem Schwanz → erlaubt
        val square = SnakeGame(listOf(Cell(5, 5), Cell(5, 6), Cell(6, 6), Cell(6, 5)), Dir.UP, Cell(0, 0))
        assertTrue(SnakeLogic.step(square, Dir.RIGHT, rnd).alive)
        // 5 Glieder: Kopf läuft in den Körper
        val longer = SnakeGame(listOf(Cell(5, 5), Cell(5, 6), Cell(6, 6), Cell(6, 5), Cell(6, 4)), Dir.UP, Cell(0, 0))
        assertFalse(SnakeLogic.step(longer, Dir.RIGHT, rnd).alive)
    }

    @Test
    fun speedsUpButNotBelowMinimum() {
        assertEquals(180L, SnakeLogic.interval(0))
        assertTrue(SnakeLogic.interval(10) < 180L)
        assertEquals(70L, SnakeLogic.interval(500))
    }
}
