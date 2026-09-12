/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeviceOperationTargetTest {
    private val phoneA = AdbDevice("serial-a", DeviceStatus.DEVICE, model = "Pixel")
    private val phoneB = AdbDevice("serial-b", DeviceStatus.DEVICE, model = "Pixel")

    @Test
    fun `device presentation distinguishes same model endpoints without replacing serial identity`() {
        val usb = AdbDevice("25231JEGR10182", DeviceStatus.DEVICE, model = "Pixel 6a")
        val wireless = AdbDevice(
            "adb-25231JEGR10182-s506Zv._adb-tls-connect._tcp",
            DeviceStatus.DEVICE,
            model = "Pixel 6a",
        )

        assertEquals("Pixel 6a · 25231JEGR10182", usb.displayNameWithEndpoint)
        assertEquals("Pixel 6a · adb-25231J…t._tcp", wireless.displayNameWithEndpoint)
        assertEquals("25231JEGR10182", usb.id)
        assertEquals("adb-25231JEGR10182-s506Zv._adb-tls-connect._tcp", wireless.id)
    }

    @Test
    fun `zero devices has no selection or operation target`() {
        assertNull(resolveSelectedDevice(emptyList(), null))
        assertNull(DeviceMonitorState().captureOperationTarget())
    }

    @Test
    fun `one ready device is auto selected`() {
        assertEquals(phoneA, resolveSelectedDevice(listOf(phoneA), null))
    }

    @Test
    fun `multiple ready devices require explicit selection`() {
        val devices = listOf(phoneA, phoneB)
        assertNull(resolveSelectedDevice(devices, null))
        assertNull(DeviceMonitorState(devices = devices).captureOperationTarget())
    }

    @Test
    fun `existing explicit selection remains identified by serial`() {
        val refreshedA = phoneA.copy(model = "Renamed Pixel")
        assertEquals(refreshedA, resolveSelectedDevice(listOf(refreshedA, phoneB), phoneA))
        assertEquals(phoneB, resolveExplicitDevice(listOf(refreshedA, phoneB), "serial-b"))
    }

    @Test
    fun `stale offline or missing selection fails closed without another-device fallback`() {
        val offlineA = phoneA.copy(status = DeviceStatus.OFFLINE)
        // Monitor policy may auto-select the sole remaining ready device for the
        // next action; an operation explicitly captured for A must still fail.
        assertEquals(phoneB, resolveSelectedDevice(listOf(offlineA, phoneB), phoneA))
        assertNull(resolveExplicitDevice(listOf(offlineA, phoneB), phoneA.id))
        assertNull(
            DeviceMonitorState(devices = listOf(phoneB), selectedDevice = phoneA)
                .captureOperationTarget(),
        )
    }

    @Test
    fun `captured target is immutable across global selection changes`() {
        val target = DeviceMonitorState(listOf(phoneA, phoneB), phoneA).captureOperationTarget()
        val switched = DeviceMonitorState(listOf(phoneA, phoneB), phoneB).captureOperationTarget()

        assertEquals(DeviceOperationTarget("serial-a", "Pixel"), target)
        assertEquals(DeviceOperationTarget("serial-b", "Pixel"), switched)
        assertEquals("serial-a", target?.serial)
        assertEquals("Pixel", target?.displayName)
    }

    @Test
    fun `same display names never collapse distinct serials`() {
        assertEquals("serial-a", DeviceMonitorState(listOf(phoneA, phoneB), phoneA).captureOperationTarget()?.serial)
        assertEquals("serial-b", DeviceMonitorState(listOf(phoneA, phoneB), phoneB).captureOperationTarget()?.serial)
    }

    @Test
    fun `stale async result requires matching serial and generation`() {
        assertEquals(true, shouldApplyDeviceResult("serial-b", "serial-b", 2, 2))
        assertEquals(false, shouldApplyDeviceResult("serial-b", "serial-a", 1, 2))
        assertEquals(false, shouldApplyDeviceResult("serial-a", "serial-a", 1, 2))
        assertEquals(false, shouldApplyDeviceResult(null, "serial-a", 2, 2))
    }

    @Test
    fun `device discovery generations are serial isolated and reject stale results`() {
        val guard = PerDeviceGenerationGuard()
        val firstA = guard.next("serial-a")
        val firstB = guard.next("serial-b")
        val secondA = guard.next("serial-a")

        assertEquals(false, guard.isCurrent("serial-a", firstA))
        assertEquals(true, guard.isCurrent("serial-a", secondA))
        assertEquals(true, guard.isCurrent("serial-b", firstB))
    }
}
