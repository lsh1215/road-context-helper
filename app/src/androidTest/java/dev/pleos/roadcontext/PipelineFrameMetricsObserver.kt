package dev.pleos.roadcontext

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Test-only window diagnostics, never used to decide that a particular UI state was drawn. */
internal class PipelineFrameMetricsObserver(private val window: Window) {
    private data class ClockPair(val elapsedBeforeNs: Long, val nanoTimeNs: Long, val elapsedAfterNs: Long) {
        fun json(): JSONObject = JSONObject()
            .put("elapsedBeforeNs", elapsedBeforeNs).put("nanoTimeNs", nanoTimeNs)
            .put("elapsedAfterNs", elapsedAfterNs)
            .put("offsetLowerNs", elapsedBeforeNs - nanoTimeNs)
            .put("offsetUpperNs", elapsedAfterNs - nanoTimeNs)
            .put("pairReadUncertaintyNs", elapsedAfterNs - elapsedBeforeNs)
    }

    private data class Record(
        val metrics: FrameMetrics,
        val clock: ClockPair,
        val droppedReports: Int,
        val copyCompletedElapsedNs: Long,
    )

    private val thread = HandlerThread("pipeline-frame-observer")
    private lateinit var handler: Handler
    private val records = ArrayList<Record>(4096)
    private lateinit var startClock: ClockPair
    private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
        val clock = pairClocks()
        // Framework reuses its FrameMetrics object. Copy immediately; JSON and analysis happen
        // only after detachment, outside every measured input and outside this callback.
        val copy = FrameMetrics(metrics)
        synchronized(records) {
            records += Record(copy, clock, dropped, SystemClock.elapsedRealtimeNanos())
        }
    }

    /** Called on the activity main thread, before warm-up. */
    fun start() {
        thread.start()
        handler = Handler(thread.looper)
        startClock = pairClocks()
        try {
            window.addOnFrameMetricsAvailableListener(listener, handler)
        } catch (failure: Exception) {
            thread.quitSafely()
            throw failure
        }
    }

    /** Called on the instrumentation thread, after the last measurement (also on assertion failure). */
    fun stopAndReport(): JSONObject {
        val stopClock = pairClocks()
        val errors = mutableListOf<String>()
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                window.removeOnFrameMetricsAvailableListener(listener)
            }
        } catch (failure: Exception) {
            errors += "detach: ${failure.javaClass.simpleName}: ${failure.message}"
        }
        var queueDrained = false
        var interrupted = false
        try {
            val drained = CountDownLatch(1)
            val barrierPosted = handler.post { drained.countDown() }
            queueDrained = barrierPosted && drained.await(5, TimeUnit.SECONDS)
            if (!queueDrained) errors += "observer handler queue did not drain within 5 seconds"
        } catch (failure: Exception) {
            interrupted = failure is InterruptedException
            errors += "drain: ${failure.javaClass.simpleName}: ${failure.message}"
        } finally {
            thread.quitSafely()
            try {
                thread.join(1_000)
            } catch (failure: InterruptedException) {
                interrupted = true
                errors += "observer shutdown join interrupted"
            }
        }
        if (thread.isAlive) errors += "observer thread still alive after shutdown timeout"
        val snapshot = synchronized(records) { records.toList() }
        // Preserve all observations even if diagnostic draining failed, and preserve the caller's
        // interrupt contract after cleanup. The parent test also saves its semantic samples.
        if (interrupted) Thread.currentThread().interrupt()
        val frames = JSONArray()
        snapshot.forEachIndexed { index, record ->
            val metrics = record.metrics
            frames.put(JSONObject().put("index", index).put("callbackClockPair", record.clock.json())
                .put("copyCompletedElapsedNs", record.copyCompletedElapsedNs)
                .put("callbackCopyObservedNs", record.copyCompletedElapsedNs - record.clock.elapsedBeforeNs)
                .put("droppedReportsSinceLastCallback", record.droppedReports)
                .put("intendedVsyncTimestampNs", metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP))
                .put("vsyncTimestampNs", metrics.getMetric(FrameMetrics.VSYNC_TIMESTAMP))
                .put("unknownDelayNs", metrics.getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION))
                .put("inputHandlingNs", metrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION))
                .put("animationNs", metrics.getMetric(FrameMetrics.ANIMATION_DURATION))
                .put("layoutMeasureNs", metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION))
                .put("drawNs", metrics.getMetric(FrameMetrics.DRAW_DURATION))
                .put("syncNs", metrics.getMetric(FrameMetrics.SYNC_DURATION))
                .put("commandIssueNs", metrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION))
                .put("swapBuffersNs", metrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION))
                .put("totalNs", metrics.getMetric(FrameMetrics.TOTAL_DURATION))
                .put("firstDrawFrame", metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME))
                .put("deadlineNs", if (Build.VERSION.SDK_INT >= 31) metrics.getMetric(FrameMetrics.DEADLINE) else -1L)
                .put("gpuNs", if (Build.VERSION.SDK_INT >= 31) metrics.getMetric(FrameMetrics.GPU_DURATION) else -1L))
        }
        return JSONObject().put("frameCount", snapshot.size).put("frames", frames)
            .put("startClockPair", startClock.json()).put("stopRequestedClockPair", stopClock.json())
            .put("queueDrained", queueDrained).put("collectionErrors", JSONArray(errors))
            .put("droppedReportCount", snapshot.sumOf { it.droppedReports.toLong() })
            .put("hasFrameObservations", snapshot.isNotEmpty())
            .put("clockPolicy", "Raw frame timestamps are retained. Each callback and both run boundaries bracket System.nanoTime with elapsedRealtimeNanos, yielding an offset interval and pair-read uncertainty. Do not assume zero offset or constant offset over suspend; inspect local pairs and drift before correlation. Callback arrival time is not frame completion time.")
            .put("clockEvidence", "FrameMetrics documents VSYNC_TIMESTAMP as the time used by Choreographer callbacks and INTENDED_VSYNC_TIMESTAMP as the intended start on the same vsync timeline. AOSP Choreographer FrameCallback documents frameTimeNanos in the System.nanoTime timebase. This supports monotonic-time correlation, but runtime clock-pair uncertainty/drift must still be checked before mapping to elapsedRealtimeNanos.")
            .put("limits", "All window frames during warm-up, resets and measured inputs are retained without expected-state filtering. Temporal overlap cannot identify which model state a frame contains or prove physical presentation. Dropped reports mean missing diagnostics, not necessarily dropped display frames. A drained handler only covers callbacks already queued: removal can exclude in-flight/future frame reports.")
            .put("overhead", "A dedicated HandlerThread copies each FrameMetrics and a clock pair into memory under a short lock. Allocations and scheduling can perturb the run; callbackCopyObservedNs captures only the recorded callback prefix, not total observer overhead. JSON serialization and queue drain occur after measurements. Diagnostic values are never subtracted from the 250ms metric.")
            .put("references", JSONArray(listOf(
                "https://developer.android.com/reference/android/view/FrameMetrics",
                "https://developer.android.com/reference/android/view/Window.OnFrameMetricsAvailableListener",
                "https://android.googlesource.com/platform/frameworks/base/+/HEAD/core/java/android/view/Choreographer.java")))
    }

    private fun pairClocks(): ClockPair {
        val before = SystemClock.elapsedRealtimeNanos()
        val nano = System.nanoTime()
        return ClockPair(before, nano, SystemClock.elapsedRealtimeNanos())
    }
}
