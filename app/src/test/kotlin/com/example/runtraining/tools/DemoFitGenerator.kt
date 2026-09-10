package com.example.runtraining.tools

import com.example.runtraining.workout.fit.FitDecodeResult
import com.example.runtraining.workout.fit.FitDecoder
import com.garmin.fit.DateTime
import com.garmin.fit.File as FitFileType
import com.garmin.fit.FileEncoder
import com.garmin.fit.FileIdMesg
import com.garmin.fit.Fit
import com.garmin.fit.Intensity
import com.garmin.fit.Manufacturer
import com.garmin.fit.Sport
import com.garmin.fit.SubSport
import com.garmin.fit.WktStepDuration
import com.garmin.fit.WktStepTarget
import com.garmin.fit.WorkoutMesg
import com.garmin.fit.WorkoutStepMesg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Date

/**
 * Opt-in generator for the committed demo `.fit` workouts under
 * `demo-workouts/`. Skipped in normal test runs; enable with env
 * `GENERATE_DEMO_FIT=1`. Encodes with the same Garmin FIT SDK the app decodes
 * with, then re-decodes each file through [FitDecoder] to self-validate.
 * Output is deterministic (fixed timestamp) so re-runs don't churn git.
 */
class DemoFitGenerator {

    // Fixed FIT file_id time_created (2026-01-01T00:00:00Z) → deterministic bytes.
    private val fixedCreated = DateTime(Date(1_767_225_600_000L))

    @Test
    fun generateDemoFitFiles() {
        assumeTrue("Set env GENERATE_DEMO_FIT=1 to regenerate demo .fit files", System.getenv("GENERATE_DEMO_FIT") == "1")

        val outDir = File(repoRoot(), "demo-workouts").apply { mkdirs() }

        writeAndVerify(File(outDir, "easy-aerobic-40min.fit"), "Easy Aerobic 40'", numSteps = 3, expectedSteps = 3, expectedRepeats = 0) { enc ->
            timeStep(enc, 0, "Warm up", Intensity.WARMUP, sec = 600, paceSlow = 410, paceFast = 355)
            timeStep(enc, 1, "Easy run", Intensity.ACTIVE, sec = 1500, paceSlow = 400, paceFast = 360)
            timeStep(enc, 2, "Cool down", Intensity.COOLDOWN, sec = 300)
        }

        writeAndVerify(File(outDir, "intervals-6x800m.fit"), "6 x 800m Intervals", numSteps = 5, expectedSteps = 4, expectedRepeats = 1) { enc ->
            timeStep(enc, 0, "Warm up", Intensity.WARMUP, sec = 720, paceSlow = 410, paceFast = 355)
            distanceStep(enc, 1, "800m hard", Intensity.ACTIVE, meters = 800, paceSlow = 312, paceFast = 300)
            timeStep(enc, 2, "Jog recovery", Intensity.REST, sec = 150, paceSlow = 480, paceFast = 420)
            repeatStep(enc, 3, backToStepIndex = 1, times = 6)
            timeStep(enc, 4, "Cool down", Intensity.COOLDOWN, sec = 480)
        }

        writeAndVerify(File(outDir, "tempo-20min.fit"), "Tempo 20'", numSteps = 3, expectedSteps = 3, expectedRepeats = 0) { enc ->
            timeStep(enc, 0, "Warm up", Intensity.WARMUP, sec = 600, paceSlow = 410, paceFast = 355)
            timeStep(enc, 1, "Tempo", Intensity.ACTIVE, sec = 1200, paceSlow = 335, paceFast = 315)
            timeStep(enc, 2, "Cool down", Intensity.COOLDOWN, sec = 600, paceSlow = 480, paceFast = 410)
        }
    }

    private fun writeAndVerify(
        file: File,
        name: String,
        numSteps: Int,
        expectedSteps: Int,
        expectedRepeats: Int,
        steps: (FileEncoder) -> Unit,
    ) {
        val enc = FileEncoder(file, Fit.ProtocolVersion.V2_0)
        enc.write(
            FileIdMesg().apply {
                setType(FitFileType.WORKOUT)
                setManufacturer(Manufacturer.DEVELOPMENT)
                setProduct(0)
                setSerialNumber(0xF17F17L)
                setTimeCreated(fixedCreated)
            },
        )
        enc.write(
            WorkoutMesg().apply {
                setWktName(name)
                setSport(Sport.RUNNING)
                setSubSport(SubSport.GENERIC)
                setNumValidSteps(numSteps)
            },
        )
        steps(enc)
        enc.close()

        val result = FitDecoder.decode(file.readBytes())
        assertTrue("$name should decode Ok, was $result", result is FitDecodeResult.Ok)
        val ok = result as FitDecodeResult.Ok
        assertEquals("$name emitted step count", expectedSteps, ok.workout.steps.size)
        assertEquals("$name repeat count", expectedRepeats, ok.workout.repeats.size)
        println("wrote ${file.name}: ${ok.workout.steps.size} steps, ${ok.workout.repeats.size} repeat(s)")
    }

    private fun timeStep(
        enc: FileEncoder, index: Int, name: String, intensity: Intensity,
        sec: Int, paceSlow: Int? = null, paceFast: Int? = null,
    ) {
        val m = WorkoutStepMesg().apply {
            setMessageIndex(index)
            setWktStepName(name)
            setIntensity(intensity)
            setDurationType(WktStepDuration.TIME)
            setDurationTime(sec.toFloat())
        }
        applyTarget(m, paceSlow, paceFast)
        enc.write(m)
    }

    private fun distanceStep(
        enc: FileEncoder, index: Int, name: String, intensity: Intensity,
        meters: Int, paceSlow: Int? = null, paceFast: Int? = null,
    ) {
        val m = WorkoutStepMesg().apply {
            setMessageIndex(index)
            setWktStepName(name)
            setIntensity(intensity)
            setDurationType(WktStepDuration.DISTANCE)
            setDurationDistance(meters.toFloat())
        }
        applyTarget(m, paceSlow, paceFast)
        enc.write(m)
    }

    private fun repeatStep(enc: FileEncoder, index: Int, backToStepIndex: Int, times: Int) {
        enc.write(
            WorkoutStepMesg().apply {
                setMessageIndex(index)
                setDurationType(WktStepDuration.REPEAT_UNTIL_STEPS_CMPLT)
                setDurationStep(backToStepIndex.toLong())
                setTargetValue(times.toLong())
            },
        )
    }

    /** Pace targets are stored as a speed range (m/s), matching FitDecoder. */
    private fun applyTarget(m: WorkoutStepMesg, paceSlowSecPerKm: Int?, paceFastSecPerKm: Int?) {
        if (paceSlowSecPerKm != null && paceFastSecPerKm != null) {
            m.setTargetType(WktStepTarget.SPEED)
            m.setCustomTargetSpeedLow(1000f / paceSlowSecPerKm)   // slower bound → lower m/s
            m.setCustomTargetSpeedHigh(1000f / paceFastSecPerKm)  // faster bound → higher m/s
        } else {
            m.setTargetType(WktStepTarget.OPEN)
        }
    }

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null && !File(dir, "settings.gradle.kts").exists()) dir = dir.parentFile
        return dir ?: File(System.getProperty("user.dir"))
    }
}
