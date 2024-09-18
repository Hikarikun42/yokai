import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(androidx.plugins.library)
    alias(kotlinx.plugins.multiplatform)
    alias(kotlinx.plugins.serialization)
}

kotlin {
    androidTarget()
    sourceSets {
        val commonMain by getting {
            dependencies {
                api(kotlinx.serialization.json)
                api(projects.injektKoin)
                api(libs.rxjava)
                api(libs.jsoup)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(projects.core)
                api(androidx.preference)

                // Workaround for https://youtrack.jetbrains.com/issue/KT-57605
                implementation(kotlinx.coroutines.android)
                implementation(project.dependencies.platform(kotlinx.coroutines.bom))
            }
        }
    }
}
android {
    namespace = "eu.kanade.tachiyomi.source"
    defaultConfig {
        consumerProguardFile("consumer-proguard.pro")
    }
}
tasks {
    withType<KotlinCompile> {
        compilerOptions.freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
        )
    }
}
