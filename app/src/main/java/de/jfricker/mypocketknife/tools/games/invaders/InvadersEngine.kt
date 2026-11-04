package de.jfricker.mypocketknife.tools.games.invaders

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Ereignisse eines Spielschritts (für Ton und Vibration). */
enum class Sfx { SHOT, INVADER_HIT, PLAYER_HIT, UFO_HIT, STEP, EXTRA_LIFE }

/** Steuerung für einen Schritt. axis −1…1 (Kippen/Knöpfe) oder targetX (Finger, Weltkoordinate). */
data class Controls(val axis: Float = 0f, val targetX: Float? = null, val fire: Boolean = false)

class Bullet(var x: Float, var y: Float)

class Effect(val x: Float, val y: Float, var time: Float, val text: String? = null)

/**
 * Spiellogik in einer festen "Welt" von [W] × [H] Einheiten (wie der alte Automat), unabhängig vom Bildschirm.
 * Pro Bildschirm-Frame wird [step] mit der vergangenen Zeit aufgerufen.
 *
 * Regeln: 5 × 11 Invader (30/20/10 Punkte), die Formation wird schneller, je weniger übrig sind,
 * am Rand eine Reihe tiefer. Ein eigener Schuss gleichzeitig, 4 Bunker, ab und zu ein Ufo (50–300).
 * 3 Leben, Zusatzleben bei 1500 Punkten. Ende, wenn keine Leben mehr da sind oder die Invader unten ankommen.
 */
class InvadersEngine(private val random: Random = Random.Default) {

    companion object {
        const val W = 224f
        const val H = 256f
        const val COLS = 11
        const val ROWS = 5
        const val CELL_W = 16f
        const val CELL_H = 16f
        const val INV_H = 8f
        const val PLAYER_Y = 216f
        const val PLAYER_W = 13f
        const val PLAYER_H = 8f
        const val GROUND_Y = 236f
        const val BUNKER_Y = 184f
        val BUNKER_X = floatArrayOf(34f, 79f, 123f, 168f)
        val BUNKER_W = Sprites.width(Sprites.BUNKER)
        val BUNKER_H = Sprites.height(Sprites.BUNKER)
        const val UFO_Y = 26f
        val UFO_W = Sprites.width(Sprites.SAUCER).toFloat()
        val UFO_H = Sprites.height(Sprites.SAUCER).toFloat()
        const val START_LIVES = 3
        const val EXTRA_LIFE_AT = 1500
        const val PLAYER_SPEED = 90f
        const val DRAG_SPEED = 170f
        const val SHOT_SPEED = 260f
        const val DYING_TIME = 1.5f
        const val WAVE_PAUSE = 1.6f
        private val UFO_POINTS = intArrayOf(50, 100, 100, 150, 150, 300)

        fun rowType(row: Int) = when (row) {
            0 -> 0
            1, 2 -> 1
            else -> 2
        }

        fun rowPoints(row: Int) = when (rowType(row)) {
            0 -> 30
            1 -> 20
            else -> 10
        }

        fun invaderWidth(row: Int) = Sprites.width(Sprites.INVADERS[rowType(row)][0]).toFloat()
    }

    var score = 0; private set
    var lives = START_LIVES; private set
    var wave = 1; private set
    var gameOver = false; private set

    val alive = BooleanArray(ROWS * COLS)
    var formationX = 0f; private set
    var formationY = 0f; private set
    var animFrame = 0; private set
    private var dir = 1
    private var stepTimer = 0f

    var playerX = W / 2; private set
    var playerBullet: Bullet? = null; private set
    val enemyBullets = mutableListOf<Bullet>()
    private var fireTimer = 1.5f

    /** > 0: Kanone explodiert gerade (alles steht still). */
    var dyingTimer = 0f; private set

    /** > 0: Pause zwischen zwei Wellen. */
    var waveTimer = 0f; private set

    var ufoX: Float? = null; private set
    private var ufoDir = 1
    private var ufoTimer = 22f

    /** Explosionen der Invader und Punkte-Anzeige des Ufos. */
    val effects = mutableListOf<Effect>()

    val bunkers = Array(BUNKER_X.size) { BooleanArray(BUNKER_W * BUNKER_H) }

