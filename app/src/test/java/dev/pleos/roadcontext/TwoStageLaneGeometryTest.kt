package dev.pleos.roadcontext
import org.junit.Assert.*
import org.junit.Test
class TwoStageLaneGeometryTest {
 private val g=TwoStageLaneGeometry(334.0,390.0,588.0,628.0)
 @Test fun bothEntryLanesEndInSequence() {
  assertEquals(2.5f,g.rightEdge(100.0));assertEquals(1.5f,g.rightEdge(450.0));assertEquals(.5f,g.rightEdge(650.0))
 }
 @Test fun illustrativeVehicleAlwaysRemainsOnRoad() {
  for(i in 0..7000){val s=i/10.0;assertTrue(g.illustrativeLane(s)+.35f<g.rightEdge(s));assertTrue(g.illustrativeLane(s)>-1.15f)}
 }
 @Test fun fixedGpsNeverChangesIllustrativePosition() { assertEquals(g.illustrativeLane(360.0),g.illustrativeLane(360.0)) }
}
