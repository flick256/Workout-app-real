import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin module: no Android dependencies, so its tests run fast on the JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Java 17 bytecode, matching the app module. Builds on any JDK from 17 up.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
