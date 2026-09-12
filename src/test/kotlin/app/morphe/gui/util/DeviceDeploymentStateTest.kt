package app.morphe.gui.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceDeploymentStateTest {
    @Test
    fun `device transitions stay isolated by serial`() {
        var states = emptyMap<String, DeviceDeploymentState>()
        states = states + ("A" to states.forSerial("A").installed("Installed on A"))

        assertTrue(states.forSerial("A").installed == true)
        assertEquals(DeviceDeploymentState.InstallPhase.INSTALLED, states.forSerial("A").installPhase)
        assertNull(states.forSerial("B").installed)
        assertEquals(DeviceDeploymentState.InstallPhase.IDLE, states.forSerial("B").installPhase)
    }

    @Test
    fun `link success on one serial does not affect another`() {
        val states = mapOf(
            "A" to DeviceDeploymentState("A").linksConfigured("Links routed"),
        )

        assertEquals(DeviceDeploymentState.LinkPhase.CONFIGURED, states.forSerial("A").linkPhase)
        assertEquals(DeviceDeploymentState.LinkPhase.UNKNOWN, states.forSerial("B").linkPhase)
    }

    @Test
    fun `failed install remains retryable without marking installed`() {
        val state = DeviceDeploymentState("A")
            .installing("Installing")
            .installFailed("failed")

        assertFalse(state.installed == true)
        assertEquals(DeviceDeploymentState.InstallPhase.FAILED, state.installPhase)
        assertEquals("failed", state.installError)
    }
}
