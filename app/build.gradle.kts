import java.util.Properties

plugins {
    alias(libs.plugins.ktfmt)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.aboutlibraries)
}

val appVersionCode = 15
val appVersionName = "0.5.1"
val androidCompileSdkVersion = 37
val androidMinSdkVersion = 24
val androidTargetSdkVersion = 37
val javaLanguageVersion = 21
val isPrBuild = providers.gradleProperty("IS_PR_BUILD").map(String::toBoolean).orElse(false).get()
val signingProfile =
    providers.gradleProperty("SIGNING_PROFILE").orElse(if (isPrBuild) "pr" else "release").get()
require(signingProfile in setOf("release", "pr")) { "SIGNING_PROFILE must be release or pr" }

ktfmt { kotlinLangStyle() }

aboutLibraries { collect { configPath = file("config") } }

android {
    namespace = "win.zuoye.dao"
    ndkVersion = libs.versions.ndk.get()
    compileSdk { version = release(androidCompileSdkVersion) }

    defaultConfig {
        applicationId =
            providers
                .gradleProperty("DAO_PACKAGE_NAME")
                .orElse(if (isPrBuild) "win.zuoye.dao.pr" else "win.zuoye.dao")
                .get()
        minSdk = androidMinSdkVersion
        targetSdk = androidTargetSdkVersion
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("boolean", "IS_PR_BUILD", isPrBuild.toString())
        externalNativeBuild {
            cmake {
                arguments(
                    "-DANDROID_STL=c++_static",
                    "-DDAO_ZXING_VERSION=${libs.versions.zxingCpp.get()}",
                )
                targets("dao_qr")
            }
        }
    }

    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    signingConfigs {
        val keystoreProperties =
            Properties().apply {
                val keystorePropertiesFile = rootProject.file("$signingProfile.keystore.properties")
                if (keystorePropertiesFile.exists()) {
                    keystorePropertiesFile.inputStream().use { load(it) }
                }
            }
        val signingProperties =
            mapOf(
                    "KEYSTORE_FILE" to keystoreProperties.getProperty("storeFile"),
                    "KEYSTORE_PASSWORD" to keystoreProperties.getProperty("storePassword"),
                    "KEY_ALIAS" to keystoreProperties.getProperty("keyAlias"),
                    "KEY_PASSWORD" to keystoreProperties.getProperty("keyPassword"),
                )
                .mapValues { (name, localValue) ->
                    providers
                        .gradleProperty(name)
                        .orElse(providers.environmentVariable(name))
                        .orNull ?: localValue
                }
        val signingStoreFile = signingProperties["KEYSTORE_FILE"]?.let { rootProject.file(it) }
        if (signingStoreFile?.isFile == true) {
            create(signingProfile) {
                storeFile = signingStoreFile
                storePassword = signingProperties["KEYSTORE_PASSWORD"]
                keyAlias = signingProperties["KEY_ALIAS"]
                keyPassword = signingProperties["KEY_PASSWORD"]
            }
        }
    }

    buildTypes {
        val selectedSigningConfig =
            signingConfigs.findByName(signingProfile) ?: signingConfigs.getByName("debug")
        configureEach { signingConfig = selectedSigningConfig }
        debug {
            externalNativeBuild {
                cmake {
                    arguments += listOf("-DCMAKE_CXX_FLAGS_DEBUG=-Og", "-DCMAKE_C_FLAGS_DEBUG=-Og")
                }
            }
        }
        release {
            vcsInfo.include = false
            optimization { enable = true }
        }
    }
    compileOptions {
        val androidJavaVersion = JavaVersion.toVersion(javaLanguageVersion)
        sourceCompatibility = androidJavaVersion
        targetCompatibility = androidJavaVersion
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources { generateLocaleConfig = true }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.androidResources.localeFilters.addAll(listOf("zh", "en"))
    }
    val apkBaseName = if (isPrBuild) "Dao-PR" else "Dao"
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set(
                output.versionName.map { versionName -> "$apkBaseName-$versionName.apk" }
            )
        }
    }
}

java { toolchain { languageVersion = JavaLanguageVersion.of(javaLanguageVersion) } }

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.aboutlibraries.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.compose)
    implementation(libs.androidx.navigationevent.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.nav)
    implementation(libs.tyme)
    implementation(libs.nayuki.qrcodegen)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
