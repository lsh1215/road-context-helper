package dev.pleos.roadcontext

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class MangwonIllustrativeMotionTest {
    @Test fun carCannotMoveLaterallyInTheSolidSection() {
        for (i in 0..1000) {
            val pose = MangwonIllustrativeMotion.pose(i / 1000f)
            if (pose.depth >= MangwonIllustrativeMotion.SOLID_END_DEPTH) {
                assertEquals("Solid lines mean stay in the entry lane", 1.5f, pose.lane, .00001f)
            }
            if (pose.lane < 1.499f) assertTrue("Rear clearance before sideways motion", pose.depth < .52f)
        }
    }

    @Test fun completeMotionIsContinuousAndReachesTheLeftSideWithoutTurningBack() {
        var previous = MangwonIllustrativeMotion.pose(0f)
        for (i in 1..1000) {
            val pose = MangwonIllustrativeMotion.pose(i / 1000f)
            assertTrue(pose.lane <= previous.lane)
            assertTrue(pose.depth <= previous.depth)
            assertTrue(abs(pose.lane - previous.lane) < .02f)
            assertTrue(abs(pose.depth - previous.depth) < .003f)
            assertTrue(pose.opacity in 0f..1f)
            previous = pose
        }
        assertEquals(-1.5f, previous.lane, .001f)
        assertEquals(0f, previous.opacity, .001f)
        assertEquals(0f, MangwonIllustrativeMotion.pose(0f).opacity, .001f)
    }

    @Test fun invalidInputsCannotProduceOffRoadOrNonFiniteCoordinates() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f, 2f).forEach {
            val pose = MangwonIllustrativeMotion.pose(it)
            assertTrue(pose.lane in -1.5f..1.5f)
            assertTrue(pose.depth in .28f.. .92f)
            assertTrue(pose.opacity in 0f..1f)
        }
    }
}
