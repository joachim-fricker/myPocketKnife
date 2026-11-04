package de.jfricker.mypocketknife.tools.games.asteroids

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Ereignisse eines Schritts (für Ton und Vibration). */
enum class AsSfx { FIRE, BANG_LARGE, BANG_MEDIUM, BANG_SMALL, SHIP_EXPLODE, EXTRA_LIFE, SAUCER_FIRE, BEAT_HIGH, BEAT_LOW, HYPER, THRUST }

data class AsControls(
    val left: Boolean = false,
    val right: Boolean = false,
    val thrust: Boolean = false,
    val fire: Boolean = false,
    val hyper: Boolean = false,
)

/** Fels; size 0 = gross, 1 = mittel, 2 = klein. shape = Radius-Faktoren der Ecken (eigene Zufallsform). */
class Rock(var x: Float, var y: Float, val vx: Float, val vy: Float, val size: Int, val shape: FloatArray, var angle: Float, val spin: Float) {
    val radius get() = AsteroidsEngine.ROCK_RADIUS[size]
}

class Shot(var x: Float, var y: Float, val vx: Float, val vy: Float, var life: Float, val fromSaucer: Boolean)

class Saucer(var x: Float, var y: Float, val vx: Float, var vy: Float, val small: Boolean, var fireTimer: Float, var turnTimer: Float) {
    val radius get() = if (small) 9f else 16f
}

class Particle(var x: Float, var y: Float, val vx: Float, val vy: Float, var life: Float, val maxLife: Float)

/**
 * Asteroids in einer festen Welt von [W] × [H] Einheiten mit Wrap-around an allen Rändern.
 * Schiff mit Trägheit, 4 eigene Schüsse gleichzeitig, Felsen zerfallen gross → mittel → klein (20/50/100),
 * Ufos (gross 200, klein 1000), Hyperraum, 3 Leben, Zusatzleben alle 10000 Punkte, Wellen mit immer mehr Felsen.
 */
class AsteroidsEngine(private val random: Random = Random.Default) {

    companion object {
        const val W = 360f
        const val H = 540f
        val ROCK_RADIUS = floatArrayOf(34f, 18f, 9f)
        val ROCK_POINTS = intArrayOf(20, 50, 100)
        const val SHIP_RADIUS = 9f
        const val TURN_SPEED = 4.0f // rad/s
        const val THRUST = 250f
        const val MAX_SPEED = 280f
        const val SHOT_SPEED = 420f
        const val SHOT_LIFE = 0.85f
        const val MAX_SHOTS = 4
        const val FIRE_COOLDOWN = 0.22f
        const val START_LIVES = 3
        const val EXTRA_LIFE_EVERY = 10000
        const val RESPAWN_TIME = 2f
        const val INVULNERABLE = 2.5f
        const val WAVE_PAUSE = 2f
    }

    var score = 0; private set
    var lives = START_LIVES; private set
    var wave = 0; private set
    var gameOver = false; private set

    // Schiff
    var shipX = W / 2; private set
    var shipY = H / 2; private set
    var shipVx = 0f; private set
    var shipVy = 0f; private set
    /** Winkel in Bogenmass, 0 = nach oben, im Uhrzeigersinn. */
    var shipAngle = 0f; private set
    var shipAlive = true; private set
    var thrusting = false; private set
    var invulnerable = INVULNERABLE; private set
    private var respawnTimer = 0f
    private var fireCooldown = 0f
    private var hyperCooldown = 0f
    private var thrustSoundTimer = 0f

    val rocks = mutableListOf<Rock>()
    val shots = mutableListOf<Shot>()
    var saucer: Saucer? = null; private set
    val particles = mutableListOf<Particle>()
    val events = mutableListOf<AsSfx>()

    var waveTimer = 0f; private set
    private var saucerTimer = 15f
    private var beatTimer = 1f
    private var beatHigh = true
    private var nextExtraLife = EXTRA_LIFE_EVERY

    init {
        nextWave()
    }

    fun shipDirX() = sin(shipAngle)
    fun shipDirY() = -cos(shipAngle)

    // ------------------------------------------------------------------ Ablauf

