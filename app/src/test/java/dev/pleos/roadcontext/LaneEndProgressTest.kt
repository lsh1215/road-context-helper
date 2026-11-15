package dev.pleos.roadcontext
import org.junit.Assert.*
import org.junit.Test
class LaneEndProgressTest {
    private val zone = LaneEndZone(334.5629,390.2343,103.5629)
    @Test fun rangesEncloseRoadviewBracketAndDecreaseWithProgress() {
        assertEquals("약 230–300m",zone.at(100.0)!!.label)
        val early=zone.at(100.0)!!; val later=zone.at(200.0)!!
        assertEquals(100.0,early.nearMeters-later.nearMeters,.0001)
        assertEquals(100.0,early.farMeters-later.farMeters,.0001)
        assertEquals("약 0–70m",zone.at(330.0)!!.label)
    }
    @Test fun enteringAndLeavingRegionNeverReportsFalseExactZero() {
        assertEquals(LaneEndPhase.APPROACH,zone.at(334.5)!!.phase)
        assertEquals("차로 끝 구간 부근",zone.at(334.5629)!!.label)
        assertEquals("차로 끝 구간 부근",zone.at(390.2343)!!.label)
        assertEquals("차로 끝 구간 지남",zone.at(390.3)!!.label)
    }
    @Test fun invalidPositionsHaveNoProgressAndRepeatedPositionIsStable() {
        for(x in listOf(Double.NaN,Double.POSITIVE_INFINITY,-1.0))assertNull(zone.at(x))
        assertEquals(zone.at(120.0),zone.at(120.0))
    }
    @Test(expected=IllegalArgumentException::class) fun reversedLandmarksRejected() { LaneEndZone(400.0,300.0,100.0) }
}