    /** Wird bei jeder Bunker-Änderung erhöht (zum Neuzeichnen). */
    var bunkerVersion = 0; private set

    private var extraLifeGiven = false
    private var stepSound = 0

    /** Ereignisse des letzten Schritts. */
    val events = mutableListOf<Sfx>()

    val aliveCount get() = alive.count { it }

    init {
        startWave()
    }

    fun invaderLeft(row: Int, col: Int) = formationX + col * CELL_W + (CELL_W - invaderWidth(row)) / 2
    fun invaderTop(row: Int) = formationY + row * CELL_H

    // ------------------------------------------------------------------ Ablauf

    fun step(dtIn: Float, controls: Controls) {
        events.clear()
        if (gameOver) return
        val dt = dtIn.coerceIn(0f, 0.05f)

        effects.forEach { it.time -= dt }
        effects.removeAll { it.time <= 0f }

        if (waveTimer > 0f) {
            waveTimer -= dt
            if (waveTimer <= 0f) {
                wave++
                startWave()
            }
            return
        }
        if (dyingTimer > 0f) {
            dyingTimer -= dt
            if (dyingTimer <= 0f) {
                if (lives <= 0) gameOver = true else playerX = W / 2
            }
            return
        }

        movePlayer(dt, controls)
        if (controls.fire && playerBullet == null) {
            playerBullet = Bullet(playerX, PLAYER_Y - 4f)
            events += Sfx.SHOT
        }
        movePlayerBullet(dt)
        if (waveTimer > 0f) return
        moveEnemyBullets(dt)
        if (dyingTimer > 0f) return

        stepTimer -= dt
        if (stepTimer <= 0f) {
            stepFormation()
            stepTimer = stepInterval()
        }
        enemyFire(dt)
        moveUfo(dt)
    }

    private fun startWave() {
        alive.fill(true)
        formationX = (W - COLS * CELL_W) / 2
        formationY = 48f + 8f * min(wave - 1, 5)
        dir = 1
        stepTimer = 0.5f
        animFrame = 0
        playerBullet = null
        enemyBullets.clear()
        fireTimer = 1.5f
        ufoX = null
        ufoTimer = 18f + random.nextFloat() * 8f
        playerX = W / 2
        resetBunkers()
    }

    private fun resetBunkers() {
        for (b in bunkers) {
            for (y in 0 until BUNKER_H) for (x in 0 until BUNKER_W) {
                b[y * BUNKER_W + x] = Sprites.BUNKER[y][x] == '#'
            }
        }
        bunkerVersion++
    }

    /** Sekunden zwischen zwei Formationsschritten: je weniger Invader, desto schneller. */
    fun stepInterval(): Float {
        val waveFactor = max(0.7f, 1f - 0.05f * (wave - 1))
        return (0.016f + 0.6f * aliveCount / (ROWS * COLS)) * waveFactor
    }

    private fun movePlayer(dt: Float, c: Controls) {
        val target = c.targetX
        playerX += if (target != null) (target - playerX).coerceIn(-DRAG_SPEED * dt, DRAG_SPEED * dt)
        else c.axis.coerceIn(-1f, 1f) * PLAYER_SPEED * dt
        playerX = playerX.coerceIn(PLAYER_W / 2 + 4f, W - PLAYER_W / 2 - 4f)
    }

    // ------------------------------------------------------------------ Formation

    private fun stepFormation() {
        var minCol = COLS
        var maxCol = -1
        for (i in alive.indices) if (alive[i]) {
            minCol = min(minCol, i % COLS)
            maxCol = max(maxCol, i % COLS)
        }
        if (maxCol < 0) return
        val left = formationX + minCol * CELL_W + 2f
        val right = formationX + (maxCol + 1) * CELL_W - 2f
        if ((dir > 0 && right + 2f > W - 4f) || (dir < 0 && left - 2f < 4f)) {
            formationY += 8f
            dir = -dir
        } else {
            formationX += 2f * dir
        }
        animFrame = 1 - animFrame
        events += Sfx.STEP
        stepSound++

        // Invader fressen sich durch die Bunker
        for (i in alive.indices) if (alive[i]) {
            val r = i / COLS
            val c = i % COLS
            clearBunkerRect(invaderLeft(r, c), invaderTop(r), invaderWidth(r), INV_H)
        }
        // unten angekommen?
        var maxRow = -1
        for (i in alive.indices) if (alive[i]) maxRow = max(maxRow, i / COLS)
        if (invaderTop(maxRow) + INV_H >= PLAYER_Y) {
            lives = 0
            killPlayer()
        }
    }