    fun step(dtIn: Float, c: AsControls) {
        events.clear()
        if (gameOver) return
        val dt = dtIn.coerceIn(0f, 0.05f)

        particles.forEach {
            it.x += it.vx * dt
            it.y += it.vy * dt
            it.life -= dt
        }
        particles.removeAll { it.life <= 0f }

        if (shipAlive) updateShip(dt, c) else updateRespawn(dt)

        rocks.forEach { r ->
            r.x = wrap(r.x + r.vx * dt, W)
            r.y = wrap(r.y + r.vy * dt, H)
            r.angle += r.spin * dt
        }
        shots.forEach { s ->
            s.x = wrap(s.x + s.vx * dt, W)
            s.y = wrap(s.y + s.vy * dt, H)
            s.life -= dt
        }
        shots.removeAll { it.life <= 0f }

        updateSaucer(dt)
        collisions()
        updateBeat(dt)

        if (rocks.isEmpty()) {
            if (waveTimer <= 0f) waveTimer = WAVE_PAUSE
            waveTimer -= dt
            if (waveTimer <= 0f) nextWave()
        }
    }

    private fun updateShip(dt: Float, c: AsControls) {
        if (c.left) shipAngle -= TURN_SPEED * dt
        if (c.right) shipAngle += TURN_SPEED * dt
        thrusting = c.thrust
        if (c.thrust) {
            shipVx += shipDirX() * THRUST * dt
            shipVy += shipDirY() * THRUST * dt
            thrustSoundTimer -= dt
            if (thrustSoundTimer <= 0f) {
                events += AsSfx.THRUST
                thrustSoundTimer = 0.12f
            }
        }
        // leichte Reibung, Höchstgeschwindigkeit
        val damp = 1f - 0.45f * dt
        shipVx *= damp
        shipVy *= damp
        val speed = hypot(shipVx, shipVy)
        if (speed > MAX_SPEED) {
            shipVx *= MAX_SPEED / speed
            shipVy *= MAX_SPEED / speed
        }
        shipX = wrap(shipX + shipVx * dt, W)
        shipY = wrap(shipY + shipVy * dt, H)
        invulnerable -= dt

        fireCooldown -= dt
        if (c.fire && fireCooldown <= 0f && shots.count { !it.fromSaucer } < MAX_SHOTS) {
            shots += Shot(
                shipX + shipDirX() * SHIP_RADIUS, shipY + shipDirY() * SHIP_RADIUS,
                shipVx + shipDirX() * SHOT_SPEED, shipVy + shipDirY() * SHOT_SPEED,
                SHOT_LIFE, fromSaucer = false,
            )
            fireCooldown = FIRE_COOLDOWN
            events += AsSfx.FIRE
        }

        hyperCooldown -= dt
        if (c.hyper && hyperCooldown <= 0f) {
            shipX = 20f + random.nextFloat() * (W - 40f)
            shipY = 20f + random.nextFloat() * (H - 40f)
            shipVx = 0f
            shipVy = 0f
            hyperCooldown = 1.5f
            burst(shipX, shipY, 6, 60f)
            events += AsSfx.HYPER
        }
    }

    private fun updateRespawn(dt: Float) {
        thrusting = false
        respawnTimer -= dt
        if (respawnTimer > 0f) return
        if (lives <= 0) {
            gameOver = true
            return
        }
        // erst wieder auftauchen, wenn die Mitte frei ist
        val clear = rocks.none { dist(it.x, it.y, W / 2, H / 2) < it.radius + 60f } &&
            saucer?.let { dist(it.x, it.y, W / 2, H / 2) > 80f } != false
        if (!clear) return
        shipX = W / 2
        shipY = H / 2
        shipVx = 0f
        shipVy = 0f
        shipAngle = 0f
        shipAlive = true
        invulnerable = INVULNERABLE
    }

