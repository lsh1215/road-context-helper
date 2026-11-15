package dev.pleos.roadcontext.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Runs independently written expected states. Never generates an oracle from engine output. */
class AcceptanceFixtureTest {
    @Test fun `independent fixture states and announcement counts match`() {
        val directory = File("../verification/scenarios")
        val index = JsonParser.parseString(File(directory, "index.json").readText()).asJsonObject
        val failures = mutableListOf<String>()
        index.getAsJsonArray("files").forEach { filename ->
            val f = JsonParser.parseString(File(directory, filename.asString).readText()).asJsonObject
            val setup = f.getAsJsonObject("setup")
            val s = setup.getAsJsonObject("segment")
            val epoch = setup.long("epochAtZeroMs")
            var segment = RoadSegment(
                s.string("id"), s.string("version"), s.double("approachStartMeters"), s.double("mergeStartMeters"),
                s.double("mergeEndMeters"), s.double("headingDeg"), s.double("corridorHalfWidthMeters"), true,
                RoadEvidence(s.bool("approvedForSyntheticTesting"), "fixture://${f.string("id")}", 0,
                    s.long("expiresAtEpochMs"), s.string("coordinateSystem"), "Project-owned synthetic fixture", "independent-qa"),
                s.bool("enabled"),
            )
            val c = setup.getAsJsonObject("config")
            val config = GuidanceConfig(c.double("previewDistanceMeters"), c.double("approachingDistanceMeters"),
                c.double("passedOffsetMeters"), c.long("staleAfterMs"), c.long("cooldownMs"), c.double("headingToleranceDeg"),
                c.double("jumpBaseMeters"), c.double("jumpMaxMetersPerSecond"))
            val engine = GuidanceEngine(segment, config, InputSource.valueOf(setup.string("initialSource")))
            engine.changeRoute(setup.long("initialRouteGeneration"))
            var wallOffset = epoch
            f.getAsJsonArray("events").forEachIndexed { ordinal, element ->
                val event = element.asJsonObject
                val time = event.long("atMs")
                val result = when (event.string("kind")) {
                    "sample" -> {
                        val v = event.getAsJsonObject("sample")
                        engine.update(ProjectedSample(v.double("alongMeters"), v.double("crossMeters"),
                            v.get("headingDeg")?.takeUnless { it.isJsonNull }?.asDouble,
                            v.long("receivedElapsedMs"), InputSource.valueOf(v.string("source")), v.long("routeGeneration"),
                            v.get("routeId")?.takeUnless { it.isJsonNull }?.asString, v.bool("ambiguous"),
                            v.get("measurementElapsedMs")?.takeUnless { it.isJsonNull }?.asLong), time, wallOffset + time)
                    }
                    "tick" -> engine.tick(time, wallOffset + time)
                    "routeChange" -> engine.changeRoute(event.long("newRouteGeneration"))
                    "sourceSwitch" -> engine.switchSource(InputSource.valueOf(event.string("source")), event.long("newRouteGeneration"))
                    "clock" -> { wallOffset = event.long("wallEpochMs") - time; engine.tick(time, wallOffset + time) }
                    "capability" -> engine.setAvailable(event.bool("available"))
                    "dataUpdate" -> {
                        val patch = event.getAsJsonObject("segmentPatch")
                        segment = segment.copy(enabled = patch.get("enabled")?.asBoolean ?: segment.enabled,
                            version = patch.get("version")?.asString ?: segment.version)
                        engine.replaceSegment(segment)
                    }
                    "tbt" -> {
                        assertEquals("Only unverified annotations are represented by this fixture", "UNVERIFIED", event.string("distanceSemantics"))
                        engine.observeUnverifiedTbt()
                    }
                    "load" -> {
                        assertTrue("invalid-topology fixture must actually be invalid", segment.validationErrors().contains("invalid_topology"))
                        engine.tick(time, wallOffset + time)
                    }
                    else -> error("Unsupported fixture event ${event.string("kind")}")
                }
                val expected = event.getAsJsonObject("expected")
                if (result.state.name != expected.string("state") || result.previewCount != expected.get("previewCount").asInt) {
                    failures += "${f.string("id")} event $ordinal at $time expected=$expected actual=$result"
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun JsonObject.string(key: String) = get(key).asString
    private fun JsonObject.double(key: String) = get(key).asDouble
    private fun JsonObject.long(key: String) = get(key).asLong
    private fun JsonObject.bool(key: String) = get(key).asBoolean
}
