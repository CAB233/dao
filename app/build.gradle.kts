import java.util.Properties

plugins {
    alias(libs.plugins.ktfmt)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.aboutlibraries)
}

val appNamespace = "win.zuoye.dao"
val appVersionCode = 14
val appVersionName = "0.5.0"
val androidCompileSdkVersion = 37
val androidMinSdkVersion = 24
val androidTargetSdkVersion = 37
val javaLanguageVersion = 21
val androidJavaVersion = JavaVersion.toVersion(javaLanguageVersion)
val targetAbis = arrayOf("arm64-v8a")
val releaseLocales = listOf("zh", "en")
val isPrBuild = providers.gradleProperty("IS_PR_BUILD").map(String::toBoolean).orElse(false).get()
val defaultAppPackageName = if (isPrBuild) "$appNamespace.pr" else appNamespace
val appPackageName =
    providers.gradleProperty("DAO_PACKAGE_NAME").orElse(defaultAppPackageName).get()
val apkBaseName = if (isPrBuild) "Dao-PR" else "Dao"
val signingProfile =
    providers.gradleProperty("SIGNING_PROFILE").orElse(if (isPrBuild) "pr" else "release").get()

require(signingProfile in setOf("release", "pr")) { "SIGNING_PROFILE must be release or pr" }

val signingStoreFileProperty = "KEYSTORE_FILE"
val signingStorePasswordProperty = "KEYSTORE_PASSWORD"
val signingKeyAliasProperty = "KEY_ALIAS"
val signingKeyPasswordProperty = "KEY_PASSWORD"
val keystorePropertiesFile = rootProject.file("$signingProfile.keystore.properties")
val keystoreProperties =
    Properties().apply {
        if (keystorePropertiesFile.exists()) {
            keystorePropertiesFile.inputStream().use { load(it) }
        }
    }
val localSigningProperties =
    mapOf(
        signingStoreFileProperty to keystoreProperties.getProperty("storeFile"),
        signingStorePasswordProperty to keystoreProperties.getProperty("storePassword"),
        signingKeyAliasProperty to keystoreProperties.getProperty("keyAlias"),
        signingKeyPasswordProperty to keystoreProperties.getProperty("keyPassword"),
    )

val signingProperties = localSigningProperties.mapValues { (name, localValue) ->
    providers.gradleProperty(name).orElse(providers.environmentVariable(name)).orNull ?: localValue
}
val signingStoreFile = signingProperties[signingStoreFileProperty]?.let { rootProject.file(it) }
val signingStorePassword = signingProperties[signingStorePasswordProperty]
val signingKeyAlias = signingProperties[signingKeyAliasProperty]
val signingKeyPassword = signingProperties[signingKeyPasswordProperty]

ktfmt { kotlinLangStyle() }

android {
    namespace = appNamespace
    compileSdk { version = release(androidCompileSdkVersion) }

    defaultConfig {
        applicationId = appPackageName
        minSdk = androidMinSdkVersion
        targetSdk = androidTargetSdkVersion
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("boolean", "IS_PR_BUILD", isPrBuild.toString())
    }

    splits {
        abi {
            isEnable = true
            isUniversalApk = false
            reset()
            include(*targetAbis)
        }
    }

    signingConfigs {
        if (signingStoreFile?.isFile == true) {
            create(signingProfile) {
                storeFile = signingStoreFile
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        val selectedSigningConfig =
            signingConfigs.findByName(signingProfile) ?: signingConfigs.getByName("debug")
        configureEach { signingConfig = selectedSigningConfig }
        release {
            vcsInfo.include = false
            optimization { enable = true }
        }
    }
    compileOptions {
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
        variant.androidResources.localeFilters.addAll(releaseLocales)
    }
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set(
                output.versionName.map { versionName ->
                    "$apkBaseName-$versionName-${variant.buildType}.apk"
                }
            )
        }
    }
}

java { toolchain { languageVersion = JavaLanguageVersion.of(javaLanguageVersion) } }

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.aboutlibraries.core)
    implementation(libs.androidx.activity.compose)
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
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
