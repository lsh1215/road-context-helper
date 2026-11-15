package dev.pleos.roadcontext.core

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.lang.management.ManagementFactory
import java.security.MessageDigest
import java.time.Instant
import kotlin.math.ceil

/**
 * Bounded warmed-JVM engine check, not an Android rendering benchmark or a 30-minute soak test.
 * Road configuration is read from the independently authored normal-approach fixture.
 */
class PerformanceReproducibilityTest {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val fixtureFile = File("../verification/scenarios/normal-approach.json")
    private val sampleCount = 1_000
    private val warmupPasses = 10
    private val thresholdNs = 50_000_000L

    private data class Scenario(
        val segment: RoadSegment,
        val config: GuidanceConfig,
        val epochAtZero: Long,
        val generation: Long,
        val inputs: List<ProjectedSample>,
    ) {
        fun engine() = GuidanceEngine(segment, config, InputSource.REPLAY_SYNTHETIC).also { it.changeRoute(generation) }
    }

    private fun scenario(): Scenario {
        val root = JsonParser.parseString(fixtureFile.readText()).asJsonObject
        val setup = root.getAsJsonObject("setup")
        val s = setup.getAsJsonObject("segment")
        val c = setup.getAsJsonObject("config")
        val segment = RoadSegment(s.string("id"), s.string("version"), s.double("approachStartMeters"),
            s.double("mergeStartMeters"), s.double("mergeEndMeters"), s.double("headingDeg"),
            s.double("corridorHalfWidthMeters"), true,
            RoadEvidence(s.get("approvedForSyntheticTesting").asBoolean, "fixture://normal-approach", 0,
                s.long("expiresAtEpochMs"), s.string("coordinateSystem"), "Fictional test only", "independent-qa"),
            s.get("enabled").asBoolean)
        val config = GuidanceConfig(c.double("previewDistanceMeters"), c.double("approachingDistanceMeters"),
            c.double("passedOffsetMeters"), c.long("staleAfterMs"), c.long("cooldownMs"),
            c.double("headingToleranceDeg"), c.double("jumpBaseMeters"), c.double("jumpMaxMetersPerSecond"))
        val generation = setup.long("initialRouteGeneration")
        val end = segment.mergeEndMeters + config.passedOffsetMeters + 50.0
        val inputs = (0 until sampleCount).map { i ->
            val elapsed = i * 50L
            ProjectedSample(segment.approachStartMeters + (end - segment.approachStartMeters) * i / (sampleCount - 1),
                0.0, segment.headingDeg, elapsed, InputSource.REPLAY_SYNTHETIC, generation,
                "performance-fixture-route", measurementElapsedMs = elapsed)
        }
        return Scenario(segment, config, setup.long("epochAtZeroMs"), generation, inputs)
    }

    @Test fun `AC29 warmed fixed thousand input engine p95 within budget`() {
        val scenario = scenario()
        repeat(warmupPasses) { runTrace(scenario) }
        val engine = scenario.engine()
        val durations = LongArray(sampleCount)
        val decisions = ArrayList<GuidanceDecision>(sampleCount)
        scenario.inputs.forEachIndexed { index, sample ->
            val start = System.nanoTime()
            val decision = engine.update(sample, sample.receivedElapsedMs, scenario.epochAtZero + sample.receivedElapsedMs)
            durations[index] = System.nanoTime() - start
            decisions += decision
        }
        assertPositiveFlow(decisions)
        val sorted = durations.sorted()
        fun percentile(q: Double) = sorted[ceil(sampleCount * q).toInt() - 1]
        val p95 = percentile(.95)
        writeReport("ac29-engine-performance.json", mapOf(
            "criterion" to "AC-29 engine portion only",
            "status" to if (p95 <= thresholdNs) "PASS" else "FAIL",
            "scope" to "Warmed JVM GuidanceEngine.update only; excludes SDK transport, geographic projection and Android UI latency",
            "sampleCount" to sampleCount,
            "warmupPasses" to warmupPasses,
            "warmupInputCount" to warmupPasses * sampleCount,
            "inputCadenceVirtualMs" to 50,
            "allocationPolicy" to "Inputs allocated before timing; returned Decision allocation is included",
            "thresholdP95Ns" to thresholdNs,
            "minNs" to sorted.first(), "p50Ns" to percentile(.50), "p95Ns" to p95,
            "p99Ns" to percentile(.99), "maxNs" to sorted.last(),
            "durationSamplesNs" to durations.toList(),
            "positiveFlow" to mapOf("previewCount" to decisions.last().previewCount,
                "statesObserved" to decisions.map { it.state.name }.distinct()),
            "evidence" to evidence(scenario),
            "environment" to environment(),
            "notProven" to listOf("AC-29 input-to-UI latency", "Real vehicle performance", "AC-28 actual 30-minute endurance"),
        ))
        assertTrue("Engine p95 ${p95}ns exceeds ${thresholdNs}ns", p95 <= thresholdNs)
    }

