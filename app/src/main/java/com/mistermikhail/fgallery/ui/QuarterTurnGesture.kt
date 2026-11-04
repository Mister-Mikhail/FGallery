package com.mistermikhail.fgallery.ui

import kotlin.math.PI
import kotlin.math.atan2

/**
 * Tracks a two-pointer rotation gesture without modifying the underlying media.
 * The preview may follow the fingers, but the committed orientation is always
 * one of 0, 90, 180 or 270 degrees.
 */
internal class QuarterTurnGesture(initialQuarterTurns: Int = 0) {
    var quarterTurns: Int = Math.floorMod(initialQuarterTurns, 4)
        private set

    private var lastAngle: Double? = null
    private var accumulatedDegrees = 0.0

    fun begin(dx: Float, dy: Float) {
        lastAngle = angle(dx, dy)
        accumulatedDegrees = 0.0
    }

    /** Returns transient rotation in degrees relative to the last committed quarter-turn. */
    fun move(dx: Float, dy: Float): Float {
        val current = angle(dx, dy)
        val previous = lastAngle ?: run {
            lastAngle = current
            return 0f
        }
        var delta = current - previous
        if (delta > 180.0) delta -= 360.0
        if (delta < -180.0) delta += 360.0
        accumulatedDegrees += delta
        lastAngle = current
        return accumulatedDegrees.toFloat()
    }

    /** Snap to the nearest quarter turn, in either direction. */
    fun end(): Int {
        val steps = if (accumulatedDegrees >= 0) {
            kotlin.math.floor((accumulatedDegrees + 45.0) / 90.0).toInt()
        } else {
            kotlin.math.ceil((accumulatedDegrees - 45.0) / 90.0).toInt()
        }
        quarterTurns = Math.floorMod(quarterTurns + steps, 4)
        cancel()
        return quarterTurns * 90
    }

    fun cancel() {
        lastAngle = null
        accumulatedDegrees = 0.0
    }

    fun reset() {
        quarterTurns = 0
        cancel()
    }

    private fun angle(dx: Float, dy: Float): Double =
        atan2(dy.toDouble(), dx.toDouble()) * 180.0 / PI
}
