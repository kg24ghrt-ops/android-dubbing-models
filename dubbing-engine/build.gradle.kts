plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yourapp.dubbing.engine"
    compileSdk = 34

    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17 -O3 -fvisibility=hidden"
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Media3 for video processing
    implementation("androidx.media3:media3-transformer:1.3.0")
    implementation("androidx.media3:media3-effect:1.3.0")
    implementation("androidx.media3:media3-common:1.3.0")

    // ONNX Runtime
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.1")

    // Vosk STT
    implementation("com.alphacephei:vosk-android:0.3.47")

    // Downloading and extraction
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.apache.commons:commons-compress:1.26.0")

    // Sherpa-ONNX for offline TTS
    implementation("com.github.k2-fsa:sherpa-onnx:1.10.30")
    implementation("com.k2fsa.sherpa.onnx:sherpa-onnx:1.12.36")
    implementation(files("libs/sherpa-onnx-1.12.36.aar"))
    // Note: Pitch detection and SentencePiece are temporarily stubbed.
    // TarsosDSP and sentencepiece-android dependencies are removed.
}