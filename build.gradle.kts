import app.morphe.tasks.GenerateBootstrapConstantsTask
import app.morphe.tasks.WriteMorpheComponentsTask
import com.mikepenz.aboutlibraries.plugin.DuplicateMode
import com.mikepenz.aboutlibraries.plugin.DuplicateRule
import proguard.gradle.ProGuardTask

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose)
    alias(libs.plugins.about.libraries)
    // Shadow plugin is provided by buildSrc to enable the custom NoticeMergeTransformer.
    // Applied without a version here; the version is pinned in buildSrc/build.gradle.kts.
    id("com.gradleup.shadow")
    application
    `maven-publish`
    signing
}

group = "app.morphe"

kotlin {
    jvmToolchain(21)
    sourceSets.main {
        kotlin.srcDir(layout.buildDirectory.dir("generated/source/bootstrap/main"))
    }
}

application {
    mainClass.set("app.morphe.MorpheLauncherKt")
}

repositories {
    mavenLocal()
    mavenCentral()
    google()
    maven {
        // A repository must be specified for some reason. "registry" is a dummy.
        url = uri("https://maven.pkg.github.com/MorpheApp/registry")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
    // Obtain baksmali/smali from source builds - https://github.com/iBotPeaches/smali
    // Remove when official smali releases come out again.
    maven { url = uri("https://jitpack.io") }
}

// Resolves Skiko and JNA at build time to calculate SHA-256 hashes for the app's startup bootstrap.
val bootstrapDependencies = configurations.create("bootstrapDependencies") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = true
    extendsFrom(configurations.implementation.get())
}

dependencies {
    api(libs.morphe.patcher)
    implementation(libs.arsclib)
    implementation(libs.morphe.library) {
        // jadb is unused by morphe-desktop and leaks mockito-core/bytebuddy into the fat JAR
        exclude(group = "app.morphe", module = "jadb")
    }
    implementation(libs.picocli)
    implementation(libs.bundles.compose.desktop)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.slf4j.nop)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.navigation.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.jna.core)
    implementation(libs.jna.platform)
    implementation(libs.oshi.core)
    implementation(libs.filekit.dialogs)
    implementation(libs.about.libraries.core)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.params)
}

aboutLibraries {
    collect {
        configPath = file("aboutlibraries")
    }
    library {
        duplicationMode = DuplicateMode.MERGE
        duplicationRule = DuplicateRule.EXACT
    }
}

