/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.engine

internal fun isWindows(): Boolean {
    return System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
}