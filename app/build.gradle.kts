import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.example"
  compileSdk = 35

  defaultConfig {
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
        }
    applicationId = "com.socialindia"
    minSdk = 26
    targetSdk = 35
    versionCode = 100
    versionName = "10.0.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
      enableV1Signing = true
      enableV2Signing = true
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
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
  testOptions { unitTests { isIncludeAndroidResources = true } }
  packaging {
    jniLibs {
      useLegacyPackaging = true
    }
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

googleServices {
  missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN
}

android {
  lint {
    abortOnError = false
  }
}

configurations.all {
    resolutionStrategy {
        force("com.github.ZEGOCLOUD:zego_uikit_plugin_adapter_android:3.0.6")
        eachDependency {
            if (requested.group == "com.github.ZEGOCLOUD" && requested.name == "zego_uikit_plugin_adapter_android") {
                useVersion("3.0.6")
                because("Force exact version to avoid JitPack 401 on dynamic range [3.0.0,)")
            }
        }
    }
}

// ZegoCloud Live Streaming enabled (dynamic Admin AppID/AppSign hot-swap). Calling & Audio Rooms remain 100% Native.
dependencies {
  implementation("androidx.appcompat:appcompat:1.6.1")
  implementation("com.google.android.material:material:1.10.0")
  implementation("com.github.ZEGOCLOUD:zego_uikit_plugin_adapter_android:3.0.6")
  implementation("com.github.ZEGOCLOUD:zego_uikit_prebuilt_live_streaming_android:3.4.1")
  // implementation("com.github.ZEGOCLOUD:zego_uikit_prebuilt_live_audio_room_android:2.6.1")
  // implementation(libs.cloudinary.android)
  // implementation("com.github.ZEGOCLOUD:zego_uikit_prebuilt_call_android:3.9.11")
  // implementation(libs.giphy.ui)
  
  
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform("com.google.firebase:firebase-bom:34.17.0"))
  implementation(libs.firebase.analytics)
  implementation(libs.firebase.auth)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.storage)
  implementation(libs.firebase.database)
  implementation(libs.firebase.messaging)
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.6.4")
  
  
  
  implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation("androidx.camera:camera-video:1.5.0")
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.kotlinx.serialization.json)
  implementation("com.google.android.gms:play-services-auth:21.0.0")
  implementation("androidx.credentials:credentials:1.3.0")
  implementation("androidx.biometric:biometric:1.1.0")

  implementation("androidx.credentials:credentials-play-services-auth:1.3.0")

  implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.coil.video)
  implementation(libs.coil.gif)
  // implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.zxing.core)
  implementation(libs.play.services.location)
  implementation(libs.retrofit)
  implementation(libs.converter.kotlinx.serialization)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.common)
  implementation(libs.androidx.media3.transformer)
  // implementation(libs.androidx.media3.effect)
  implementation(libs.androidx.media3.datasource.okhttp)
  // implementation(libs.ktor.client.okhttp)
  // implementation(libs.ktor.client.content.negotiation)
  // implementation(libs.ktor.serialization.kotlinx.json)
  // implementation("io.getstream:stream-webrtc-android:1.1.1")
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}

ksp {
    arg("room.generateKotlin", "false")
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
        freeCompilerArgs.add("-opt-in=androidx.compose.foundation.ExperimentalFoundationApi")
    }
}