tasks {
    val generateBootstrapConstants = register<GenerateBootstrapConstantsTask>("generateBootstrapConstants") {
        description = "Generates BootstrapConstants.kt containing Skiko and JNA runtime artifact hashes for OS platforms"
        artifactFiles.from(bootstrapDependencies)
        skikoVersion.set(provider {
            bootstrapDependencies.incoming.resolutionResult.allComponents
                .mapNotNull { it.moduleVersion }
                .firstOrNull { it.group == "org.jetbrains.skiko" }
                ?.version
                ?: error("Could not resolve Skiko version from Compose dependencies")
        })
        jnaVersion.set(libs.versions.jna)
        oshiVersion.set(libs.versions.oshi)
        outputDir.set(layout.buildDirectory.dir("generated/source/bootstrap/main/app/morphe/engine"))
    }

    named("compileKotlin") {
        dependsOn(generateBootstrapConstants)
    }

    jar {
        manifest {
            attributes(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Enable-Native-Access" to "ALL-UNNAMED"
            )
        }
    }

    test {
        useJUnitPlatform()
        testLogging {
            events("FAILED")
        }
    }

    // Write the *resolved* morphe library version (not the catalog pin). Patcher ships
    // its own version.properties.
    val writeMorpheComponents = register<WriteMorpheComponentsTask>("writeMorpheComponents") {
        description = "Writes the resolved morphe-library version to components.properties"
        libraryVersion.set(provider {
            configurations.named("runtimeClasspath").get().incoming.resolutionResult.allComponents
                .mapNotNull { it.moduleVersion }
                .firstOrNull { it.group == "app.morphe" && it.name.startsWith("morphe-library") }
                ?.version
                ?: "unknown"
        })
        outputFile.set(layout.buildDirectory.file("generated/morphe-components/app/morphe/cli/components.properties"))
    }

    processResources {
        // Make sure the licenses are generated before the resources are processed
        dependsOn("exportLibraryDefinitions", writeMorpheComponents)
        from(layout.buildDirectory.file("generated/aboutLibraries/aboutlibraries.json"))
        from(layout.buildDirectory.dir("generated/morphe-components"))

        // Inject project version into version.properties
        filesMatching("app/morphe/cli/version.properties") {
            expand(
                "projectVersion" to project.version,
            )
        }
        // Bundle root NOTICE and LICENSE into META-INF for license compliance
        from(listOf(rootProject.file("NOTICE"), rootProject.file("LICENSE"))) {
            into("META-INF")
        }
    }

    shadowJar {
        dependencies {
            exclude(dependency("org.jetbrains.skiko:skiko-awt-runtime-.*:.*"))
            exclude(dependency("org.jetbrains.skiko:skiko-awt:.*"))
            exclude(dependency("net.java.dev.jna:.*"))
            exclude(dependency("com.github.oshi:.*"))
        }

        exclude(
            "/prebuilt/linux/aapt",
            "/prebuilt/windows/aapt.exe",
            "/prebuilt/*/aapt_*",
            "META-INF/maven/**",
            "META-INF/*.SF",
            "META-INF/*.DSA",
            "META-INF/*.RSA",
        )

        // Default to EXCLUDE to prevent duplicate native lib bloat, but INCLUDE NOTICE and services for merging
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        filesMatching(listOf(
            "META-INF/NOTICE",
            "META-INF/NOTICE.txt",
            "META-INF/NOTICE.md",
            "META-INF/*.kotlin_module",
            "META-INF/services/*",
        )) {
            duplicatesStrategy = DuplicatesStrategy.INCLUDE
        }
        from(rootProject.file("NOTICE"), rootProject.file("LICENSE"))
        minimize {
            // Core patcher & bytecode manipulation
            exclude(dependency("app.morphe:morphe-patcher"))
            exclude(dependency("com.github.MorpheApp:ARSCLib"))
            exclude(dependency("org.bouncycastle:.*"))

            // Libraries using ServiceLoader (SPI) or reflection
            exclude(dependency("io.ktor:.*"))
            exclude(dependency("org.slf4j:.*"))
            exclude(dependency("com.squareup.okhttp3:.*"))
            exclude(dependency("io.insert-koin:.*"))
            exclude(dependency("org.jetbrains.kotlinx:kotlinx-coroutines-swing"))
            exclude(dependency("io.github.vinceglb:.*"))
            // dbus-java: SPI unix-socket transport used by FileKit on Linux
            exclude(dependency("com.github.hypfvieh:.*"))
        }

        mergeServiceFiles()

        // Merge dependency NOTICE files verbatim for Apache 2.0 attribution without ASF boilerplate
        transform(NoticeMergeTransformer::class.java)
    }

    distTar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    distZip {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    publish {
        dependsOn("overwriteShadowJar")
    }

    assemble {
        dependsOn("overwriteShadowJar")
    }

    val minifyShadowJar = register<ProGuardTask>("minifyShadowJar") {
        description = "Minifies the shadow JAR using ProGuard rules"
        // Only run when explicitly requested via assemble or direct call
        enabled = gradle.startParameter.taskNames.any { it.contains("assemble") || it.contains("minify") || it.contains("publish") }
        dependsOn(shadowJar)

        // Use the JAR produced by shadowJar as input
        val shadowJarFile = shadowJar.get().archiveFile.get().asFile
        // Use a temporary file for the ProGuard output
        val tempJarFile = file(shadowJarFile.absolutePath.replace(".jar", ".tmp.jar"))

        injars(shadowJarFile)
        outjars(tempJarFile)

        // Load the Proguard configuration
        configuration("proguard-rules.pro")

        // Include the Java runtime classes
        val javaHome = System.getProperty("java.home")
        libraryjars("$javaHome/jmods")
        
        // Include all dependencies as library jars so Proguard can resolve hierarchies
        // for classes excluded from the fat JAR (like JNA and Skiko runtimes).
        libraryjars(configurations.runtimeClasspath.get().filter { it.exists() })

        // Report on what was removed
        printusage(layout.buildDirectory.file("proguard/usage.txt").get().asFile)
    }

    val overwriteShadowJar = register("overwriteShadowJar") {
        description = "Overwrites the shadow JAR with the ProGuard minified output"
        dependsOn(minifyShadowJar)
        val shadowJarFile = shadowJar.get().archiveFile.get().asFile
        val tempJarFile = file(shadowJarFile.absolutePath.replace(".jar", ".tmp.jar"))
        
        // Only run if minification actually happened
        onlyIf { minifyShadowJar.get().enabled && tempJarFile.exists() }
        
        doLast {
            tempJarFile.copyTo(shadowJarFile, overwrite = true)
            tempJarFile.delete()
        }
    }

    // Ensure all shadow-related tasks wait for minification to finish before using the JAR.
    // This prevents "zip END header not found" errors caused by concurrent access.
    named("shadowDistZip") { dependsOn(overwriteShadowJar) }
    named("shadowDistTar") { dependsOn(overwriteShadowJar) }
    named("startShadowScripts") { dependsOn(overwriteShadowJar) }
}

// Needed by gradle-semantic-release-plugin.
// Tracking: https://github.com/KengoTODA/gradle-semantic-release-plugin/issues/435

// The maven-publish is also necessary to make the signing plugin work.
publishing {
    repositories {
        mavenLocal()
    }

    publications {
        create<MavenPublication>("morphe-desktop-publication") {
            from(components["java"])
        }
    }
}

signing {
    useGpgCmd()

    sign(publishing.publications["morphe-desktop-publication"])
}
