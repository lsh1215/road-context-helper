package dev.pleos.roadcontext.data

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

/** These are fictional test coordinates and metadata, not an approved real-world road. */
class CatalogLoaderTest {
    private val now = 1_000_000L
    private val loader = CatalogLoader()
    private fun validEntry() = """{
      "id":"fixture-only", "version":"test", "status":"APPROVED", "synthetic":false,
      "enabled":true, "approachStartMeters":0, "mergeStartMeters":600, "mergeEndMeters":800,
      "headingDeg":90, "corridorHalfWidthMeters":15, "mergeSide":"LEFT",
      "geometry":{"crs":"EPSG:4326","centerline":[[127,37],[127.02,37]]},
      "evidence":{
        "sourceUrl":"https://example.org/fictional-test", "sourceVersion":"test-only",
        "sourceSha256":"${"a".repeat(64)}", "attribution":"Fictional unit test",
        "reviewer":"test-only", "checkedAtEpochMs":0, "expiresAtEpochMs":2000000,
        "originalCrs":"EPSG:4326", "license":"TEST-NOT-FOR-REAL-USE",
        "checks":{"direction":true,"lanePersistence":true,"mergeEndpoint":true,
          "connectivity":true,"crsTransform":true,"currentness":true,"license":true}
      }
    }"""
    private fun catalog(entries: String = validEntry()): String {
        val root = JsonParser.parseString("""{
          "schemaVersion":2,"catalogVersion":"test-only","sourceMode":"REAL_WORLD","approvedSegments":[$entries]
        }""").asJsonObject
        if (entries.isNotBlank()) {
            root.add("matchingCoverage", JsonParser.parseString("""{
              "crs":"EPSG:4326", "bounds":{"south":36.9,"west":126.9,"north":37.1,"east":127.1},
              "evidence":{"sourceUrl":"https://example.org/test-coverage", "sourceVersion":"test-only",
                "sourceSha256":"${"b".repeat(64)}", "attribution":"fictional", "reviewer":"test-only",
                "checkedAtEpochMs":0,"expiresAtEpochMs":2000000,"license":"TEST",
                "completenessChecks":{"fullBounds":true,"parallelRoads":true,"gradeSeparatedRoads":true,"ramps":true,"intersections":true}}
            }"""))
            val candidates = com.google.gson.JsonArray()
            root.getAsJsonArray("approvedSegments").forEach { raw ->
                val entry = raw.asJsonObject
                val candidate = entry.getAsJsonObject("geometry").deepCopy()
                candidate.add("id", entry.get("id"))
                candidates.add(candidate)
            }
            root.add("candidateRoads", candidates)
        }
        return root.toString()
    }
    private fun mutateRoot(block: (com.google.gson.JsonObject) -> Unit): String {
        val root = JsonParser.parseString(catalog()).asJsonObject
        block(root)
        return root.toString()
    }
    private fun mutated(block: (com.google.gson.JsonObject) -> Unit): String {
        val e = JsonParser.parseString(validEntry()).asJsonObject
        block(e)
        return catalog(e.toString())
    }
    private fun assertRejected(json: String) = assertTrue(loader.load(json, now).toString(), loader.load(json, now) is CatalogLoadResult.Rejected)

