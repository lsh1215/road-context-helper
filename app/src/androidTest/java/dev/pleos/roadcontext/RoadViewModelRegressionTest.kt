package dev.pleos.roadcontext

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pleos.roadcontext.core.*
import dev.pleos.roadcontext.data.*
import dev.pleos.roadcontext.integration.SourceEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises ViewModel event/presentation boundaries with explicit fictional fixtures.
 * Does NOT prove real SDK delivery, permissions at OS resume, or valid real-road data.
 */
@RunWith(AndroidJUnit4::class)
class RoadViewModelRegressionTest {
    private fun withModel(test: (RoadViewModel) -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val model = RoadViewModel(ApplicationProvider.getApplicationContext<Application>())
            val store = ViewModelStore()
            store.put("regression", model)
            try {
                model.setRestrictions(known = true, requiresDO = false, flags = 0)
                test(model)
            } finally {
                store.clear() // closes bounded diagnostics and any lifecycle resources
            }
        }
    }

    private fun liveResult(state: GuidanceState): LiveGuidanceResult {
        val evidence = RoadEvidence(true, "https://example.test/fictional-viewmodel-fixture", 0, Long.MAX_VALUE,
            "EPSG:4326", "TEST-ONLY-NOT-FOR-REAL-USE", "fictional-test")
        val road = RoadSegment("fictional-viewmodel-test", "test-only", 0.0, 600.0, 800.0, 90.0,
            15.0, false, evidence)
        val entry = RuntimeRoadEntry(road, RoadPolyline(road.id,
            listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.02))), dev.pleos.roadcontext.data.MergeSide.RIGHT,
            SourceProvenance(evidence.sourceUrl, "test", "a".repeat(64), "Fictional UI boundary fixture", "test", 0, Long.MAX_VALUE))
        val reason = when (state) {
            GuidanceState.APPROACHING -> GuidanceReason.NEAR_MERGE
            GuidanceState.PASSED -> GuidanceReason.SEGMENT_PASSED
            else -> GuidanceReason.STRUCTURE_PREVIEW
        }
        val decision = GuidanceDecision(state, reason, if (state == GuidanceState.PASSED) null else 150.0,
            1, InputSource.REAL_SDK, road.id, road.version, 1)
        return LiveGuidanceResult(if (state == GuidanceState.PASSED) LiveGuidanceStatus.PASSED else LiveGuidanceStatus.GUIDANCE,
            decision, entry)
    }

    @Test fun readerLocalGenerationRestartCannotRewindViewModelGeneration() = withModel { model ->
        model.consume(SourceEvent.RouteBoundary(1, "session_started"))
        model.consume(SourceEvent.RouteBoundary(2, "route_id_changed"))
        model.consume(SourceEvent.RouteBoundary(3, "reroute_ended"))
        val oldGeneration = model.currentRouteGeneration
        model.consume(SourceEvent.RouteBoundary(1, "session_started"))
        assertTrue("New reader's local counter must not rewind coordinator generation",
            model.currentRouteGeneration > oldGeneration)
        val sessionGeneration = model.currentRouteGeneration
        model.consume(SourceEvent.RouteBoundary(2, "route_id_changed"))
        assertTrue(model.currentRouteGeneration > sessionGeneration)
    }

    @Test fun liveDiagramCannotLeakIntoSyntheticSession() = withModel { model ->
        model.presentLive(liveResult(GuidanceState.PREVIEW))
        assertEquals(DiagramSource.REAL_SDK, model.ui.approvedDiagram()!!.source)
        model.demo()
        assertNull(model.ui.diagramModel)
        repeat(3) { model.next() }
        val diagram = model.ui.approvedDiagram()
        assertNotNull("Synthetic preview must not be rejected by a leftover real DiagramModel", diagram)
        assertEquals(DiagramSource.REPLAY_SYNTHETIC, diagram!!.source)
        assertTrue(diagram.synthetic)
        assertNotEquals("fictional-viewmodel-test", diagram.segmentId)
    }

    @Test fun liveApproachUsesApproachCopyInsteadOfPreviewCopy() = withModel { model ->
        model.presentLive(liveResult(GuidanceState.PREVIEW))
        assertEquals("미리 보는 도로 구조", model.ui.phase)
        assertTrue(model.ui.title.contains("이어집니다"))
        model.presentLive(liveResult(GuidanceState.APPROACHING))
        assertEquals("합류 구간 접근", model.ui.phase)
        assertTrue(model.ui.title.contains("합쳐집니다"))
        assertFalse(model.ui.title.contains("앞까지 이어집니다"))
        assertTrue(model.ui.showDiagram)
    }

    @Test fun passageClearsDiagramAndDistanceWithoutConfirmation() = withModel { model ->
        model.presentLive(liveResult(GuidanceState.APPROACHING))
        assertTrue(model.ui.showDiagram)
        model.presentLive(liveResult(GuidanceState.PASSED))
        assertFalse(model.ui.showDiagram)
        assertNull(model.ui.diagramModel)
        assertNull(model.ui.distance)
        assertTrue(model.ui.title.contains("지났습니다"))
    }

    @Test fun sourceFailureImmediatelyRemovesLivePresentation() = withModel { model ->
        model.presentLive(liveResult(GuidanceState.PREVIEW))
        model.consume(SourceEvent.Failure("NaviHelper", "TEST_DISCONNECT", "Fictional failure input"))
        assertFalse(model.ui.showDiagram)
        assertNull(model.ui.diagramModel)
        assertNull(model.ui.distance)
        assertEquals("연결 확인 중", model.ui.phase)
    }

    @Test fun sourceBoundaryImmediatelyRemovesOldLiveCard() = withModel { model ->
        model.presentLive(liveResult(GuidanceState.PREVIEW))
        model.consume(SourceEvent.RouteBoundary(1, "route_cancelled"))
        assertFalse(model.ui.showDiagram)
        assertNull(model.ui.diagramModel)
        assertNull(model.ui.distance)
    }

    @Test fun stoppedLiveScreenDoesNotRetainOldGuidance() = withModel { model ->
        model.presentLive(liveResult(GuidanceState.PREVIEW))
        model.stop()
        assertFalse(model.ui.showDiagram)
        assertNull(model.ui.diagramModel)
        assertNull(model.ui.distance)
    }

    @Test fun syntheticSessionIgnoresLivePresentationInputs() = withModel { model ->
        model.demo()
        repeat(3) { model.next() }
        val before = model.ui
        model.presentLive(liveResult(GuidanceState.APPROACHING))
        assertEquals(before, model.ui)
        assertEquals(DiagramSource.REPLAY_SYNTHETIC, model.ui.approvedDiagram()!!.source)
    }

    @Test fun drivingRestrictionsStopSyntheticPlaybackAndPreventManualAdvance() = withModel { model ->
        model.demo()
        model.togglePlay()
        assertTrue(model.ui.playing)
        model.setRestrictions(known = true, requiresDO = true, flags = 1)
        val restricted = model.ui
        assertFalse(restricted.playing)
        assertFalse(restricted.settingsAllowed)
        model.next()
        model.demo()
        model.live()
        assertEquals(restricted, model.ui)
    }

    @Test fun unsupportedLocationImmediatelyReplacesGuidanceWithExplicitUnsupportedState() = withModel { model ->
        model.presentLive(liveResult(GuidanceState.PREVIEW))
        assertTrue(model.ui.showDiagram)
        model.consume(SourceEvent.Connection("FusedLocation", "UNSUPPORTED"))
        assertEquals("위치 기능 미지원", model.ui.phase)
        assertTrue(model.ui.title.contains("사용할 수 없습니다"))
        assertTrue(model.ui.message.contains("지원하지 않습니다"))
        assertFalse(model.ui.showDiagram)
        assertNull(model.ui.diagramModel)
        assertNull(model.ui.distance)
    }

    @Test fun genericLiveOrTickPresentationCannotOverwriteUnsupportedState() = withModel { model ->
        model.consume(SourceEvent.Connection("FusedLocation", "UNSUPPORTED"))
        val unsupportedTitle = model.ui.title
        val subsequentResults = listOf(
            LiveGuidanceResult(LiveGuidanceStatus.WAITING),
            LiveGuidanceResult(LiveGuidanceStatus.SOURCE_UNAVAILABLE),
            LiveGuidanceResult(LiveGuidanceStatus.NO_APPROVED_ROADS),
            liveResult(GuidanceState.PREVIEW),
        )
        subsequentResults.forEach { result ->
            // This exercises the presentation boundary used by the ticker, not Handler timing.
            model.presentLive(result)
            assertEquals("위치 기능 미지원", model.ui.phase)
            assertEquals(unsupportedTitle, model.ui.title)
            assertFalse(model.ui.showDiagram)
            assertNull(model.ui.diagramModel)
        }
    }

    @Test fun unsupportedLiveLocationDoesNotPreventExplicitSyntheticDemonstration() = withModel { model ->
        model.consume(SourceEvent.Connection("FusedLocation", "UNSUPPORTED"))
        model.demo()
        repeat(3) { model.next() }
        assertTrue(model.ui.simulation)
        assertTrue(model.ui.showDiagram)
        assertEquals("미리 보는 도로 구조", model.ui.phase)
        assertEquals(DiagramSource.REPLAY_SYNTHETIC, model.ui.approvedDiagram()!!.source)
        model.presentLive(LiveGuidanceResult(LiveGuidanceStatus.SOURCE_UNAVAILABLE))
        assertTrue(model.ui.showDiagram)
        assertEquals(DiagramSource.REPLAY_SYNTHETIC, model.ui.approvedDiagram()!!.source)
    }
}
