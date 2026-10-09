import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.scan2anki"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.scan2anki"
        minSdk = 26
        targetSdk = 36
        // CI passes -PversionCode/-PversionName derived from the release tag.
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = findProperty("versionName") as String? ?: "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing is only configured when the keystore is provided through the
    // environment (as the GitHub release workflow does); otherwise release builds are unsigned.
    val releaseKeystore = System.getenv("SCAN2ANKI_KEYSTORE_FILE")?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("SCAN2ANKI_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SCAN2ANKI_KEY_ALIAS")
                keyPassword = System.getenv("SCAN2ANKI_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.chinese)
    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coroutines.android)
    implementation(libs.timber)
    implementation(libs.androidx.exifinterface)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.androidx.room.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    kspTest(libs.hilt.android.compiler)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}

// AGP does not merge the "test" source set's own `assets` into the directory Robolectric
// reads for JVM unit tests (it only packages the main/debug variant's merged assets there),
// so declaring `assets.srcDirs("$projectDir/schemas")` on the test source set alone is not
// enough for MigrationTestHelper to find the exported Room schema JSON files.
//
// A private, test-only copy of the merged assets was tried first (never touching AGP's own
// output at all), repointing Robolectric via the `android_merged_assets` line in the
// `test_config.properties` file AGP's `generateDebugUnitTestConfig` task generates. That
// does not work: `MigrationTestHelper`'s `context.assets` is actually backed by the packaged
// `apk-for-local-test.ap_` archive (AGP's `PackageForHostTest` task, aka
// `packageDebugUnitTestForUnitTest`), not by that properties-file path — and
// `PackageForHostTest.mergedAssetsDirectory` is wired with `setDisallowChanges` straight to
// the same shared `ASSETS` singleartifact (i.e. `mergeDebugAssets`'s output) that production
// packaging also consumes, with no supported override (confirmed by reading AGP 8.13.2's own
// `PackageForUnitTest.kt` source). So there is no way to hand Robolectric a private set of
// assets without those assets passing through that same shared directory at some point.
//
// Given that, stage the schema JSON into the shared `mergeDebugAssets` output ourselves
// ahead of every JVM test task, then remove it again once those tests finish
// (`finalizedBy`, so it runs whether the test task passes or fails — though not if the build
// process itself is killed outright, e.g. Ctrl-C, OOM, or the IDE stopping it; in that case
// the staged schema JSONs are simply cleaned up by the next test run's own copy/cleanup pair
// instead) so the directory is back to exactly what
// `mergeDebugAssets` itself produced before any later, unrelated build (e.g. a plain
// `./gradlew assembleDebug` run days later) can package it into a real APK. For the case
// where a *single* invocation runs both test and production-packaging tasks (e.g.
// `./gradlew testDebugUnitTest assembleDebug`), the known real consumers of that shared
// directory for an `application` module — `compressDebugAssets` and `packageDebug` — are
// explicitly ordered to run after the cleanup; this is not a fully general fix (a future AGP
// version adding another consumer task, e.g. for App Bundles via `bundleDebug`, would need
// the same treatment), but it is verified to work for every path this project's build
// actually exercises (`testDebugUnitTest`, `assembleDebug`, run either separately or
// together).
//
// CAUTION: `mergeDebugAssetsOutputDir` below is an AGP-internal intermediate path, not a
// public API — if a future AGP upgrade relocates it, this staging/cleanup pair will need to
// be re-pointed. Breakage is loud, not silent: the migration test would fail with the same
// FileNotFoundException it had before this workaround existed.
val mergeDebugAssetsOutputDir = layout.buildDirectory.dir("intermediates/assets/debug/mergeDebugAssets")

val copyRoomSchemasForUnitTests = tasks.register<Copy>("copyRoomSchemasForUnitTests") {
    dependsOn("mergeDebugAssets")
    from("$projectDir/schemas")
    into(mergeDebugAssetsOutputDir)
}

val cleanupRoomSchemasForUnitTests = tasks.register<Delete>("cleanupRoomSchemasForUnitTests") {
    // Deletes only the subdirectory our copy staged (named after the Database class's
    // fully-qualified name, per Room's schema export convention) — never touches anything
    // else `mergeDebugAssets` legitimately produced.
    delete(mergeDebugAssetsOutputDir.map { it.dir("com.scan2anki.data.AppDatabase") })
}

tasks.matching { it.name == "packageDebugUnitTestForUnitTest" }.configureEach {
    dependsOn(copyRoomSchemasForUnitTests)
}

tasks.withType<Test>().configureEach {
    dependsOn(copyRoomSchemasForUnitTests)
    finalizedBy(cleanupRoomSchemasForUnitTests)
}

tasks.matching { it.name == "compressDebugAssets" || it.name == "packageDebug" }.configureEach {
    mustRunAfter(cleanupRoomSchemasForUnitTests)
}
