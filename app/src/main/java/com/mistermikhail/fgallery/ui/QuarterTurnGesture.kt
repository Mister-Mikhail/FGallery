package com.mistermikhail.fgallery.ui

import kotlin.math.PI
import kotlin.math.atan2

/**
 * Tracks a two-pointer rotation gesture without modifying the underlying media.
 * The preview may follow the fingers, but the committed orientation is always
 * one of 0, 90, 180 or 270 degrees.
 */
internal class QuarterTurnGesture(initialQuarterTurns: Int = 0, thresholdDegrees: Int = DEFAULT_THRESHOLD) {
    companion object {
        const val DEFAULT_THRESHOLD = 15
        const val MAX_THRESHOLD = 50
    }

    private val threshold = thresholdDegrees.coerceIn(0, MAX_THRESHOLD)
    var quarterTurns: Int = Math.floorMod(initialQuarterTurns, 4)
        private set

    private var lastAngle: Double? = null
    private var accumulatedDegrees = 0.0

    fun begin(dx: Float, dy: Float) {
        lastAngle = if (validVector(dx, dy)) angle(dx, dy) else null
        accumulatedDegrees = 0.0
    }

    /** Returns transient rotation in degrees relative to the last committed quarter-turn. */
    fun move(dx: Float, dy: Float): Float {
        // Overlapping pointers have no defined angle: retain the current preview
        // rather than treating atan2(0, 0) as a real rotation.
        if (!validVector(dx, dy)) {
            lastAngle = null
            return previewDegrees().toFloat()
        }
        val current = angle(dx, dy)
        val previous = lastAngle ?: run {
            lastAngle = current
            return previewDegrees().toFloat()
        }
        var delta = current - previous
        if (delta > 180.0) delta -= 360.0
        if (delta < -180.0) delta += 360.0
        accumulatedDegrees += delta
        lastAngle = current
        return previewDegrees().toFloat()
    }

    // Start at zero at the edge of the dead zone and catch up continuously by 90°.
    private fun previewDegrees(): Double {
        val magnitude = kotlin.math.abs(accumulatedDegrees)
        if (magnitude <= threshold) return 0.0
        if (magnitude >= 90.0 || threshold == 0) return accumulatedDegrees
        return Math.copySign((magnitude - threshold) * 90.0 / (90.0 - threshold), accumulatedDegrees)
    }

    /** Snap to the nearest quarter turn, in either direction. */
    fun end(): Int {
        val preview = previewDegrees()
        val steps = if (preview >= 0) {
            kotlin.math.floor((preview + 45.0) / 90.0).toInt()
        } else {
            kotlin.math.ceil((preview - 45.0) / 90.0).toInt()
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

    private fun validVector(dx: Float, dy: Float): Boolean =
        dx.isFinite() && dy.isFinite() && dx * dx + dy * dy > 1e-4f

    private fun angle(dx: Float, dy: Float): Double =
        atan2(dy.toDouble(), dx.toDouble()) * 180.0 / PI
}
