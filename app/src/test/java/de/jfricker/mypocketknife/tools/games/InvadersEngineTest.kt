package de.jfricker.mypocketknife.tools.games

import de.jfricker.mypocketknife.tools.games.invaders.Controls
import de.jfricker.mypocketknife.tools.games.invaders.InvadersEngine
import de.jfricker.mypocketknife.tools.games.invaders.InvadersEngine.Companion.COLS
import de.jfricker.mypocketknife.tools.games.invaders.Sprites
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class InvadersEngineTest {

    private val dt = 1f / 60

    private fun InvadersEngine.run(seconds: Float, controls: Controls = Controls()) {
        repeat((seconds / dt).toInt()) { step(dt, controls) }
    }

    @Test
    fun spritesAreRectangular() {
        val all = Sprites.INVADERS.flatten() + listOf(Sprites.CANNON, Sprites.SAUCER, Sprites.BURST, Sprites.CANNON_BURST, Sprites.BUNKER)
        for (s in all) assertTrue(s.all { it.length == s[0].length })
        Sprites.INVADERS.forEach { frames -> assertEquals(frames[0][0].length, frames[1][0].length) }
    }

    @Test
    fun shootingLastInvaderClearsWave() {
        val e = InvadersEngine(Random(1))
        e.testKillAllBut(setOf(4 * COLS + 5)) // unterste Reihe, Mitte = genau über der Kanone
        var t = 0f
        while (e.score == 0 && t < 2f) {
            e.step(dt, Controls(fire = true))
            t += dt
        }
        assertEquals(10, e.score)
        assertTrue(e.waveTimer > 0f)
        e.run(2f)
        assertEquals(2, e.wave)
        assertEquals(55, e.aliveCount)
    }

    @Test
    fun formationDropsAtEdge() {
        val e = InvadersEngine(Random(2))
        e.testKillAllBut(setOf(10)) // nur oben rechts
        val y0 = e.formationY
        e.run(10f)
        assertTrue(e.formationY > y0)
    }

    @Test
    fun fewerInvadersMoveFaster() {
        val e = InvadersEngine(Random(3))
        val full = e.stepInterval()
        e.testKillAllBut(setOf(0))
        assertTrue(e.stepInterval() < full / 10)
    }

    @Test
    fun enemyBulletCostsALifeAndThreeHitsEndTheGame() {
        val e = InvadersEngine(Random(4))
        repeat(3) { n ->
            e.testAddEnemyBullet(e.playerX, 200f)
            e.run(0.5f)
            assertEquals(2 - n, e.lives)
            e.run(2f)
        }
        assertTrue(e.gameOver)
    }

    @Test
    fun invadersReachingTheGroundEndTheGame() {
        val e = InvadersEngine(Random(5))
        e.testSetFormation(e.formationX, InvadersEngine.PLAYER_Y - 4 * InvadersEngine.CELL_H - InvadersEngine.INV_H)
        e.run(3f)
        assertTrue(e.gameOver)
    }

    @Test
    fun bulletsEatIntoBunkers() {
        val e = InvadersEngine(Random(6))
        val before = e.bunkers[0].count { it }
        e.testAddEnemyBullet(InvadersEngine.BUNKER_X[0] + 11f, 170f)
        e.run(0.6f)
        assertTrue(e.bunkers[0].count { it } < before)
        assertFalse(e.gameOver)
    }

    @Test
    fun dragMovesTowardsFinger() {
        val e = InvadersEngine(Random(7))
        // in den ersten Sekunden kann noch kein Invader-Schuss unten sein
        e.run(0.6f, Controls(targetX = 30f))
        assertEquals(30f, e.playerX, 0.5f)
        e.run(0.5f, Controls(axis = 1f))
        assertEquals(30f + 0.5f * InvadersEngine.PLAYER_SPEED, e.playerX, 1.5f)
    }
}
