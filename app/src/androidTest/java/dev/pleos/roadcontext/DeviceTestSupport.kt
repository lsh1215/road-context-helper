package dev.pleos.roadcontext

import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail

internal object DeviceTestSupport {
    val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    fun requireActualLocationPermission() {
        assertEquals("Actual runtime permission must already be granted by the user; this test does not grant it.",
            PackageManager.PERMISSION_GRANTED,
            instrumentation.targetContext.checkSelfPermission("pleos.car.permission.FUSED_LOCATION"))
    }
    fun <T> main(block: () -> T): T {
        val value = AtomicReference<T>()
        val failure = AtomicReference<Throwable>()
        instrumentation.runOnMainSync {
            try { value.set(block()) } catch (error: Throwable) { failure.set(error) }
        }
        failure.get()?.let { throw it }
        return value.get()
    }
    fun await(label: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (!condition()) {
            if (SystemClock.elapsedRealtime() >= deadline) fail("Timed out waiting for $label")
            SystemClock.sleep(50)
        }
    }
    fun save(name: String, data: JSONObject) {
        val root = outputDirectory("verification-device")
        File(root, "$name.json").writeText(data.toString(2))
    }

    /** A fresh explicit run ID keeps a failed run from inheriting old successful output files. */
    fun outputDirectory(category: String): File {
        require(category in setOf("verification-device", "human-study-captures"))
        val run = InstrumentationRegistry.getArguments().getString("evidenceRun")
        val base = if (run == null) instrumentation.targetContext.filesDir else {
            require(run.matches(Regex("[a-z0-9][a-z0-9_-]{0,63}"))) { "Invalid evidenceRun" }
            File(instrumentation.targetContext.filesDir, "verification-runs/$run")
        }
        return File(base, category).apply { mkdirs() }
    }
}