    @Test fun `AC30 identical fixture clock and config yield identical decisions three times`() {
        val scenario = scenario()
        val results = (1..3).map { runTrace(scenario) }
        results.forEach(::assertPositiveFlow)
        val equal = results.drop(1).all { it == results.first() }
        writeReport("ac30-engine-reproducibility.json", mapOf(
            "criterion" to "AC-30 deterministic engine replay",
            "status" to if (equal) "PASS" else "FAIL",
            "runs" to 3,
            "inputCountPerRun" to sampleCount,
            "comparison" to "Full ordered GuidanceDecision equality including state, reason, distance, source, passId, segment/version, previewCount",
            "decisionSequenceSha256" to results.map { sha256(gson.toJson(it)) },
            "decisionSequence" to results.first(),
            "evidence" to evidence(scenario),
            "environment" to environment(),
            "notProven" to listOf("SDK nondeterminism", "Android UI replay", "Human comprehension", "AC-28 actual 30-minute endurance"),
        ))
        assertEquals(results[0], results[1])
        assertEquals(results[0], results[2])
    }

    private fun runTrace(scenario: Scenario): List<GuidanceDecision> {
        val engine = scenario.engine()
        return scenario.inputs.map { engine.update(it, it.receivedElapsedMs, scenario.epochAtZero + it.receivedElapsedMs) }
    }

    private fun assertPositiveFlow(decisions: List<GuidanceDecision>) {
        assertEquals(sampleCount, decisions.size)
        assertEquals(GuidanceState.WAITING, decisions.first().state)
        assertTrue("An always-suppress engine cannot pass this performance test", decisions.any { it.state == GuidanceState.PREVIEW })
        assertTrue(decisions.any { it.state == GuidanceState.APPROACHING })
        assertEquals(GuidanceState.PASSED, decisions.last().state)
        assertEquals("Exactly one preview in the complete supported pass", 1, decisions.last().previewCount)
        assertTrue("No hidden rejection in supported trace", decisions.none { it.state == GuidanceState.SUPPRESSED || it.state == GuidanceState.UNAVAILABLE })
    }

    private fun evidence(scenario: Scenario) = mapOf(
        "fixturePath" to fixtureFile.path,
        "fixtureSha256" to sha256(fixtureFile.readText()),
        "derivedInputDescription" to "1000 fixed equally spaced points from fixture approach start to merge end + passed offset + 50m; virtual monotonic clock 50ms per point",
        "inputsSha256" to sha256(gson.toJson(scenario.inputs)),
        "configSha256" to sha256(gson.toJson(scenario.config)),
        "source" to InputSource.REPLAY_SYNTHETIC.name,
    )

    private fun environment() = mapOf(
        "recordedAtUtc" to Instant.now().toString(),
        "javaVersion" to System.getProperty("java.version"),
        "javaVmName" to System.getProperty("java.vm.name"),
        "osName" to System.getProperty("os.name"),
        "osArch" to System.getProperty("os.arch"),
        "availableProcessors" to Runtime.getRuntime().availableProcessors(),
        "maxHeapBytes" to Runtime.getRuntime().maxMemory(),
        "jvmOptions" to ManagementFactory.getRuntimeMXBean().inputArguments.filter {
            it.startsWith("-Xmx") || it.startsWith("-Xms") || it.startsWith("-XX:+Use") || it.startsWith("-XX:Max")
        },
        "clock" to "System.nanoTime; wall/monotonic scenario clocks are supplied fixed test inputs",
    )

    private fun writeReport(name: String, contents: Map<String, Any?>) {
        File("build/reports/verification").also { it.mkdirs() }.resolve(name).writeText(gson.toJson(contents))
    }
    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private fun JsonObject.string(key: String) = get(key).asString
    private fun JsonObject.double(key: String) = get(key).asDouble
    private fun JsonObject.long(key: String) = get(key).asLong
}
