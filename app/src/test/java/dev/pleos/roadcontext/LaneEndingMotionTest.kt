package dev.pleos.roadcontext
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class LaneEndingMotionTest {
    @Test fun entryIsFollowedBeforeLateralMergeBegins() {
        for(i in 0..400) {
            val pose=LaneEndingMotion.pose(i/1000f)
            assertEquals(1f,pose.lane,.00001f)
            assertTrue(pose.depth>=.649f)
        }
        assertEquals(0f,LaneEndingMotion.pose(.83f).lane,.001f)
    }
    @Test fun motionRemainsWithinTaperAndIsContinuous() {
        var last=LaneEndingMotion.pose(0f)
        for(i in 1..1000) {
            val p=LaneEndingMotion.pose(i/1000f)
            val rightEdge=if(p.depth>=.62f)1.5f else .5f+(p.depth-.28f)/.34f
            assertTrue("Vehicle center stays on road",p.lane<=rightEdge)
            assertTrue(p.lane in 0f..1f && p.depth in .279f.. .911f)
            assertTrue(abs(p.lane-last.lane)<.01f && abs(p.depth-last.depth)<.003f)
            last=p
        }
        assertEquals(0f,last.opacity,.001f)
    }
}
