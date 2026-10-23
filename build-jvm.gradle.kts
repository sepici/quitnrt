// Root build used with -PjvmOnly (see settings.gradle.kts). Only the plain Kotlin modules.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

apply(from = "gradle/copy-lint.gradle.kts")
