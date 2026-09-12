/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.installation

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AdbDeviceTargetSafetyTest {
    @Test
    fun `explicit A is captured exactly while A and B are ready`() {
        val runner = RecordingRunner(devices(aReady, bReady))

        val target = resolveAdbDeviceTarget("adb", "A", runner)

        assertEquals(AdbDeviceTarget("adb", "A"), target)
        assertEquals(listOf(listOf("adb", "devices")), runner.commands)
    }

    @Test
    fun `explicit offline A fails closed while B is ready`() {
        val runner = RecordingRunner(devices("A\toffline", bReady))

        val error = assertFailsWith<AdbInstallException> {
            resolveAdbDeviceTarget("adb", "A", runner)
        }

        assertContains(error.message.orEmpty(), "Device 'A' is not ready (state: offline)")
        assertEquals(listOf(listOf("adb", "devices")), runner.commands)
    }

    @Test
    fun `explicit disconnected A fails closed while B is ready`() {
        val runner = RecordingRunner(devices(bReady))

        val error = assertFailsWith<AdbInstallException> {
            resolveAdbDeviceTarget("adb", "A", runner)
        }

        assertEquals("Device 'A' is not connected", error.message)
        assertEquals(listOf(listOf("adb", "devices")), runner.commands)
    }

    @Test
    fun `multiple ready devices without serial fail deterministically before install mutation`() {
        val runner = RecordingRunner(devices(bReady, aReady))
        val mutations = mutableListOf<String>()

        val error = assertFailsWith<AdbInstallException> {
            resolveAdbDeviceTargets("adb", emptyList(), runner).forEach { mutations += it.serial }
        }

        assertEquals(
            "Multiple ready ADB devices are connected. Specify a device serial.\n" +
                "Available devices:\n- A\n- B",
            error.message,
        )
        assertTrue(mutations.isEmpty())
    }

    @Test
    fun `one ready device without serial captures that device`() {
        val target = resolveAdbDeviceTarget(
            "adb",
            requestedSerial = null,
            runner = RecordingRunner(devices("OFFLINE\toffline", aReady)),
        )

        assertEquals("A", target.serial)
    }

    @Test
    fun `zero ready devices fail closed`() {
        val error = assertFailsWith<AdbInstallException> {
            resolveAdbDeviceTarget(
                "adb",
                requestedSerial = null,
                runner = RecordingRunner(devices("A\toffline", "B\tunauthorized")),
            )
        }

        assertEquals("No authorized ADB device is connected and ready", error.message)
    }

    @Test
    fun `install link routing stays on captured A after available device changes to B`() {
        val runner = RecordingRunner(devices(aReady)).apply {
            commandResult = { command ->
                if (command == listOf("adb", "devices")) {
                    AdbCommandResult(0, listing)
                } else {
                    AdbCommandResult(0, "")
                }
            }
        }
        val target = resolveAdbDeviceTarget("adb", null, runner)
        runner.listing = devices(bReady)

        AdbAppLinkRouter(runner).route(
            target,
            listOf(listOf("pm", "set-app-links", "--package", "patched.app", "1", "all")),
        ).getOrThrow()

        assertEquals(1, runner.commands.count { it == listOf("adb", "devices") })
        assertEquals(
            listOf("adb", "-s", "A", "shell", "pm", "set-app-links", "--package", "patched.app", "1", "all"),
            runner.commands.last(),
        )
        assertTrue(runner.commands.none { "B" in it })
    }

    @Test
    fun `link routing failure is attributed to captured A without fallback`() {
        val runner = RecordingRunner(devices(aReady)).apply {
            commandResult = { command ->
                when {
                    command == listOf("adb", "devices") -> AdbCommandResult(0, listing)
                    else -> AdbCommandResult(1, "device offline")
                }
            }
        }
        val target = resolveAdbDeviceTarget("adb", "A", runner)

        val result = AdbAppLinkRouter(runner).route(target, listOf(listOf("pm", "set-app-links")))

        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()?.message.orEmpty(), "Link routing failed on A")
        assertTrue(runner.commands.none { "B" in it })
    }

    @Test
    fun `uninstall preflight validates every explicit target before any mutation`() {
        val runner = RecordingRunner(devices(aReady, bReady))
        val mutations = mutableListOf<String>()

        assertFailsWith<AdbInstallException> {
            resolveAdbDeviceTargets("adb", listOf("A", "MISSING"), runner)
                .forEach { mutations += it.serial }
        }

        assertTrue(mutations.isEmpty())
        assertEquals(listOf(listOf("adb", "devices")), runner.commands)
    }

    @Test
    fun `uninstall without serial rejects multiple devices before mutation`() {
        val mutations = mutableListOf<String>()

        assertFailsWith<AdbInstallException> {
            resolveAdbDeviceTargets("adb", emptyList(), RecordingRunner(devices(aReady, bReady)))
                .forEach { mutations += it.serial }
        }

        assertTrue(mutations.isEmpty())
    }

    @Test
    fun `patch install explicit offline target never resolves ready B`() {
        val runner = RecordingRunner(devices("A\toffline", bReady))

        val error = assertFailsWith<AdbInstallException> {
            resolveAdbDeviceTarget("adb", "A", runner)
        }

        assertContains(error.message.orEmpty(), "Device 'A' is not ready")
        assertTrue(runner.commands.none { "-s" in it || "B" in it })
    }

    @Test
    fun `patch install without serial rejects multiple devices before mutation`() {
        val mutations = mutableListOf<String>()

        assertFailsWith<AdbInstallException> {
            resolveAdbDeviceTarget("adb", null, RecordingRunner(devices(aReady, bReady)))
                .also { mutations += it.serial }
        }

        assertTrue(mutations.isEmpty())
    }

    private class RecordingRunner(
        var listing: String,
    ) : AdbCommandRunner {
        val commands = mutableListOf<List<String>>()
        var commandResult: (List<String>) -> AdbCommandResult = { command ->
            require(command == listOf("adb", "devices")) { "Unexpected mutation command: $command" }
            AdbCommandResult(0, listing)
        }

        override fun run(command: List<String>, onOutput: (String) -> Unit): AdbCommandResult {
            commands += command
            return commandResult(command)
        }
    }

    private fun devices(vararg rows: String) = buildString {
        appendLine("List of devices attached")
        rows.forEach(::appendLine)
    }

    private companion object {
        const val aReady = "A\tdevice"
        const val bReady = "B\tdevice"
    }
}