    @Test fun emptyCatalogIsValidButUnsupported() {
        val value = loader.load(catalog(""), now) as CatalogLoadResult.Loaded
        assertFalse(value.catalog.supported)
        assertTrue(value.catalog.entries.isEmpty())
    }
    @Test fun validMetadataCreatesTypedDirectedEntry() {
        val value = (loader.load(catalog(), now) as CatalogLoadResult.Loaded).catalog
        assertTrue(value.supported)
        assertEquals(MergeSide.LEFT, value.entries.single().mergeSide)
        assertEquals("fixture-only", value.entries.single().centerline.id)
        assertFalse(value.entries.single().segment.synthetic)
    }
    @Test fun duplicateSegmentIdsRejectWholeCatalog() = assertRejected(catalog(validEntry()+","+validEntry()))
    @Test fun unapprovedEntryRejects() = assertRejected(mutated { it.addProperty("status", "PENDING") })
    @Test fun disabledEntryRejects() = assertRejected(mutated { it.addProperty("enabled", false) })
    @Test fun syntheticEntryRejects() = assertRejected(mutated { it.addProperty("synthetic", true) })
    @Test fun expiredEntryRejects() = assertRejected(mutated { it.getAsJsonObject("evidence").addProperty("expiresAtEpochMs", now) })
    @Test fun futureReviewRejects() = assertRejected(mutated { it.getAsJsonObject("evidence").addProperty("checkedAtEpochMs", now+1) })
    @Test fun missingProvenanceRejects() = assertRejected(mutated { it.getAsJsonObject("evidence").remove("reviewer") })
    @Test fun missingReviewCheckRejects() = assertRejected(mutated { it.getAsJsonObject("evidence").getAsJsonObject("checks").addProperty("mergeEndpoint", false) })
    @Test fun sourceUrlMustBeHttps() = assertRejected(mutated { it.getAsJsonObject("evidence").addProperty("sourceUrl", "fixture://fake") })
    @Test fun outputCrsMustBeWgs84() = assertRejected(mutated { it.getAsJsonObject("geometry").addProperty("crs", "EPSG:5179") })
    @Test fun wrongGeometryDirectionRejects() = assertRejected(mutated { it.addProperty("headingDeg", 270) })
    @Test fun missingPostMergeGeometryRejects() = assertRejected(mutated { it.addProperty("mergeEndMeters", 1900) })
    @Test fun invalidTopologyRejects() = assertRejected(mutated { it.addProperty("mergeEndMeters", 500) })
    @Test fun malformedJsonRejects() { listOf("null", "[]", "{", catalog()+" junk").forEach(::assertRejected) }
    @Test fun missingCoverageRejectsSupportedCatalog() = assertRejected(mutateRoot { it.remove("matchingCoverage") })
    @Test fun missingCompetitorReviewRejectsCatalog() = assertRejected(mutateRoot {
        it.getAsJsonObject("matchingCoverage").getAsJsonObject("evidence").getAsJsonObject("completenessChecks").addProperty("gradeSeparatedRoads", false)
    })
    @Test fun expiredCoverageRejectsCatalog() = assertRejected(mutateRoot {
        it.getAsJsonObject("matchingCoverage").getAsJsonObject("evidence").addProperty("expiresAtEpochMs", now)
    })
    @Test fun missingCandidateRoadsRejectsCatalog() = assertRejected(mutateRoot { it.remove("candidateRoads") })
    @Test fun unsupportedCompetingRoadsAreRetained() {
        val json = mutateRoot {
            val competitor = it.getAsJsonArray("candidateRoads")[0].asJsonObject.deepCopy()
            competitor.addProperty("id", "unsupported-competitor")
            it.getAsJsonArray("candidateRoads").add(competitor)
        }
        val loaded = loader.load(json, now) as CatalogLoadResult.Loaded
        assertEquals(2, loaded.catalog.candidateRoads.size)
        assertEquals(1, loaded.catalog.entries.size)
    }
    @Test fun supportedRoadMustAppearInCandidates() = assertRejected(mutateRoot {
        it.getAsJsonArray("candidateRoads")[0].asJsonObject.addProperty("id", "different")
    })
    @Test fun wrongCoverageBoundsRejects() = assertRejected(mutateRoot {
        it.getAsJsonObject("matchingCoverage").getAsJsonObject("bounds").addProperty("east", 126.0)
    })
    @Test fun supportedGeometryMustBeInsideCoverage() = assertRejected(mutateRoot {
        it.getAsJsonObject("matchingCoverage").getAsJsonObject("bounds").addProperty("east", 127.001)
    })
    @Test fun legacySupportedCatalogRejectsButLegacyEmptyRemainsUnsupported() {
        assertRejected(catalog().replace("\"schemaVersion\":2", "\"schemaVersion\":1"))
        val result = loader.load(catalog("").replace("\"schemaVersion\":2", "\"schemaVersion\":1"), now) as CatalogLoadResult.Loaded
        assertFalse(result.catalog.supported)
    }
    @Test fun coverageMarginIncludesTheWholeSearchEnvelope() {
        val bounds = GeographicBounds(36.9, 126.9, 37.1, 127.1)
        assertTrue(bounds.containsWithMargin(dev.pleos.roadcontext.core.GeoPoint(37.0, 127.0), 100.0))
        assertFalse(bounds.containsWithMargin(dev.pleos.roadcontext.core.GeoPoint(37.0, 126.9), 1.0))
        assertFalse(bounds.containsWithMargin(dev.pleos.roadcontext.core.GeoPoint(37.2, 127.0), 0.0))
    }
    @Test fun unknownVersionRejects() = assertRejected(catalog().replace("\"schemaVersion\":2", "\"schemaVersion\":3"))
    @Test fun wrongSourceModeRejects() = assertRejected(catalog().replace("REAL_WORLD", "REPLAY_SYNTHETIC"))
    @Test fun stringsDoNotCoerceToBooleanOrNumber() {
        assertRejected(mutated { it.addProperty("synthetic", "false") })
        assertRejected(mutated { it.addProperty("headingDeg", "90") })
    }
    @Test fun outOfRangeCoordinateRejects() = assertRejected(catalog().replace("[127,37]", "[181,37]"))
    @Test fun adjacentDuplicateVertexRejects() = assertRejected(catalog().replace("[127.02,37]", "[127,37]"))
}
