package com.example.runtraining.workout.tss

import com.example.runtraining.workout.model.Intensity
import com.example.runtraining.workout.model.RepeatGroup
import com.example.runtraining.workout.model.RepeatPosition
import com.example.runtraining.workout.model.SourceDuration
import com.example.runtraining.workout.model.Step
import com.example.runtraining.workout.model.Target
import com.example.runtraining.workout.model.Workout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies [TssCalculator.computeElapsed]: the "actual performed" TSS obtained
 * by integrating planned intensity over the elapsed portion of the timeline.
 */
class TssCalculatorElapsedTest {

    private val threshold = 300 // sec/km

    private fun step(
        id: Long,
        durationSec: Int?,
        paceSecPerKm: Int?,
        inRepeat: RepeatPosition? = null,
    ) = Step(
        id = id,
        stepIndex = id.toInt(),
        name = null,
        intensity = Intensity.ACTIVE,
        sourceDuration = SourceDuration.Time(durationSec ?: 0),
        effectiveDurationSec = durationSec,
        target = if (paceSecPerKm != null) Target.Pace(paceSecPerKm, paceSecPerKm) else Target.Open,
        zoneLabel = null,
        inRepeat = inRepeat,
    )

    private fun workout(steps: List<Step>, groups: List<RepeatGroup> = emptyList()): Workout {
        val planned = steps.sumOf { it.effectiveDurationSec ?: 0 }
        return Workout(
            id = 1,
            contentHash = "h",
            originalFilename = "f.fit",
            displayName = "W",
            importedAtEpochMs = 0,
            plannedDurationSec = planned,
            plannedDistanceM = 0,
            tss = null,
            lastCompletedAtEpochMs = null,
            steps = steps,
            repeatGroups = groups,
        )
    }

    @Test
    fun uniformStep_scalesLinearlyWithElapsed() {
        // 3600 s at threshold pace → IF = 1 → 100 TSS for the full hour.
        val w = workout(listOf(step(1, 3600, threshold)))
        assertEquals(100.0, TssCalculator.computeElapsed(w, threshold, 3600)!!, 0.05)
        assertEquals(50.0, TssCalculator.computeElapsed(w, threshold, 1800)!!, 0.05)
        assertEquals(0.0, TssCalculator.computeElapsed(w, threshold, 0)!!, 0.001)
    }

    @Test
    fun thresholdUnset_returnsNull() {
        val w = workout(listOf(step(1, 3600, threshold)))
        assertNull(TssCalculator.computeElapsed(w, null, 3600))
    }

    @Test
    fun intervals_stoppingAfterHardPartCountsTheHardWork() {
        // 600 s hard (pace 250 → IF 1.2) then 600 s easy (pace 428 → IF ~0.70).
        val w = workout(listOf(step(1, 600, 250), step(2, 600, 428)))
        // Full elapsed reproduces the planned TSS.
        val planned = TssCalculator.compute(w, threshold)!!
        assertEquals(planned, TssCalculator.computeElapsed(w, threshold, 1200)!!, 0.05)
        // Stopping right after the hard interval: 600 * 1.44 / 36 = 24.0.
        assertEquals(24.0, TssCalculator.computeElapsed(w, threshold, 600)!!, 0.05)
        // Uniform time-scaling would have given planned * 0.5 — assert we did NOT do that.
        assertTrue(TssCalculator.computeElapsed(w, threshold, 600)!! > planned * 0.5)
    }

    @Test
    fun repeatBlock_expandsAndIntegrates() {
        // Group [A=30s pace250 IF1.2, B=30s pace600 IF0.5] x 3  → total 180 s.
        val a = step(1, 30, 250, RepeatPosition(repeatGroupId = 10, positionInRepeat = 0))
        val b = step(2, 30, 600, RepeatPosition(repeatGroupId = 10, positionInRepeat = 1))
        val w = workout(listOf(a, b), listOf(RepeatGroup(id = 10, orderInWorkout = 0, iterationCount = 3)))
        val planned = TssCalculator.compute(w, threshold)!!
        // Full elapsed equals the planned (repeat-expanded) TSS.
        assertEquals(planned, TssCalculator.computeElapsed(w, threshold, 180)!!, 0.05)
        // After exactly two of three iterations (120 s), we have 2/3 of the planned work.
        assertEquals(planned * 2.0 / 3.0, TssCalculator.computeElapsed(w, threshold, 120)!!, 0.1)
    }

    @Test
    fun elapsedBeyondPlanned_capsAtPlanned() {
        val w = workout(listOf(step(1, 600, 250), step(2, 600, 428)))
        val planned = TssCalculator.compute(w, threshold)!!
        assertEquals(planned, TssCalculator.computeElapsed(w, threshold, 99999)!!, 0.05)
    }
}
