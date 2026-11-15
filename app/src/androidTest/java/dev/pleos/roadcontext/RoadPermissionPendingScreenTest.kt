package dev.pleos.roadcontext

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Component UI fixtures only: explicitly supplies pending=true to the production RoadScreen.
 * Does not launch a real permission dialog, exercise the OS permission lifecycle, or prove
 * the controller sets/clears the pending flag. Device/OS integration must be verified separately.
 */
class RoadPermissionPendingScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pendingPermissionDisablesBothActionsInLightTheme() = verifyPending(dark = false)

    @Test fun pendingPermissionDisablesBothActionsInDarkTheme() = verifyPending(dark = true)

    private fun verifyPending(dark: Boolean) {
        var permissionCallbacks = 0
        var demoCallbacks = 0
        val state = RoadUiState(
            setup = true,
            settingsAllowed = true,
            permissionRequestPending = true,
            dark = dark,
        )
        compose.setContent {
            Box(Modifier.fillMaxSize().testTag("permission_pending_surface")) {
                RoadScreen(
                    state = state,
                    onDemo = { demoCallbacks++ },
                    onLive = { permissionCallbacks++ },
                    onPlay = {}, onNext = {}, onReset = {}, onTheme = {},
                )
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            try { compose.onAllNodesWithTag("permission").fetchSemanticsNodes().isNotEmpty() }
            catch (_: IllegalStateException) { false }
        }
        listOf("permission", "demo").forEach { tag ->
            compose.onNodeWithTag(tag).assertIsDisplayed().assertIsNotEnabled()
            // Pointer injection exercises the disabled Button rather than invoking its
            // accessibility action directly, which can bypass gesture handling.
            compose.onNodeWithTag(tag).performTouchInput { click(center) }
        }
        compose.runOnIdle {
            assertEquals("Pending permission must ignore permission button taps", 0, permissionCallbacks)
            assertEquals("Pending permission must ignore demo button taps", 0, demoCallbacks)
        }
        val theme = if (dark) "dark" else "light"
        val bitmap = compose.onNodeWithTag("permission_pending_surface").captureToImage().asAndroidBitmap()
        val directory = DeviceTestSupport.outputDirectory("verification-device")
        val filename = "permission-pending-$theme-component-fixture.png"
        File(directory, filename).outputStream().use {
            assertTrue("PNG capture must be written", bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        DeviceTestSupport.save("permission-pending-$theme-component-fixture", JSONObject()
            .put("scope", "Production RoadScreen component UI fixture; not an actual OS permission dialog")
            .put("theme", theme)
            .put("setup", true)
            .put("settingsAllowed", true)
            .put("permissionRequestPending", true)
            .put("permissionDisabledSemantics", true)
            .put("demoDisabledSemantics", true)
            .put("permissionCallbacksAfterPointerClick", permissionCallbacks)
            .put("demoCallbacksAfterPointerClick", demoCallbacks)
            .put("capture", filename)
            .put("captureWidthPx", bitmap.width)
            .put("captureHeightPx", bitmap.height))
    }
}
