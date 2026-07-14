/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

import app.morphe.engine.util.AppLinkCommands
import kotlin.test.Test
import kotlin.test.assertEquals

class AppLinkCommandsTest {

    @Test
    fun `enablePatched sets master switch then approves all hosts`() {
        val cmds = AppLinkCommands.enablePatched("app.morphe.android.youtube")
        assertEquals(
            listOf(
                listOf("pm", "set-app-links-allowed", "--user", "0", "--package", "app.morphe.android.youtube", "true"),
                listOf("pm", "set-app-links-user-selection", "--user", "0", "--package", "app.morphe.android.youtube", "true", "all"),
            ),
            cmds,
        )
    }

    @Test
    fun `disableStock turns off the master switch only`() {
        val cmds = AppLinkCommands.disableStock("com.google.android.youtube")
        assertEquals(
            listOf(
                listOf("pm", "set-app-links-allowed", "--user", "0", "--package", "com.google.android.youtube", "false"),
            ),
            cmds,
        )
    }

    @Test
    fun `restore commands invert apply commands`() {
        assertEquals(
            listOf(listOf("pm", "set-app-links-user-selection", "--user", "0", "--package", "p", "false", "all")),
            AppLinkCommands.restorePatched("p"),
        )
        assertEquals(
            listOf(listOf("pm", "set-app-links-allowed", "--user", "0", "--package", "s", "true")),
            AppLinkCommands.restoreStock("s"),
        )
    }

    @Test
    fun `custom user id is threaded through`() {
        val cmds = AppLinkCommands.enablePatched("p", user = "10")
        assertEquals("10", cmds.first()[3])
    }
}
