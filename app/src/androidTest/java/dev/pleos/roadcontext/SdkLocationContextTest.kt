package dev.pleos.roadcontext

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pleos.roadcontext.integration.LocationProvider
import dev.pleos.roadcontext.integration.SourceLocation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SdkLocationContextTest {
    @Test fun routeContextRequiresMovingFreshCoLocatedNaviAndClearsOnBoundary() {
        val context = SdkLocationContext()
        val now = SystemClock.elapsedRealtime()
        val wall = System.currentTimeMillis()
        context.refreshClockOffset(now, wall)
        val navi = SourceLocation(37.55, 126.98, 90.0, null, now - 100,
            LocationProvider.NAVI_HELPER, routeId = "fictional-route")
        val fused = SourceLocation(37.55001, 126.98, 90.0, wall - 100, now - 50,
            LocationProvider.FUSED_LOCATION)

        context.observe(navi)
        assertFalse(context.select(fused, now, wall).liveInput.contextValidated)
        context.observe(navi.copy(latitude = 37.55001))
        val selected = context.select(fused, now, wall)
        assertTrue(selected.clockValidated)
        assertTrue(selected.liveInput.contextValidated)
        assertFalse(context.select(fused, now + 3000, wall + 3000).liveInput.contextValidated)

        context.onRouteBoundary()
        assertFalse(context.select(fused, now, wall).liveInput.contextValidated)
    }
}
