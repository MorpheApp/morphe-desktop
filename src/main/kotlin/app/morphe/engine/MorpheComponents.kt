/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

import java.util.Properties

/** Resolves runtime version metadata for bundled Morphe ecosystem components. */
object MorpheComponents {

    /** Version of morphe-patcher on the classpath. */
    val patcherVersion: String? by lazy {
        runCatching {
            MorpheComponents::class.java
                .getResourceAsStream("/app/morphe/patcher/version.properties")
                ?.use { Properties().apply { load(it) }.getProperty("version") }
                ?.trim()
                ?.takeUnless { it.isEmpty() || it.startsWith($$"${") }
        }.getOrNull()
    }
}
