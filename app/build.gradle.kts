plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.shd.codestudio"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.shd.codestudio"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    androidResources {
        noCompress += listOf("tar", "gz", "tar.gz", "xz", "zip", "bin")
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += listOf(
                "META-INF/*.SF",
                "META-INF/*.DSA",
                "META-INF/*.RSA",
                "META-INF/INDEX.LIST",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/DEPENDENCIES",
                "META-INF/versions/**",
                "module-info.class",
                "about.html",
                "plugin.properties"
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Core & Lifecycle (Verified Latest Stable)
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.12.0-alpha05")
    implementation("androidx.documentfile:documentfile:1.1.0")

    // Activity & Compose BOM
    implementation("androidx.activity:activity-compose:1.14.0-alpha03")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))

    // UI, Foundation & Material
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-extended")

    // Apache Commons + XZ
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("org.tukaani:xz:1.12")

    // In-Process Native Build Engine (Modern ECJ + nb-javac Android + Google D8/R8 + APK Signer)
    implementation("org.eclipse.jdt:ecj:3.46.100")
    implementation("io.github.itsaky:nb-javac-android:17.0.0.3")
    implementation("com.android.tools:r8:9.6.0-alpha15")
    implementation("com.android.tools.build:apksig:9.6.0-alpha04")

    // Test
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.4.0-alpha04")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.8.0-alpha04")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}