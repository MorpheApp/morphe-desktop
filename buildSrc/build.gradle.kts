plugins {
    java
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Shadow is declared as implementation so the root project's build script
    // gets these classes on its buildscript classpath at runtime. The root
    // project must then apply the shadow plugin without a version to avoid
    // the "plugin is already on the classpath" conflict.
    implementation("com.gradleup.shadow:shadow-gradle-plugin:9.4.1")
    implementation("org.jetbrains:annotations:24.1.0")
}
