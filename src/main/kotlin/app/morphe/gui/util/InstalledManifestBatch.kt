/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */
package app.morphe.gui.util

/** Length-framed, bounded manifest reads. No binary data passes through shell variables. */
internal object InstalledManifestBatch {
    const val MAX_ENTRIES = 8
    private const val MAX_MANIFEST_BYTES = 8 * 1024 * 1024

    fun command(paths: List<String>): String {
        require(paths.size in 1..MAX_ENTRIES)
        paths.forEach(ProcessInstalledAppDeviceClient::requireSafeBaseApkPath)
        val prefix = "if ! command -v awk >/dev/null 2>&1 || ! (set -o pipefail) 2>/dev/null; then printf 'U\\n'; exit 0; fi; " +
            "set -o pipefail; printf 'M1:${paths.size}\\n'; "
        // Listing is text; the actual manifest is sent directly to stdout.
        // Unquoted function variables are safe here: path validation excludes shell
        // whitespace/globs, index is generated, and length is checked as digits.
        // Avoid embedded double quotes which Windows adb argument passing strips.
        val function = "read_manifest() { " +
            "length=\$(unzip -l \$2 AndroidManifest.xml 2>/dev/null | awk '\$NF ~ /^AndroidManifest[.]xml\$/ { print \$1 }' 2>/dev/null) || { printf '%s:-1\\n' \$1; return; }; " +
            "case \$length in ''|*[!0-9]*) printf '%s:-1\\n' \$1; return;; esac; " +
            "if [ \$length -le 0 ] || [ \$length -gt $MAX_MANIFEST_BYTES ]; then " +
            "printf '%s:-1\\n' \$1; return; fi; " +
            "printf '%s:%s\\n' \$1 \$length; " +
            "unzip -p \$2 AndroidManifest.xml 2>/dev/null || exit 1; }; "
        return prefix + function + paths.mapIndexed { index, path ->
            "read_manifest $index ${ProcessInstalledAppDeviceClient.quoteRemoteShellArg(path)}"
        }.joinToString("; ")
    }

    /** null means an explicitly unavailable capability; malformed streams never trigger fallback. */
    fun decode(result: DeviceEntryResult, count: Int): List<DeviceEntryResult>? {
        require(count in 1..MAX_ENTRIES)
        if (result.exitCode == 0 && result.stdout.contentEquals("U\n".toByteArray())) return null
        fun failed(reason: String) = List(count) { DeviceEntryResult(1, byteArrayOf(), reason) }
        if (result.exitCode != 0) return failed("Manifest batch failed (exit ${result.exitCode}): ${result.stderr}")
        return try {
            val bytes = result.stdout
            var offset = 0
            fun header(): String {
                val start = offset
                while (offset < bytes.size && bytes[offset] != '\n'.code.toByte()) {
                    require(offset - start < 32 && bytes[offset].toInt() in 32..126) { "Invalid batch header" }
                    offset++
                }
                require(offset < bytes.size) { "Truncated batch header" }
                return String(bytes, start, offset++ - start, Charsets.US_ASCII)
            }
            require(header() == "M1:$count") { "Unexpected manifest batch" }
            val decoded = List(count) { index ->
                val fields = header().split(':')
                require(fields.size == 2 && fields[0] == index.toString()) { "Manifest batch order mismatch" }
                val length = fields[1].toIntOrNull() ?: error("Invalid manifest length")
                if (length == -1) DeviceEntryResult(1, byteArrayOf(), "Manifest unavailable in batch")
                else {
                    require(length in 1..MAX_MANIFEST_BYTES && length <= bytes.size - offset) { "Invalid manifest payload length" }
                    DeviceEntryResult(0, bytes.copyOfRange(offset, offset + length), "").also { offset += length }
                }
            }
            require(offset == bytes.size) { "Unexpected bytes after manifest batch" }
            decoded
        } catch (e: Exception) {
            failed("Invalid manifest batch: ${e.message}")
        }
    }
}
