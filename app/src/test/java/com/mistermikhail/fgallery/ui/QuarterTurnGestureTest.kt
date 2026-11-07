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

}
