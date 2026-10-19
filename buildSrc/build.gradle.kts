plugins {
    java
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.gradleup.shadow:shadow-gradle-plugin:9.6.1")
    implementation("com.guardsquare:proguard-gradle:7.10.0")
    implementation("org.jetbrains:annotations:26.1.0")
}
