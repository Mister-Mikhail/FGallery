package com.mistermikhail.fgallery.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class QuarterTurnGestureTest {
    @Test fun clockwiseAndCounterclockwiseSnapToQuarterTurns() {
        val gesture = QuarterTurnGesture()
        gesture.begin(1f, 0f)
        gesture.move(0f, 1f)
        assertEquals(90, gesture.end())
        gesture.begin(1f, 0f)
        gesture.move(0f, -1f)
        assertEquals(0, gesture.end())
    }

    @Test fun smallRotationDoesNotPersist() {
        val gesture = QuarterTurnGesture()
        gesture.begin(1f, 0f)
        gesture.move(1f, 0.3f)
        assertEquals(0, gesture.end())
    }

    @Test fun rotationAcrossAngleWrapDoesNotJump() {
        val gesture = QuarterTurnGesture()
        gesture.begin(-1f, 0.01f)
        gesture.move(-1f, -0.01f)
        assertEquals(0, gesture.end())
    }

    @Test fun sequentialTurnsAndReset() {
        val gesture = QuarterTurnGesture()
        repeat(4) {
            gesture.begin(1f, 0f)
            gesture.move(0f, 1f)
            assertEquals(((it + 1) % 4) * 90, gesture.end())
        }
        gesture.reset()
        assertEquals(0, gesture.end())
    }

    @Test fun cancelDoesNotChangeCommittedOrientation() {
        val gesture = QuarterTurnGesture(3)
        gesture.begin(1f, 0f)
        gesture.move(0f, 1f)
        gesture.cancel()
        assertEquals(270, gesture.end())
    }
    @Test fun snapThresholdAndNegativeDirection() {
        val gesture = QuarterTurnGesture()
        gesture.begin(1f, 0f)
        gesture.move(0.8f, 0.6f) // ~37 degrees: no turn
        assertEquals(0, gesture.end())
        gesture.begin(1f, 0f)
        gesture.move(0.6f, -0.8f) // ~-53 degrees: one negative turn
        assertEquals(270, gesture.end())
    }

    @Test fun accumulatedMotionCanCrossMultipleQuarterTurns() {
        val gesture = QuarterTurnGesture()
        gesture.begin(1f, 0f)
        gesture.move(0f, 1f)
        gesture.move(-1f, 0f)
        gesture.move(0f, -1f)
        assertEquals(270, gesture.end())
    }

    @Test fun gestureStateDoesNotCarryOverAfterEnd() {
        val gesture = QuarterTurnGesture()
        gesture.begin(1f, 0f)
        gesture.move(0f, 1f)
        assertEquals(90, gesture.end())
        gesture.begin(1f, 0f)
        gesture.move(1f, 0.1f)
        assertEquals(90, gesture.end())
    }

    @Test fun overlappingPointersDoNotCausePhantomQuarterTurn() {
        val gesture = QuarterTurnGesture()
        gesture.begin(1f, 0f)
        gesture.move(0f, 1f) // 90 degrees accumulated
        assertEquals(90f, gesture.move(0f, 0f), 0.001f)
        // The next valid vector is a fresh baseline, not a jump from atan2(0, 0).
        assertEquals(90f, gesture.move(-1f, 0f), 0.001f)
        assertEquals(90, gesture.end())
    }

    @Test fun invalidInitialVectorWaitsForValidPointerSeparation() {
        val gesture = QuarterTurnGesture()
        gesture.begin(0f, 0f)
        assertEquals(0f, gesture.move(0f, 1f), 0.001f)
        assertEquals(90f, gesture.move(-1f, 0f), 0.001f)
        assertEquals(90, gesture.end())
    }

    @Test fun previewKeepsAccumulatedNegativeRotationAfterPointersOverlap() {
        val gesture = QuarterTurnGesture()
        gesture.begin(1f, 0f)
        assertEquals(-90f, gesture.move(0f, -1f), 0.001f)
        assertEquals(-90f, gesture.move(0f, 0f), 0.001f)
        assertEquals(-90f, gesture.move(-1f, 0f), 0.001f)
        assertEquals(270, gesture.end())
    }

    @Test fun repeatedCounterclockwiseTurnsCycleThroughAllOrientations() {
        val gesture = QuarterTurnGesture()
        repeat(4) { step ->
            gesture.begin(1f, 0f)
            gesture.move(0f, -1f)
            assertEquals(Math.floorMod(-(step + 1), 4) * 90, gesture.end())
        }
    }

    @Test fun resetDiscardsUncommittedRotationAndPreviousFileOrientation() {
        val gesture = QuarterTurnGesture()
        gesture.begin(1f, 0f)
        gesture.move(0f, 1f)
        assertEquals(90, gesture.end())
        gesture.begin(1f, 0f)
        gesture.move(-1f, 0f)
        gesture.reset() // Page switch / viewer close must not preserve any angle.
        assertEquals(0, gesture.end())
        gesture.begin(1f, 0f)
        gesture.move(0f, -1f)
        assertEquals(270, gesture.end())
    }

    @Test fun pinchOnlyDistanceChangeDoesNotRotate() {
        val gesture = QuarterTurnGesture()
        gesture.begin(100f, 0f)
        gesture.move(200f, 0f)
        gesture.move(20f, 0f)
        assertEquals(0, gesture.end())
    }

    @Test fun invalidCoordinatesDoNotProducePhantomTurn() {
        val gesture = QuarterTurnGesture()
        gesture.begin(1f, 0f)
        gesture.move(Float.NaN, 1f)
        gesture.move(0f, 1f) // First valid vector after invalid input is a new baseline.
        assertEquals(0, gesture.end())
    }

    private fun moveAt(gesture: QuarterTurnGesture, degrees: Double, distance: Double = 1.0): Float {
        val radians = Math.toRadians(degrees)
        return gesture.move((distance * kotlin.math.cos(radians)).toFloat(),
            (distance * kotlin.math.sin(radians)).toFloat())
    }

    @Test fun pinchJitterIsInvisibleInBothDirections() {
        val gesture = QuarterTurnGesture()
        gesture.begin(1f, 0f)
        for (angle in listOf(-14.9, 12.0, -5.0, 14.9)) {
            assertEquals(0f, moveAt(gesture, angle, 12.0), 0f)
        }
        assertEquals(0, gesture.end())
    }

    @Test fun previewStartsContinuouslyAndSymmetrically() {
        for (sign in listOf(-1, 1)) {
            val gesture = QuarterTurnGesture()
            gesture.begin(1f, 0f)
            assertEquals(sign * 0.12f, moveAt(gesture, sign * 15.1), 0.002f)
            assertEquals(sign * 90f, moveAt(gesture, sign * 90.0), 0.001f)
            assertEquals(Math.floorMod(sign, 4) * 90, gesture.end())
        }
    }

    @Test fun adjustableThresholdIncludesImmediateTrackingAndFiftyDegrees() {
        val immediate = QuarterTurnGesture(thresholdDegrees = 0)
        immediate.begin(1f, 0f)
        assertEquals(-2f, moveAt(immediate, -2.0), 0.001f)
        val high = QuarterTurnGesture(thresholdDegrees = 50)
        high.begin(1f, 0f)
        assertEquals(0f, moveAt(high, 49.0), 0f)
        assertEquals(0, high.end())
        high.begin(1f, 0f)
        assertEquals(90f, moveAt(high, 90.0), 0.001f)
        assertEquals(90, high.end())
    }
    @Test fun reversalAfterActivationTracksEveryDegreeThroughTheStartingAngle() {
        for (sign in listOf(-1, 1)) {
            val gesture = QuarterTurnGesture()
            gesture.begin(1f, 0f)
            var previous = moveAt(gesture, sign * 35.0)
            for (angle in 34 downTo -30) {
                val next = moveAt(gesture, sign * angle.toDouble())
                assertEquals(-sign.toFloat(), next - previous, 0.001f)
                previous = next
            }
            gesture.end()
            gesture.begin(1f, 0f)
            assertEquals(0f, moveAt(gesture, sign * 10.0), 0f)
        }
    }
}
