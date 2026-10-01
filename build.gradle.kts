import com.ncorti.ktfmt.gradle.tasks.KtfmtBaseTask

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.ktfmt)
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.aboutlibraries) apply false
}

ktfmt { kotlinLangStyle() }

// ktfmt 的 Kotlin/IntelliJ 依赖仍使用 Unsafe，在 JDK 24+ 上按工具进程配置兼容参数。
if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_24)) {
    allprojects {
        tasks.withType<KtfmtBaseTask>().configureEach {
            processIsolationJvmArgs.add("--sun-misc-unsafe-memory-access=allow")
        }
    }
}