    private fun nextWave() {
        wave++
        waveTimer = 0f
        val count = min(4 + wave, 11)
        repeat(count) {
            // am Rand verteilt, nicht beim Schiff
            var x: Float
            var y: Float
            do {
                if (random.nextBoolean()) {
                    x = random.nextFloat() * W
                    y = if (random.nextBoolean()) 0f else H - 1f
                } else {
                    x = if (random.nextBoolean()) 0f else W - 1f
                    y = random.nextFloat() * H
                }
            } while (dist(x, y, shipX, shipY) < 120f)
            rocks += newRock(x, y, 0)
        }
        saucerTimer = 14f + random.nextFloat() * 8f
        shots.removeAll { it.fromSaucer }
    }

    private fun newRock(x: Float, y: Float, size: Int): Rock {
        val speedRange = when (size) {
            0 -> 25f..55f
            1 -> 45f..85f
            else -> 65f..115f
        }
        val waveFactor = min(1.6f, 1f + 0.06f * (wave - 1))
        val speed = (speedRange.start + random.nextFloat() * (speedRange.endInclusive - speedRange.start)) * waveFactor
        val dir = random.nextFloat() * 2f * PI.toFloat()
        val corners = 10 + random.nextInt(3)
        val shape = FloatArray(corners) { 0.72f + random.nextFloat() * 0.38f }
        return Rock(x, y, cos(dir) * speed, sin(dir) * speed, size, shape, random.nextFloat() * 6.28f, (random.nextFloat() - 0.5f) * 1.6f)
    }

    // ------------------------------------------------------------------ Ufo

    private fun updateSaucer(dt: Float) {
        val s = saucer
        if (s == null) {
            if (waveTimer > 0f) return
            saucerTimer -= dt
            if (saucerTimer <= 0f) spawnSaucer()
            return
        }
        s.x += s.vx * dt
        s.y = wrap(s.y + s.vy * dt, H)
        s.turnTimer -= dt
        if (s.turnTimer <= 0f) {
            s.vy = listOf(-45f, 0f, 45f)[random.nextInt(3)]
            s.turnTimer = 1f + random.nextFloat() * 1.5f
        }
        if (s.x < -30f || s.x > W + 30f) {
            saucer = null
            saucerTimer = 12f + random.nextFloat() * 8f
            return
        }
        s.fireTimer -= dt
        if (s.fireTimer <= 0f) {
            val angle = if (s.small && shipAlive) {
                // gezielt, mit etwas Streuung (wird mit der Punktzahl genauer)
                val spread = (0.5f - score / 80000f).coerceIn(0.08f, 0.5f)
                atan2(shipY - s.y, shipX - s.x) + (random.nextFloat() - 0.5f) * spread
            } else random.nextFloat() * 2f * PI.toFloat()
            shots += Shot(s.x, s.y, cos(angle) * 260f, sin(angle) * 260f, 1.2f, fromSaucer = true)
            s.fireTimer = if (s.small) 0.9f else 1.3f
            events += AsSfx.SAUCER_FIRE
        }
    }

    private fun spawnSaucer() {
        val smallChance = when {
            score < 5000 -> 0.15f
            score < 15000 -> 0.4f
            else -> 0.7f
        }
        val small = random.nextFloat() < smallChance
        val fromLeft = random.nextBoolean()
        val speed = if (small) 95f else 70f
        saucer = Saucer(
            if (fromLeft) -20f else W + 20f,
            40f + random.nextFloat() * (H - 80f),
            if (fromLeft) speed else -speed, 0f, small, 1f, 1.5f,
        )
    }

    // ------------------------------------------------------------------ Treffer

