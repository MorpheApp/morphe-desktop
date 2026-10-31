/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.engine.model.SupportedApp
import app.morphe.engine.model.VersionStatus
import app.morphe.engine.patches.resolveVersionStatus
import kotlin.test.Test

class BuildCodeSupportTest {

    private fun app(
        stable: List<String> = listOf("380.0.0.29.109"),
        experimental: List<String> = emptyList(),
        buildCodes: Map<String, Set<Int>> = emptyMap(),
    ) = SupportedApp(
        packageName = "com.instagram.android",
        displayName = "Instagram",
        supportedVersions = stable,
        experimentalVersions = experimental,
        recommendedVersion = stable.firstOrNull(),
        versionBuildCodes = buildCodes,
    )

    @Test
    fun noConstraintAcceptsAnyBuild() {
        val subject = app()
        check(subject.buildCodeSupported("380.0.0.29.109", 123)) { "unconstrained version rejected a build" }
        check(subject.buildCodeSupported("380.0.0.29.109", null)) { "unconstrained version rejected a null build" }
    }

    @Test
    fun constrainedVersionAcceptsOnlyItsBuilds() {
        val subject = app(buildCodes = mapOf("380.0.0.29.109" to setOf(700, 701)))
        check(subject.buildCodeSupported("380.0.0.29.109", 700)) { "a targeted build was rejected" }
        check(!subject.buildCodeSupported("380.0.0.29.109", 999)) { "an untargeted build was accepted" }
    }

    @Test
    fun unreadableBuildPasses() {
        val subject = app(buildCodes = mapOf("380.0.0.29.109" to setOf(700)))
        check(subject.buildCodeSupported("380.0.0.29.109", null)) { "a null build was rejected" }
    }

    @Test
    fun versionPrefixIsIgnoredWhenMatching() {
        val subject = app(buildCodes = mapOf("380.0.0.29.109" to setOf(700)))
        check(!subject.buildCodeSupported("v380.0.0.29.109", 999)) { "a v-prefixed version skipped the check" }
        check(subject.buildCodeSupported("v380.0.0.29.109", 700)) { "a v-prefixed version rejected a good build" }
    }

    @Test
    fun statusReportsBuildMismatchRatherThanClaimingSupport() {
        val subject = app(buildCodes = mapOf("380.0.0.29.109" to setOf(700)))
        val wrongBuild = resolveVersionStatus("380.0.0.29.109", subject, versionCode = 999)
        check(wrongBuild.status == VersionStatus.BUILD_UNSUPPORTED) {
            "expected BUILD_UNSUPPORTED, got ${wrongBuild.status}"
        }
        check(wrongBuild.requiredBuildCodes == setOf(700)) {
            "expected requiredBuildCodes to be [700], got ${wrongBuild.requiredBuildCodes}"
        }
        val rightBuild = resolveVersionStatus("380.0.0.29.109", subject, versionCode = 700)
        check(rightBuild.status == VersionStatus.RECOMMENDED) {
            "a targeted build should still read as supported, got ${rightBuild.status}"
        }
    }

    @Test
    fun buildCheckAppliesToExperimentalVersionsToo() {
        val subject = app(
            stable = listOf("379.0.0.0.0"),
            experimental = listOf("380.0.0.29.109"),
            buildCodes = mapOf("380.0.0.29.109" to setOf(700)),
        )
        val resolved = resolveVersionStatus("380.0.0.29.109", subject, versionCode = 999)
        check(resolved.status == VersionStatus.BUILD_UNSUPPORTED) {
            "experimental versions skipped the build check, got ${resolved.status}"
        }
        check(resolved.requiredBuildCodes == setOf(700)) {
            "expected requiredBuildCodes to be [700], got ${resolved.requiredBuildCodes}"
        }
    }

    @Test
    fun oneUnconstrainedPatchOpensTheWholeVersion() {
        val subject = app(buildCodes = mapOf("380.0.0.29.109" to emptySet()))
        check(subject.buildCodeSupported("380.0.0.29.109", 999)) {
            "an unconstrained target should accept any build"
        }
        val resolved = resolveVersionStatus("380.0.0.29.109", subject, versionCode = 999)
        check(resolved.status == VersionStatus.RECOMMENDED) {
            "unconstrained version reported as a build mismatch: ${resolved.status}"
        }
    }

    @Test
    fun unknownVersionIsUnaffectedByBuildCodes() {
        val subject = app(buildCodes = mapOf("380.0.0.29.109" to setOf(700)))
        val resolved = resolveVersionStatus("999.0.0.0.0", subject, versionCode = 1)
        check(resolved.status == VersionStatus.UNSUPPORTED) { "expected UNSUPPORTED, got ${resolved.status}" }
    }

    @Test
    fun resolvesRecommendedSupportedAndExperimentalCorrectly() {
        val subject = app(
            stable = listOf("19.16.39", "19.11.43", "19.05.36"),
            experimental = listOf("19.20.01"),
        )
        val recommended = resolveVersionStatus("19.16.39", subject)
        check(recommended.status == VersionStatus.RECOMMENDED) { "expected RECOMMENDED, got ${recommended.status}" }

        val olderSupported = resolveVersionStatus("19.11.43", subject)
        check(olderSupported.status == VersionStatus.SUPPORTED) { "expected SUPPORTED, got ${olderSupported.status}" }
        check(olderSupported.suggestedVersion == "19.16.39") { "expected suggestedVersion 19.16.39, got ${olderSupported.suggestedVersion}" }

        val experimental = resolveVersionStatus("19.20.01", subject)
        check(experimental.status == VersionStatus.EXPERIMENTAL) { "expected EXPERIMENTAL, got ${experimental.status}" }

        val tooNew = resolveVersionStatus("20.00.00", subject)
        check(tooNew.status == VersionStatus.UNSUPPORTED) { "expected UNSUPPORTED, got ${tooNew.status}" }

        val tooOld = resolveVersionStatus("18.00.00", subject)
        check(tooOld.status == VersionStatus.UNSUPPORTED) { "expected UNSUPPORTED, got ${tooOld.status}" }
    }
}
