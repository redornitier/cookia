plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.redornitier.cookia"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.redornitier.cookia"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        //noinspection ChromeOsAbiSupport
        ndk { abiFilters += listOf("arm64-v8a") } // Pixel 8 es arm64

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        // Evita compresión de extensiones del modelo (mejor carga en runtime)
        noCompress += setOf(
            "bin", "params", "ndarray", "json", "model", "vocab", "tvm", "gguf", "txt"
        )
        // ↑ usa extensiones reales de tus ficheros. No pongas el punto.
    }
    packaging {
        resources {
            // si tu app ya trae libc++_shared de otra lib, evita conflicto
            jniLibs {
                pickFirsts += listOf("**/libc++_shared.so")
            }
            // no comprimir pesos grandes si los metes en assets (opcional)
            // resources.excludes += listOf(/* lo usamos solo si hace falta */)

        }
    }
}

dependencies {

    implementation(project(":mlc4j"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.kotlinx.coroutines.android)

}