    private fun collisions() {
        // Schüsse gegen Felsen und Ufo
        val shotIt = shots.iterator()
        while (shotIt.hasNext()) {
            val s = shotIt.next()
            val rock = rocks.firstOrNull { dist(it.x, it.y, s.x, s.y) < it.radius }
            if (rock != null) {
                shotIt.remove()
                breakRock(rock, byPlayer = !s.fromSaucer)
                continue
            }
            val sc = saucer
            if (!s.fromSaucer && sc != null && dist(sc.x, sc.y, s.x, s.y) < sc.radius) {
                shotIt.remove()
                destroySaucer(sc, award = true)
                continue
            }
            if (s.fromSaucer && shipAlive && invulnerable <= 0f && dist(shipX, shipY, s.x, s.y) < SHIP_RADIUS) {
                shotIt.remove()
                killShip()
            }
        }
        // Ufo gegen Felsen
        saucer?.let { sc ->
            val rock = rocks.firstOrNull { dist(it.x, it.y, sc.x, sc.y) < it.radius + sc.radius * 0.8f }
            if (rock != null) {
                breakRock(rock, byPlayer = false)
                destroySaucer(sc, award = false)
            }
        }
        // Schiff gegen Felsen und Ufo
        if (shipAlive && invulnerable <= 0f) {
            val rock = rocks.firstOrNull { dist(it.x, it.y, shipX, shipY) < it.radius + SHIP_RADIUS * 0.8f }
            if (rock != null) {
                breakRock(rock, byPlayer = true)
                killShip()
                return
            }
            saucer?.let { sc ->
                if (dist(sc.x, sc.y, shipX, shipY) < sc.radius + SHIP_RADIUS * 0.8f) {
                    destroySaucer(sc, award = true)
                    killShip()
                }
            }
        }
    }

    private fun breakRock(r: Rock, byPlayer: Boolean) {
        rocks.remove(r)
        if (byPlayer) addScore(ROCK_POINTS[r.size])
        events += when (r.size) {
            0 -> AsSfx.BANG_LARGE
            1 -> AsSfx.BANG_MEDIUM
            else -> AsSfx.BANG_SMALL
        }
        burst(r.x, r.y, 8 + (2 - r.size) * 4, 90f)
        if (r.size < 2) repeat(2) { rocks += newRock(r.x, r.y, r.size + 1) }
    }

    private fun destroySaucer(s: Saucer, award: Boolean) {
        if (award) addScore(if (s.small) 1000 else 200)
        burst(s.x, s.y, 14, 110f)
        events += AsSfx.BANG_MEDIUM
        saucer = null
        saucerTimer = 12f + random.nextFloat() * 8f
    }

    private fun killShip() {
        shipAlive = false
        thrusting = false
        lives--
        respawnTimer = RESPAWN_TIME
        burst(shipX, shipY, 22, 120f)
        events += AsSfx.SHIP_EXPLODE
    }

    private fun addScore(points: Int) {
        score += points
        if (score >= nextExtraLife) {
            lives++
            nextExtraLife += EXTRA_LIFE_EVERY
            events += AsSfx.EXTRA_LIFE
        }
    }

    /** "Herzschlag" wie beim Original: wird schneller, je weniger Felsen übrig sind. */
    private fun updateBeat(dt: Float) {
        if (rocks.isEmpty() || !shipAlive) return
        beatTimer -= dt
        if (beatTimer > 0f) return
        events += if (beatHigh) AsSfx.BEAT_HIGH else AsSfx.BEAT_LOW
        beatHigh = !beatHigh
        val n = rocks.size
        beatTimer = (0.28f + 0.07f * n).coerceAtMost(1f)
    }

    private fun burst(x: Float, y: Float, n: Int, speed: Float) {
        repeat(n) {
            val a = random.nextFloat() * 2f * PI.toFloat()
            val v = speed * (0.3f + random.nextFloat() * 0.7f)
            val life = 0.4f + random.nextFloat() * 0.6f
            particles += Particle(x, y, cos(a) * v, sin(a) * v, life, life)
        }
    }

    private fun wrap(v: Float, max: Float) = ((v % max) + max) % max

    /** Abstand mit Wrap-around (kürzester Weg über den Rand). */
    fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        var dx = kotlin.math.abs(x1 - x2)
        var dy = kotlin.math.abs(y1 - y2)
        if (dx > W / 2) dx = W - dx
        if (dy > H / 2) dy = H - dy
        return hypot(dx, dy)
    }

    // ------------------------------------------------------------------ für Tests

    internal fun testClearRocks() = rocks.clear()

    internal fun testAddRock(x: Float, y: Float, size: Int) {
        rocks += Rock(x, y, 0f, 0f, size, FloatArray(10) { 1f }, 0f, 0f)
    }

    internal fun testSetShip(x: Float, y: Float, angle: Float) {
        shipX = x
        shipY = y
        shipAngle = angle
        shipVx = 0f
        shipVy = 0f
        invulnerable = 0f
    }
}
