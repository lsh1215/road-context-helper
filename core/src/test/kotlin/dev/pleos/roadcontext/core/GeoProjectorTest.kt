package dev.pleos.roadcontext.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class GeoProjectorTest {
    private val projector = GeoProjector()
    private val metersPerDegree = GeoProjector.EARTH_RADIUS_METERS * PI / 180
    private fun road(id: String = "test-road", vararg points: GeoPoint) = RoadPolyline(id, points.toList())
    private val eastbound = road(points = arrayOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.01)))

    @Test fun `equator projection returns meters not coordinate degrees`() {
        val p = projector.project(GeoPoint(0.0001, 0.005), listOf(eastbound)).unambiguousCandidate!!
        assertEquals(0.005 * metersPerDegree, p.alongMeters, 0.001)
        assertEquals(0.0001 * metersPerDegree, p.crossMeters, 0.001)
        assertEquals(90.0, p.headingDeg, 0.001)
        assertEquals(0.0, p.closestPoint.latitude, 1e-10)
    }

    @Test fun `reversed vertices reverse along axis heading and cross sign`() {
        val p = projector.project(GeoPoint(0.0001, 0.002), listOf(eastbound.copy(points = eastbound.points.reversed()))).unambiguousCandidate!!
        assertEquals(0.008 * metersPerDegree, p.alongMeters, 0.001)
        assertEquals(-0.0001 * metersPerDegree, p.crossMeters, 0.001)
        assertEquals(270.0, p.headingDeg, 0.001)
    }

    @Test fun `bent polyline uses cumulative distance not straight endpoint distance`() {
        val bent = road(points = arrayOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.01), GeoPoint(0.01, 0.01)))
        val p = projector.project(GeoPoint(0.005, 0.0101), listOf(bent)).unambiguousCandidate!!
        assertEquals(0.015 * metersPerDegree, p.alongMeters, 0.01)
        assertEquals(0.0, p.headingDeg, 0.001)
        assertEquals(1, p.segmentIndex)
        assertTrue(p.crossMeters < 0)
    }

    @Test fun `shared vertex is not two independent road positions`() {
        val bent = road(points = arrayOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.01), GeoPoint(0.01, 0.01)))
        val r = projector.project(GeoPoint(0.0, 0.01), listOf(bent))
        assertFalse(r.ambiguous)
        assertEquals(1, r.candidates.size)
    }

    @Test fun `dense vertices on one straight road do not create false ambiguity`() {
        val dense = RoadPolyline("dense", (0..100).map { GeoPoint(0.0, it * 0.00001) })
        val p = projector.project(GeoPoint(0.00001, 0.000505), listOf(dense))
        assertFalse(p.ambiguous)
        assertNotNull(p.unambiguousCandidate)
        assertEquals(0.000505 * metersPerDegree, p.unambiguousCandidate!!.alongMeters, 0.001)
    }

    @Test fun `parallel roads inside uncertainty remain ambiguous`() {
        val parallel = eastbound.copy(id = "parallel", points = eastbound.points.map { it.copy(latitude = 0.0001) })
        val r = projector.project(GeoPoint(0.00001, 0.005), listOf(eastbound, parallel), uncertaintyMeters = 20.0)
        assertTrue(r.ambiguous)
        assertNull(r.unambiguousCandidate)
        assertEquals(2, r.candidates.size)
    }

    @Test fun `overlapping upper and lower roads cannot be selected by level tag`() {
        val r = projector.project(GeoPoint(0.0, 0.005), listOf(eastbound.copy(levelTag = "ground"), eastbound.copy(id = "overpass", levelTag = "upper")))
        assertTrue(r.ambiguous)
        assertNull(r.unambiguousCandidate)
    }

    @Test fun `self crossing retains different along positions`() {
        val loop = road(points = arrayOf(GeoPoint(-0.001, -0.001), GeoPoint(0.001, 0.001), GeoPoint(0.001, -0.001), GeoPoint(-0.001, 0.001)))
        val r = projector.project(GeoPoint(0.0, 0.0), listOf(loop))
        assertTrue(r.ambiguous)
        assertEquals(2, r.candidates.size)
        assertNull(r.unambiguousCandidate)
    }

    @Test fun `out of corridor does not fabricate projection`() {
        assertTrue(projector.project(GeoPoint(1.0, 1.0), listOf(eastbound)).candidates.isEmpty())
    }

    @Test fun `bounded segment accounts for distance past its endpoint`() {
        val p = projector.project(GeoPoint(0.0, 0.0101), listOf(eastbound)).unambiguousCandidate!!
        assertEquals(0.01 * metersPerDegree, p.alongMeters, 0.001)
        assertEquals(0.0001 * metersPerDegree, p.distanceMeters, 0.001)
        assertEquals(0.0, p.crossMeters, 0.001)
    }

    @Test fun `dateline is short arc not nearly whole planet`() {
        val r = road(points = arrayOf(GeoPoint(0.0, 179.999), GeoPoint(0.0, -179.999)))
        val p = projector.project(GeoPoint(0.0, 180.0), listOf(r)).unambiguousCandidate!!
        assertEquals(0.001 * metersPerDegree, p.alongMeters, 0.001)
        assertEquals(90.0, p.headingDeg, 0.001)
    }

    @Test fun `invalid coordinates crs and geometry are rejected`() {
        listOf(GeoPoint(Double.NaN, 0.0), GeoPoint(0.0, Double.POSITIVE_INFINITY), GeoPoint(91.0, 0.0), GeoPoint(0.0, 181.0)).forEach {
            assertEquals("INVALID_LOCATION", projector.project(it, listOf(eastbound)).rejections.single().reason)
        }
        assertEquals("UNSUPPORTED_CRS", projector.project(GeoPoint(0.0, 0.0), listOf(eastbound.copy(crs = "EPSG:5179"))).rejections.single().reason)
        assertEquals("INSUFFICIENT_VERTICES", projector.project(GeoPoint(0.0, 0.0), listOf(eastbound.copy(points = listOf(GeoPoint(0.0, 0.0))))).rejections.single().reason)
        assertEquals("INVALID_ROAD_COORDINATE", projector.project(GeoPoint(0.0, 0.0), listOf(eastbound.copy(points = listOf(GeoPoint(0.0, 0.0), GeoPoint(999.0, 999.0))))).rejections.single().reason)
    }

    @Test fun `duplicate vertices are ignored but zero length road rejected`() {
        val a = GeoPoint(0.0, 0.0)
        val r = projector.project(GeoPoint(0.0, 0.005), listOf(eastbound.copy(points = listOf(a, a, eastbound.points.last()))))
        assertNotNull(r.unambiguousCandidate)
        assertEquals("ZERO_LENGTH_ROAD", projector.project(a, listOf(road(points = arrayOf(a, a)))).rejections.single().reason)
    }

    @Test fun `antipodes and duplicate road ids cannot establish unique topology`() {
        val antipodal = road(points = arrayOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 180.0)))
        assertEquals("ANTIPODAL_SEGMENT", projector.project(GeoPoint(0.0, 0.0), listOf(antipodal)).rejections.single().reason)
        val duplicate = projector.project(GeoPoint(0.0, 0.005), listOf(eastbound, eastbound))
        assertEquals(2, duplicate.rejections.size)
        assertNull(duplicate.unambiguousCandidate)
    }

    @Test fun `invalid input policy fails explicitly and partial invalid catalogue is not trusted`() {
        assertEquals("INVALID_DISTANCE_POLICY", projector.project(GeoPoint(0.0, 0.0), listOf(eastbound), uncertaintyMeters = -1.0).rejections.single().reason)
        val r = projector.project(GeoPoint(0.0, 0.005), listOf(eastbound, eastbound.copy(id = "invalid", crs = "LOCAL_METERS_SYNTHETIC")))
        assertEquals(1, r.candidates.size)
        assertNull(r.unambiguousCandidate)
    }
}