    /** Ton-Index 0…3 für den Marschtakt. */
    val stepNote get() = stepSound % 4

    private fun enemyFire(dt: Float) {
        fireTimer -= dt
        val maxBullets = if (wave == 1) 2 else 3
        if (fireTimer > 0f || enemyBullets.size >= maxBullets) return
        val columns = (0 until COLS).filter { c -> (0 until ROWS).any { r -> alive[r * COLS + c] } }
        if (columns.isEmpty()) return
        // jeder dritte Schuss gezielt aus der Spalte über der Kanone
        val col = if (random.nextInt(3) == 0) {
            columns.minBy { c -> kotlin.math.abs(formationX + c * CELL_W + CELL_W / 2 - playerX) }
        } else columns[random.nextInt(columns.size)]
        val row = (ROWS - 1 downTo 0).first { r -> alive[r * COLS + col] }
        enemyBullets += Bullet(formationX + col * CELL_W + CELL_W / 2, invaderTop(row) + INV_H)
        val interval = max(0.35f, 1.1f - 0.1f * (wave - 1))
        fireTimer = interval * (0.5f + random.nextFloat())
    }

    private fun moveUfo(dt: Float) {
        val x = ufoX
        if (x == null) {
            ufoTimer -= dt
            if (ufoTimer <= 0f && aliveCount >= 8) {
                ufoDir = if (random.nextBoolean()) 1 else -1
                ufoX = if (ufoDir > 0) -UFO_W else W
            }
            return
        }
        val nx = x + 45f * ufoDir * dt
        if (nx < -UFO_W - 1f || nx > W + 1f) {
            ufoX = null
            ufoTimer = 20f + random.nextFloat() * 10f
        } else ufoX = nx
    }

    // ------------------------------------------------------------------ Schüsse

    private fun movePlayerBullet(dt: Float) {
        val b = playerBullet ?: return
        val dist = SHOT_SPEED * dt
        val n = max(1, ceil(dist / 2f).toInt())
        repeat(n) {
            b.y -= dist / n
            if (b.y < 16f) {
                playerBullet = null
                return
            }
            if (hitInvader(b) || hitUfo(b) || hitEnemyBullet(b) || hitBunker(b.x, b.y, 4f, fromBelow = true)) {
                playerBullet = null
                return
            }
        }
    }

    private fun hitInvader(b: Bullet): Boolean {
        for (i in alive.indices) {
            if (!alive[i]) continue
            val r = i / COLS
            val c = i % COLS
            val left = invaderLeft(r, c)
            val top = invaderTop(r)
            if (b.x >= left && b.x <= left + invaderWidth(r) && b.y <= top + INV_H && b.y + 4f >= top) {
                alive[i] = false
                addScore(rowPoints(r))
                effects += Effect(formationX + c * CELL_W + CELL_W / 2, top, 0.25f)
                events += Sfx.INVADER_HIT
                if (aliveCount == 0) {
                    waveTimer = WAVE_PAUSE
                    enemyBullets.clear()
                    ufoX = null
                }
                return true
            }
        }
        return false
    }

    private fun hitUfo(b: Bullet): Boolean {
        val x = ufoX ?: return false
        if (b.x < x || b.x > x + UFO_W || b.y > UFO_Y + UFO_H || b.y + 4f < UFO_Y) return false
        val points = UFO_POINTS[random.nextInt(UFO_POINTS.size)]
        addScore(points)
        effects += Effect(x + UFO_W / 2, UFO_Y, 1.2f, text = points.toString())
        events += Sfx.UFO_HIT
        ufoX = null
        ufoTimer = 20f + random.nextFloat() * 10f
        return true
    }

    private fun hitEnemyBullet(b: Bullet): Boolean {
        val hit = enemyBullets.firstOrNull { kotlin.math.abs(it.x - b.x) <= 1.5f && b.y <= it.y + 6f && b.y + 4f >= it.y }
            ?: return false
        enemyBullets.remove(hit)
        return true
    }

