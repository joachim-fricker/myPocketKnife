package de.jfricker.mypocketknife.tools.games

import de.jfricker.mypocketknife.tools.games.asteroids.AsControls
import de.jfricker.mypocketknife.tools.games.asteroids.AsteroidsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AsteroidsEngineTest {

    private val dt = 1f / 60

    private fun AsteroidsEngine.run(seconds: Float, c: AsControls = AsControls()) {
        repeat((seconds / dt).toInt()) { step(dt, c) }
    }

    private fun AsteroidsEngine.runUntil(maxSeconds: Float, cond: () -> Boolean) {
        var t = 0f
        while (!cond() && t < maxSeconds) {
            step(dt, AsControls())
            t += dt
        }
    }

    @Test
    fun firstWaveHasFiveLargeRocksAwayFromShip() {
        val e = AsteroidsEngine(Random(1))
        assertEquals(1, e.wave)
        assertEquals(5, e.rocks.size)
        assertTrue(e.rocks.all { it.size == 0 && e.dist(it.x, it.y, e.shipX, e.shipY) >= 120f })
    }

    @Test
    fun shootingLargeRockSplitsIt() {
        val e = AsteroidsEngine(Random(2))
        e.testClearRocks()
        e.testAddRock(AsteroidsEngine.W / 2, 150f, 0)
        e.testSetShip(AsteroidsEngine.W / 2, 300f, 0f)
        var t = 0f
        while (e.score == 0 && t < 1f) {
            e.step(dt, AsControls(fire = true))
            t += dt
        }
        assertEquals(20, e.score)
        assertEquals(2, e.rocks.size)
        assertTrue(e.rocks.all { it.size == 1 })
    }

    @Test
    fun lastSmallRockStartsNextWave() {
        val e = AsteroidsEngine(Random(3))
        e.testClearRocks()
        e.testAddRock(AsteroidsEngine.W / 2, 200f, 2)
        e.testSetShip(AsteroidsEngine.W / 2, 300f, 0f)
        var t = 0f
        while (e.score == 0 && t < 1f) {
            e.step(dt, AsControls(fire = true))
            t += dt
        }
        assertEquals(100, e.score)
        assertTrue(e.rocks.isEmpty())
        e.run(AsteroidsEngine.WAVE_PAUSE + 0.2f)
        assertEquals(2, e.wave)
        assertEquals(6, e.rocks.size)
    }

    @Test
    fun rockCollisionCostsLifeAndThreeEndTheGame() {
        val e = AsteroidsEngine(Random(4))
        repeat(3) { n ->
            e.runUntil(20f) { e.shipAlive }
            assertTrue(e.shipAlive)
            e.testSetShip(100f, 100f, 0f)
            e.testAddRock(100f, 100f, 2)
            e.step(dt, AsControls())
            assertFalse(e.shipAlive)
            assertEquals(2 - n, e.lives)
        }
        e.run(AsteroidsEngine.RESPAWN_TIME + 0.5f)
        assertTrue(e.gameOver)
    }

    @Test
    fun shipKeepsMomentumAndWrapsAround() {
        val e = AsteroidsEngine(Random(5))
        e.testClearRocks()
        e.testAddRock(10f, 10f, 0) // damit keine neue Welle startet
        e.testSetShip(AsteroidsEngine.W / 2, 300f, 0f)
        e.run(0.5f, AsControls(thrust = true))
        val vy = e.shipVy
        assertTrue(vy < -50f) // nach oben
        e.run(0.2f)
        assertTrue(e.shipVy < 0f && e.shipVy > vy) // gleitet weiter, etwas langsamer
        assertEquals(2.83f, e.dist(1f, 1f, AsteroidsEngine.W - 1f, AsteroidsEngine.H - 1f), 0.01f)
    }

    @Test
    fun rotationTurnsShip() {
        val e = AsteroidsEngine(Random(6))
        e.testSetShip(100f, 100f, 0f)
        e.step(0.05f, AsControls(right = true))
        assertEquals(0.2f, e.shipAngle, 0.001f)
    }
}
