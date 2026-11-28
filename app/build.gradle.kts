import java.time.Duration
import java.util.Base64
plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
}

// Firebase configuration is environment-specific and is not committed.
// Apply the Google Services plugin only when app/google-services.json exists.
if (file("google-services.json").exists()) {
  apply(plugin = "com.google.gms.google-services")
  apply(plugin = "com.google.firebase.crashlytics")
}

android {
  namespace = "com.example"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.aistudio.crashintelligence.vyfrip"
    minSdk = 24
    targetSdk = 36
    versionCode = 6
    versionName = "1.0.6"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  val releaseKeystorePath = System.getenv("KEYSTORE_PATH")
  val releaseStorePassword = System.getenv("STORE_PASSWORD")
  val releaseKeyAlias = System.getenv("KEY_ALIAS")
  val releaseKeyPassword = System.getenv("KEY_PASSWORD")
  val releaseStoreType = System.getenv("KEYSTORE_TYPE")
  val releaseSigningConfigured = listOf(
    releaseKeystorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
  ).all { !it.isNullOrBlank() }

  val rootDebugKeystore = rootProject.file("debug.keystore")
  val rootDebugKeystoreBase64 = rootProject.file("debug.keystore.base64")
  if (!rootDebugKeystore.exists() && rootDebugKeystoreBase64.exists()) {
    val decodedBytes = Base64.getMimeDecoder().decode(rootDebugKeystoreBase64.readText().trim())
    rootDebugKeystore.writeBytes(decodedBytes)
  }

  signingConfigs {
    if (rootDebugKeystore.exists()) {
      create("debugConfig") {
        storeFile = rootDebugKeystore
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
    if (releaseSigningConfigured) {
      create("release") {
        storeFile = file(requireNotNull(releaseKeystorePath))
        storePassword = requireNotNull(releaseStorePassword)
        keyAlias = requireNotNull(releaseKeyAlias)
        keyPassword = requireNotNull(releaseKeyPassword)
        if (!releaseStoreType.isNullOrBlank()) storeType = releaseStoreType
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Release builds remain unsigned unless signing credentials are supplied through environment variables.
      if (releaseSigningConfigured) signingConfig = signingConfigs.getByName("release")
    }
    debug {
      if (rootDebugKeystore.exists()) {
        signingConfig = signingConfigs.getByName("debugConfig")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      all {
        it.maxHeapSize = "2048m"
        it.forkEvery = 1L
      }
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
  lint {
    abortOnError = false
    checkReleaseBuilds = false
    disable += "MissingTranslation"
  }
}

secrets {
    propertiesFileName = ".env"
    defaultPropertiesFileName = ".env.example"
    // OAuth client identifiers/secrets must never be injected into the APK BuildConfig.
    ignoreList.add("GITHUB_CLIENT_ID")
    ignoreList.add("GITHUB_CLIENT_SECRET")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.coil.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  implementation(libs.converter.moshi)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.generativeai)
  implementation(libs.javax.inject)
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.auth)
  implementation(libs.firebase.ai)
  implementation(libs.firebase.crashlytics)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.shizuku.api)
  implementation(libs.shizuku.provider)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

tasks.withType<Test> {
    // The full Robolectric suite routinely exceeds five minutes on cold CI runners.
    timeout.set(Duration.ofMinutes(15))
    // Robolectric fetches android-all directly (outside Gradle's dependency resolver).
    // Use the canonical Maven Central host to avoid repo1's intermittent 429 responses.
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
    systemProperty("robolectric.dependency.repo.id", "central")
    val localRobolectricDir = file("/home/appuser/.m2/repository/org/robolectric/android-all-instrumented/16-robolectric-13921718-i7")
    if (localRobolectricDir.exists()) {
        systemProperty("robolectric.offline", "true")
        systemProperty("robolectric.dependency.dir", localRobolectricDir.absolutePath)
        systemProperty("robolectric.default.sdk", "36")
    } else {
        systemProperty("robolectric.default.sdk", "35")
    }
    testLogging {
        events("passed", "failed", "skipped")
    }
}