    private fun moveEnemyBullets(dt: Float) {
        val speed = min(130f, 75f + 7f * (wave - 1))
        val dist = speed * dt
        val n = max(1, ceil(dist / 2f).toInt())
        val it = enemyBullets.iterator()
        while (it.hasNext()) {
            val b = it.next()
            var gone = false
            for (k in 0 until n) {
                b.y += dist / n
                if (b.y + 6f >= GROUND_Y) {
                    gone = true
                    break
                }
                if (hitBunker(b.x, b.y, 6f, fromBelow = false)) {
                    gone = true
                    break
                }
                if (b.x >= playerX - PLAYER_W / 2 && b.x <= playerX + PLAYER_W / 2 && b.y + 6f >= PLAYER_Y + 1f && b.y <= PLAYER_Y + PLAYER_H) {
                    lives--
                    killPlayer()
                    return
                }
            }
            if (gone) it.remove()
        }
    }

    private fun killPlayer() {
        dyingTimer = DYING_TIME
        enemyBullets.clear()
        playerBullet = null
        events += Sfx.PLAYER_HIT
    }

    private fun addScore(points: Int) {
        score += points
        if (!extraLifeGiven && score >= EXTRA_LIFE_AT) {
            extraLifeGiven = true
            lives++
            events += Sfx.EXTRA_LIFE
        }
    }

    // ------------------------------------------------------------------ Bunker

    /** Trifft ein Schuss (senkrechter Strich bei x von y bis y+h) einen Bunker, wird dort ein Loch gesprengt. */
    private fun hitBunker(x: Float, y: Float, h: Float, fromBelow: Boolean): Boolean {
        if (y > BUNKER_Y + BUNKER_H || y + h < BUNKER_Y) return false
        for (b in BUNKER_X.indices) {
            val px = (x - BUNKER_X[b]).toInt()
            if (x < BUNKER_X[b] || px >= BUNKER_W) continue
            val from = (y - BUNKER_Y).toInt().coerceAtLeast(0)
            val to = (y + h - BUNKER_Y).toInt().coerceAtMost(BUNKER_H - 1)
            if (from > to) return false
            val rows = if (fromBelow) (to downTo from) else (from..to)
            for (py in rows) {
                if (bunkers[b][py * BUNKER_W + px]) {
                    erode(b, px, py)
                    return true
                }
            }
            return false
        }
        return false
    }

    private fun erode(b: Int, cx: Int, cy: Int) {
        val pixels = bunkers[b]
        for (dy in -3..3) for (dx in -2..2) {
            val x = cx + dx
            val y = cy + dy
            if (x !in 0 until BUNKER_W || y !in 0 until BUNKER_H) continue
            val d = dx * dx + dy * dy
            if (d <= 2 || (d <= 6 && random.nextFloat() < 0.6f)) pixels[y * BUNKER_W + x] = false
        }
        bunkerVersion++
    }

    private fun clearBunkerRect(left: Float, top: Float, w: Float, h: Float) {
        if (top > BUNKER_Y + BUNKER_H || top + h < BUNKER_Y) return
        var changed = false
        for (b in BUNKER_X.indices) {
            val x0 = (left - BUNKER_X[b]).toInt().coerceAtLeast(0)
            val x1 = (left + w - BUNKER_X[b]).toInt().coerceAtMost(BUNKER_W - 1)
            if (x0 > x1) continue
            val y0 = (top - BUNKER_Y).toInt().coerceAtLeast(0)
            val y1 = (top + h - BUNKER_Y).toInt().coerceAtMost(BUNKER_H - 1)
            for (y in y0..y1) for (x in x0..x1) {
                if (bunkers[b][y * BUNKER_W + x]) {
                    bunkers[b][y * BUNKER_W + x] = false
                    changed = true
                }
            }
        }
        if (changed) bunkerVersion++
    }

    // ------------------------------------------------------------------ für Tests

    internal fun testKillAllBut(keep: Set<Int>) {
        for (i in alive.indices) alive[i] = i in keep
    }

    internal fun testSetFormation(x: Float, y: Float) {
        formationX = x
        formationY = y
    }

    internal fun testAddEnemyBullet(x: Float, y: Float) {
        enemyBullets += Bullet(x, y)
    }
}
