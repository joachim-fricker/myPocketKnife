package de.jfricker.mypocketknife.tools.games.snake

import kotlin.random.Random

data class Cell(val x: Int, val y: Int)

enum class Dir(val dx: Int, val dy: Int) {
    UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

    val opposite: Dir
        get() = when (this) {
            UP -> DOWN
            DOWN -> UP
            LEFT -> RIGHT
            RIGHT -> LEFT
        }
}

/** Unveränderlicher Spielstand. body[0] = Kopf. */
data class SnakeGame(
    val body: List<Cell>,
    val dir: Dir,
    val food: Cell?,
    val eaten: Int = 0,
    val alive: Boolean = true,
) {
    val score get() = eaten * SnakeLogic.POINTS
}

/**
 * Klassisches Snake: Rand und eigener Körper sind tödlich, jedes Futter = +10 Punkte und ein Glied länger,
 * das Tempo steigt langsam mit der Länge.
 */
object SnakeLogic {
    const val COLS = 16
    const val ROWS = 20
    const val POINTS = 10

    fun newGame(random: Random = Random.Default): SnakeGame {
        val y = ROWS / 2
        val body = listOf(Cell(5, y), Cell(4, y), Cell(3, y))
        return SnakeGame(body, Dir.RIGHT, randomFree(body, random))
    }

    /** Millisekunden pro Schritt: von 180 langsam bis 70. */
    fun interval(eaten: Int): Long = (180 - 4L * eaten).coerceAtLeast(70)

    fun step(game: SnakeGame, turn: Dir?, random: Random = Random.Default): SnakeGame {
        if (!game.alive) return game
        val dir = if (turn != null && turn != game.dir.opposite) turn else game.dir
        val head = game.body.first()
        val next = Cell(head.x + dir.dx, head.y + dir.dy)
        val eats = next == game.food
        // Schwanzende räumt sein Feld im selben Schritt (ausser beim Fressen)
        val obstacles = if (eats) game.body else game.body.dropLast(1)
        if (next.x !in 0 until COLS || next.y !in 0 until ROWS || next in obstacles) {
            return game.copy(dir = dir, alive = false)
        }
        val body = listOf(next) + obstacles
        return if (eats) {
            val food = randomFree(body, random)
            game.copy(body = body, dir = dir, food = food, eaten = game.eaten + 1, alive = food != null)
        } else game.copy(body = body, dir = dir)
    }

    /** Zufälliges freies Feld, null wenn das Feld voll ist. */
    fun randomFree(body: List<Cell>, random: Random): Cell? {
        val taken = body.toHashSet()
        val free = (0 until COLS * ROWS).map { Cell(it % COLS, it / COLS) }.filter { it !in taken }
        return if (free.isEmpty()) null else free[random.nextInt(free.size)]
    }
}
