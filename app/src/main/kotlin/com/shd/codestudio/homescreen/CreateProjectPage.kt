package com.shd.codestudio.homescreen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shd.codestudio.DarkBackground
import com.shd.codestudio.NeonLime
import com.shd.codestudio.SurfaceDark
import com.shd.codestudio.SurfaceLight
import com.shd.codestudio.TextSecondary
import java.io.File
import java.text.Normalizer

// ==================== স্ক্রোলবার মডিফায়ার ====================
private fun Modifier.pageVerticalScrollbar(
    state: ScrollState,
    width: Dp = 4.dp,
    thumbColor: Color = Color(0xFFD4FF33).copy(alpha = 0.75f),
    trackColor: Color = Color.White.copy(alpha = 0.06f)
): Modifier = drawWithContent {
    drawContent()
    if (state.maxValue > 0) {
        val viewHeight = size.height
        val contentHeight = viewHeight + state.maxValue
        val scrollbarHeight = ((viewHeight / contentHeight) * viewHeight).coerceIn(36.dp.toPx(), viewHeight * 0.85f)
        val scrollbarOffsetY = (state.value.toFloat() / state.maxValue.toFloat()) * (viewHeight - scrollbarHeight)
        val xOffset = size.width - width.toPx() - 2.dp.toPx()

        drawRoundRect(
            color = trackColor,
            topLeft = Offset(xOffset, 6.dp.toPx()),
            size = Size(width.toPx(), viewHeight - 12.dp.toPx()),
            cornerRadius = CornerRadius(width.toPx() / 2f, width.toPx() / 2f)
        )
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(xOffset, scrollbarOffsetY.coerceIn(6.dp.toPx(), viewHeight - scrollbarHeight - 6.dp.toPx())),
            size = Size(width.toPx(), scrollbarHeight),
            cornerRadius = CornerRadius(width.toPx() / 2f, width.toPx() / 2f)
        )
    }
}

// ==================== ট্রান্সলিটারেটর ও প্যাকেজ জেনারেটর ====================
fun transliterateToAsciiIdentifier(input: String): String {
    val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
    val ascii = normalized.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    val cleaned = ascii.lowercase()
        .replace(Regex("[^a-z0-9_]"), "_")
        .replace(Regex("_+"), "_")
        .trim('_')

    if (cleaned.isEmpty()) return "myproject"
    return if (cleaned[0].isDigit()) "app_$cleaned" else cleaned
}

fun generateSmartPackageSuggestions(projectName: String): List<String> {
    val base = transliterateToAsciiIdentifier(projectName)
    return listOf(
        "com.example.$base",
        "com.app.$base",
        "org.$base.app",
        "net.shd.$base"
    )
}

fun createProjectByLanguage(
    context: android.content.Context,
    projectName: String,
    packageName: String,
    language: String,
    minSdk: Int,
    targetSdk: Int,
    toolchainPreset: String = "AGP9"
) {
    try {
        val isAgp9Plus = toolchainPreset != "AGP8"
        val agpVersion = if (isAgp9Plus) "9.3.1" else "8.13.2"
        val gradleVersion = if (isAgp9Plus) "9.5.0" else "8.13"
        val kotlinVersion = if (isAgp9Plus) "2.4.21" else "2.1.21"
        val composeBomVersion = if (isAgp9Plus) "2026.09.00" else "2025.05.00"
        val coreKtxVersion = if (isAgp9Plus) "1.17.0" else "1.16.0"
        val activityComposeVersion = if (isAgp9Plus) "1.11.0" else "1.10.1"

        val cleanProjectName = projectName.trim()
        val projectDir = getProjectDirectory(context, cleanProjectName)
        if (projectDir.exists()) {
            projectDir.deleteRecursively()
        }
        projectDir.mkdirs()

        val packagePath = packageName.replace(".", "/")
        val asciiId = transliterateToAsciiIdentifier(cleanProjectName)
        val themeName = "Theme.${asciiId.replaceFirstChar { it.uppercase() }}"
        val libName = "native_$asciiId"

        File(projectDir, ".gitignore").writeText(
            """
            *.iml
            .gradle
            /local.properties
            /.idea
            .DS_Store
            /build
            /captures
            .externalNativeBuild
            .cxx
            local.properties
            """.trimIndent() + "\n"
        )

        // সব IDE (Android Studio, CodeAssist, Gradle CLI, CodeStudio) কম্প্যাটিবল properties
        File(projectDir, "gradle.properties").writeText(
            """
            org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
            android.useAndroidX=true
            kotlin.code.style=official
            android.nonTransitiveRClass=true
            """.trimIndent() + "\n"
        )

        File(projectDir, "settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    google()
                    mavenCentral()
                    maven { url = uri("https://jitpack.io") }
                }
            }

            rootProject.name = "$cleanProjectName"
            include(":app")
            """.trimIndent() + "\n"
        )

        // GitHub Actions অটো-বিল্ড ওয়ার্কফ্লো (সব প্রজেক্টেই লেটেস্ট Node24 কম্প্যাটিবল Actions সহ রেডি থাকবে)
        val workflowDir = File(projectDir, ".github/workflows").apply { mkdirs() }
        File(workflowDir, "android.yml").writeText(
            """
            name: Build Android APK

            on:
              push:
                branches: [ main, master ]
              workflow_dispatch:

            jobs:
              build:
                runs-on: ubuntu-latest
                steps:
                  - name: Checkout Repository
                    uses: actions/checkout@v6

                  - name: Set up JDK 17
                    uses: actions/setup-java@v5
                    with:
                      java-version: '17'
                      distribution: 'temurin'

                  - name: Setup Gradle $gradleVersion
                    uses: gradle/actions/setup-gradle@v6
                    with:
                      gradle-version: '$gradleVersion'

                  - name: Build Debug APK
                    run: |
                      if [ -f "./gradlew" ] && [ -f "./gradle/wrapper/gradle-wrapper.jar" ]; then
                        chmod +x gradlew
                        ./gradlew assembleDebug --no-daemon
                      else
                        gradle assembleDebug --no-daemon
                      fi

                  - name: Upload Debug APK
                    uses: actions/upload-artifact@v7
                    with:
                      name: app-debug-apk
                      path: app/build/outputs/apk/debug/*.apk
                      if-no-files-found: warn
            """.trimIndent() + "\n"
        )

        val gradleDir = File(projectDir, "gradle")
        val wrapperDir = File(gradleDir, "wrapper")
        wrapperDir.mkdirs()

        val pluginsTomlBlock = if (isAgp9Plus) {
            """
            [plugins]
            android-application = { id = "com.android.application", version.ref = "agp" }
            kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
            """.trimIndent()
        } else {
            """
            [plugins]
            android-application = { id = "com.android.application", version.ref = "agp" }
            kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
            kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
            """.trimIndent()
        }

        File(gradleDir, "libs.versions.toml").writeText(
            """
            [versions]
            agp = "$agpVersion"
            kotlin = "$kotlinVersion"
            coreKtx = "$coreKtxVersion"
            lifecycleRuntimeKtx = "2.9.0"
            junit = "4.13.2"
            junitVersion = "1.2.1"
            espressoCore = "3.6.1"
            appcompat = "1.7.0"
            material = "1.12.0"
            activityCompose = "$activityComposeVersion"
            composeBom = "$composeBomVersion"

            [libraries]
            androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
            androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
            junit = { group = "junit", name = "junit", version.ref = "junit" }
            androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
            androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
            androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
            material = { group = "com.google.android.material", name = "material", version.ref = "material" }
            androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
            androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
            androidx-ui = { group = "androidx.compose.ui", name = "ui" }
            androidx-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
            androidx-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
            androidx-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
            androidx-material3 = { group = "androidx.compose.material3", name = "material3" }

            $pluginsTomlBlock
            """.trimIndent() + "\n"
        )

        File(wrapperDir, "gradle-wrapper.properties").writeText(
            """
            distributionBase=GRADLE_USER_HOME
            distributionPath=wrapper/dists
            distributionUrl=https\://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip
            networkTimeout=10000
            validateDistributionUrl=true
            zipStoreBase=GRADLE_USER_HOME
            zipStorePath=wrapper/dists
            """.trimIndent() + "\n"
        )

        copyAssetToFile(context, "gradle-wrapper.jar", File(wrapperDir, "gradle-wrapper.jar"))
        val gradlewDest = File(projectDir, "gradlew")
        copyAssetToFile(context, "gradlew", gradlewDest)
        if (gradlewDest.exists()) gradlewDest.setExecutable(true, false)
        copyAssetToFile(context, "gradlew.bat", File(projectDir, "gradlew.bat"))

        if (language == "Java") {
            File(projectDir, "build.gradle.kts").writeText(
                """
                plugins {
                    alias(libs.plugins.android.application) apply false
                }
                """.trimIndent() + "\n"
            )
        } else if (isAgp9Plus) {
            File(projectDir, "build.gradle.kts").writeText(
                """
                plugins {
                    alias(libs.plugins.android.application) apply false
                    alias(libs.plugins.kotlin.compose) apply false
                }
                """.trimIndent() + "\n"
            )
        } else {
            File(projectDir, "build.gradle.kts").writeText(
                """
                plugins {
                    alias(libs.plugins.android.application) apply false
                    alias(libs.plugins.kotlin.android) apply false
                    alias(libs.plugins.kotlin.compose) apply false
                }
                """.trimIndent() + "\n"
            )
        }

        val appDir = File(projectDir, "app")
        val srcMainDir = File(appDir, "src/main")
        val resDir = File(srcMainDir, "res")
        val resDrawableDir = File(resDir, "drawable").apply { mkdirs() }
        val resMipmapDir = File(resDir, "mipmap-anydpi-v26").apply { mkdirs() }
        val resValuesDir = File(resDir, "values").apply { mkdirs() }
        val resXmlDir = File(resDir, "xml").apply { mkdirs() }

        File(appDir, "proguard-rules.pro").writeText("# Add project specific ProGuard rules here.\n")

        File(srcMainDir, "AndroidManifest.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:tools="http://schemas.android.com/tools">

                <application
                    android:allowBackup="true"
                    android:dataExtractionRules="@xml/data_extraction_rules"
                    android:fullBackupContent="@xml/backup_rules"
                    android:icon="@mipmap/ic_launcher"
                    android:label="@string/app_name"
                    android:roundIcon="@mipmap/ic_launcher_round"
                    android:supportsRtl="true"
                    android:theme="@style/$themeName"
                    tools:targetApi="31">
                    <activity
                        android:name=".MainActivity"
                        android:exported="true"
                        android:theme="@style/$themeName">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>

            </manifest>
            """.trimIndent() + "\n"
        )

        File(resDrawableDir, "ic_launcher_background.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:width="108dp"
                android:height="108dp"
                android:viewportWidth="108"
                android:viewportHeight="108">
                <path
                    android:fillColor="#3DDC84"
                    android:pathData="M0,0h108v108h-108z" />
            </vector>
            """.trimIndent() + "\n"
        )

        File(resDrawableDir, "ic_launcher_foreground.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <vector xmlns:android="http://schemas.android.com/apk/res/android"
                android:width="108dp"
                android:height="108dp"
                android:viewportWidth="108"
                android:viewportHeight="108">
                <path
                    android:fillColor="#FFFFFF"
                    android:pathData="M34,34h40v40h-40z" />
            </vector>
            """.trimIndent() + "\n"
        )

        File(resMipmapDir, "ic_launcher.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                <background android:drawable="@drawable/ic_launcher_background" />
                <foreground android:drawable="@drawable/ic_launcher_foreground" />
            </adaptive-icon>
            """.trimIndent() + "\n"
        )

        File(resMipmapDir, "ic_launcher_round.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                <background android:drawable="@drawable/ic_launcher_background" />
                <foreground android:drawable="@drawable/ic_launcher_foreground" />
            </adaptive-icon>
            """.trimIndent() + "\n"
        )

        File(resValuesDir, "colors.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <color name="purple_200">#FFBB86FC</color>
                <color name="purple_500">#FF6200EE</color>
                <color name="purple_700">#FF3700B3</color>
                <color name="teal_200">#FF03DAC5</color>
                <color name="black">#FF000000</color>
                <color name="white">#FFFFFFFF</color>
            </resources>
            """.trimIndent() + "\n"
        )

        File(resValuesDir, "strings.xml").writeText(
            """
            <resources>
                <string name="app_name">$cleanProjectName</string>
            </resources>
            """.trimIndent() + "\n"
        )

        File(resXmlDir, "backup_rules.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <full-backup-content>
            </full-backup-content>
            """.trimIndent() + "\n"
        )

        File(resXmlDir, "data_extraction_rules.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <data-extraction-rules>
                <cloud-backup>
                </cloud-backup>
            </data-extraction-rules>
            """.trimIndent() + "\n"
        )

        val appPluginsBlock = if (isAgp9Plus) {
            """
            plugins {
                alias(libs.plugins.android.application)
                alias(libs.plugins.kotlin.compose)
            }
            """.trimIndent()
        } else {
            """
            plugins {
                alias(libs.plugins.android.application)
                alias(libs.plugins.kotlin.android)
                alias(libs.plugins.kotlin.compose)
            }
            """.trimIndent()
        }

        val kotlinJvmBlock = if (isAgp9Plus) "" else {
            """

                kotlinOptions {
                    jvmTarget = "17"
                }
            """.trimIndent()
        }

        when (language) {
            "Java" -> {
                val javaDir = File(srcMainDir, "java/$packagePath").apply { mkdirs() }
                val resLayoutDir = File(resDir, "layout").apply { mkdirs() }
                val resValuesNightDir = File(resDir, "values-night").apply { mkdirs() }
                File(appDir, "src/test/java/$packagePath").mkdirs()
                File(appDir, "src/androidTest/java/$packagePath").mkdirs()

                File(resValuesDir, "themes.xml").writeText(
                    """
                    <resources xmlns:tools="http://schemas.android.com/tools">
                        <style name="$themeName" parent="Theme.Material3.DayNight.NoActionBar">
                            <item name="colorPrimary">@color/purple_500</item>
                        </style>
                    </resources>
                    """.trimIndent() + "\n"
                )

                File(resValuesNightDir, "themes.xml").writeText(
                    """
                    <resources xmlns:tools="http://schemas.android.com/tools">
                        <style name="$themeName" parent="Theme.Material3.DayNight.NoActionBar">
                            <item name="colorPrimary">@color/purple_200</item>
                        </style>
                    </resources>
                    """.trimIndent() + "\n"
                )

                File(javaDir, "MainActivity.java").writeText(
                    """
                    package $packageName;

                    import android.os.Bundle;
                    import androidx.appcompat.app.AppCompatActivity;

                    public class MainActivity extends AppCompatActivity {
                        @Override
                        protected void onCreate(Bundle savedInstanceState) {
                            super.onCreate(savedInstanceState);
                            setContentView(R.layout.activity_main);
                        }
                    }
                    """.trimIndent() + "\n"
                )

                File(resLayoutDir, "activity_main.xml").writeText(
                    """
                    <?xml version="1.0" encoding="utf-8"?>
                    <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:gravity="center"
                        android:orientation="vertical">

                        <TextView
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Hello Java from $cleanProjectName!"
                            android:textSize="18sp" />

                    </LinearLayout>
                    """.trimIndent() + "\n"
                )

                File(appDir, "build.gradle.kts").writeText(
                    """
                    plugins {
                        alias(libs.plugins.android.application)
                    }

                    android {
                        namespace = "$packageName"
                        compileSdk = $targetSdk

                        defaultConfig {
                            applicationId = "$packageName"
                            minSdk = $minSdk
                            targetSdk = $targetSdk
                            versionCode = 1
                            versionName = "1.0"
                            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

                        buildFeatures {
                            buildConfig = true
                        }

                        compileOptions {
                            sourceCompatibility = JavaVersion.VERSION_17
                            targetCompatibility = JavaVersion.VERSION_17
                        }
                    }

                    dependencies {
                        implementation(libs.androidx.appcompat)
                        implementation(libs.material)
                        testImplementation(libs.junit)
                        androidTestImplementation(libs.androidx.junit)
                        androidTestImplementation(libs.androidx.espresso.core)
                    }
                    """.trimIndent() + "\n"
                )
            }

            "C++" -> {
                val kotlinDir = File(srcMainDir, "kotlin/$packagePath").apply { mkdirs() }
                val uiThemeDir = File(kotlinDir, "ui/theme").apply { mkdirs() }
                val cppDir = File(srcMainDir, "cpp").apply { mkdirs() }
                val testKotlinDir = File(appDir, "src/test/kotlin/$packagePath").apply { mkdirs() }
                val androidTestKotlinDir = File(appDir, "src/androidTest/kotlin/$packagePath").apply { mkdirs() }

                File(resValuesDir, "themes.xml").writeText(
                    """
                    <?xml version="1.0" encoding="utf-8"?>
                    <resources>
                        <style name="$themeName" parent="android:Theme.Material.Light.NoActionBar" />
                    </resources>
                    """.trimIndent() + "\n"
                )

                File(cppDir, "native-lib.cpp").writeText(
                    """
                    #include <jni.h>
                    #include <string>

                    extern "C" JNIEXPORT jstring JNICALL
                    Java_${packageName.replace(".", "_")}_MainActivity_stringFromJNI(
                            JNIEnv* env,
                            jobject /* this */) {
                        std::string hello = "Hello from C++ Native ($cleanProjectName)!";
                        return env->NewStringUTF(hello.c_str());
                    }
                    """.trimIndent() + "\n"
                )

                File(cppDir, "CMakeLists.txt").writeText(
                    """
                    cmake_minimum_required(VERSION 3.22.1)
                    project("$cleanProjectName")
                    add_library($libName SHARED native-lib.cpp)
                    find_library(log-lib log)
                    target_link_libraries($libName android ${'$'}{log-lib})
                    """.trimIndent() + "\n"
                )

                writeComposeThemeFiles(uiThemeDir, packageName, asciiId.replaceFirstChar { it.uppercase() })

                File(kotlinDir, "MainActivity.kt").writeText(
                    """
                    package $packageName

                    import android.os.Bundle
                    import androidx.activity.ComponentActivity
                    import androidx.activity.compose.setContent
                    import androidx.compose.foundation.layout.Box
                    import androidx.compose.foundation.layout.fillMaxSize
                    import androidx.compose.material3.Text
                    import androidx.compose.ui.Alignment
                    import androidx.compose.ui.Modifier
                    import $packageName.ui.theme.${asciiId.replaceFirstChar { it.uppercase() }}Theme

                    class MainActivity : ComponentActivity() {
                        init {
                            System.loadLibrary("$libName")
                        }

                        external fun stringFromJNI(): String

                        override fun onCreate(savedInstanceState: Bundle?) {
                            super.onCreate(savedInstanceState)
                            setContent {
                                ${asciiId.replaceFirstChar { it.uppercase() }}Theme {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(text = stringFromJNI())
                                    }
                                }
                            }
                        }
                    }
                    """.trimIndent() + "\n"
                )

                writeKotlinTestFiles(testKotlinDir, androidTestKotlinDir, packageName)

                File(appDir, "build.gradle.kts").writeText(
                    """
                    $appPluginsBlock

                    android {
                        namespace = "$packageName"
                        compileSdk = $targetSdk

                        defaultConfig {
                            applicationId = "$packageName"
                            minSdk = $minSdk
                            targetSdk = $targetSdk
                            versionCode = 1
                            versionName = "1.0"
                            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

                            externalNativeBuild {
                                cmake {
                                    cppFlags += ""
                                }
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
                        }$kotlinJvmBlock

                        buildFeatures {
                            compose = true
                            buildConfig = true
                        }

                        externalNativeBuild {
                            cmake {
                                path = file("src/main/cpp/CMakeLists.txt")
                                version = "3.22.1"
                            }
                        }
                    }

                    dependencies {
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
                        debugImplementation(libs.androidx.ui.tooling)
                    }
                    """.trimIndent() + "\n"
                )
            }

            else -> {
                val kotlinDir = File(srcMainDir, "kotlin/$packagePath").apply { mkdirs() }
                val uiThemeDir = File(kotlinDir, "ui/theme").apply { mkdirs() }
                val testKotlinDir = File(appDir, "src/test/kotlin/$packagePath").apply { mkdirs() }
                val androidTestKotlinDir = File(appDir, "src/androidTest/kotlin/$packagePath").apply { mkdirs() }

                File(resValuesDir, "themes.xml").writeText(
                    """
                    <?xml version="1.0" encoding="utf-8"?>
                    <resources>
                        <style name="$themeName" parent="android:Theme.Material.Light.NoActionBar" />
                    </resources>
                    """.trimIndent() + "\n"
                )

                writeComposeThemeFiles(uiThemeDir, packageName, asciiId.replaceFirstChar { it.uppercase() })

                File(kotlinDir, "MainActivity.kt").writeText(
                    """
                    package $packageName

                    import android.os.Bundle
                    import androidx.activity.ComponentActivity
                    import androidx.activity.compose.setContent
                    import androidx.compose.foundation.layout.Box
                    import androidx.compose.foundation.layout.fillMaxSize
                    import androidx.compose.foundation.layout.padding
                    import androidx.compose.material3.Scaffold
                    import androidx.compose.material3.Text
                    import androidx.compose.runtime.Composable
                    import androidx.compose.ui.Alignment
                    import androidx.compose.ui.Modifier
                    import androidx.compose.ui.tooling.preview.Preview
                    import $packageName.ui.theme.${asciiId.replaceFirstChar { it.uppercase() }}Theme

                    class MainActivity : ComponentActivity() {
                        override fun onCreate(savedInstanceState: Bundle?) {
                            super.onCreate(savedInstanceState)
                            setContent {
                                ${asciiId.replaceFirstChar { it.uppercase() }}Theme {
                                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(innerPadding),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Greeting(name = "$cleanProjectName")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    @Composable
                    fun Greeting(name: String, modifier: Modifier = Modifier) {
                        Text(
                            text = "Hello Kotlin Compose from ${'$'}name!",
                            modifier = modifier
                        )
                    }

                    @Preview(showBackground = true)
                    @Composable
                    fun GreetingPreview() {
                        ${asciiId.replaceFirstChar { it.uppercase() }}Theme {
                            Greeting("Android")
                        }
                    }
                    """.trimIndent() + "\n"
                )

                writeKotlinTestFiles(testKotlinDir, androidTestKotlinDir, packageName)

                File(appDir, "build.gradle.kts").writeText(
                    """
                    $appPluginsBlock

                    android {
                        namespace = "$packageName"
                        compileSdk = $targetSdk

                        defaultConfig {
                            applicationId = "$packageName"
                            minSdk = $minSdk
                            targetSdk = $targetSdk
                            versionCode = 1
                            versionName = "1.0"
                            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
                        }$kotlinJvmBlock

                        buildFeatures {
                            compose = true
                            buildConfig = true
                        }
                    }

                    dependencies {
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
                        debugImplementation(libs.androidx.ui.tooling)
                    }
                    """.trimIndent() + "\n"
                )
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun writeComposeThemeFiles(uiThemeDir: File, packageName: String, themePrefix: String) {
    File(uiThemeDir, "Color.kt").writeText(
        """
        package $packageName.ui.theme

        import androidx.compose.ui.graphics.Color

        val Purple80 = Color(0xFFD0BCFF)
        val PurpleGrey80 = Color(0xFFCCC2DC)
        val Pink80 = Color(0xFFEFB8C8)

        val Purple40 = Color(0xFF6650A4)
        val PurpleGrey40 = Color(0xFF625B71)
        val Pink40 = Color(0xFF7D5260)
        """.trimIndent() + "\n"
    )

    File(uiThemeDir, "Type.kt").writeText(
        """
        package $packageName.ui.theme

        import androidx.compose.material3.Typography
        import androidx.compose.ui.text.TextStyle
        import androidx.compose.ui.text.font.FontFamily
        import androidx.compose.ui.text.font.FontWeight
        import androidx.compose.ui.unit.sp

        val Typography = Typography(
            bodyLarge = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.5.sp
            )
        )
        """.trimIndent() + "\n"
    )

    File(uiThemeDir, "Theme.kt").writeText(
        """
        package $packageName.ui.theme

        import androidx.compose.foundation.isSystemInDarkTheme
        import androidx.compose.material3.MaterialTheme
        import androidx.compose.material3.darkColorScheme
        import androidx.compose.material3.lightColorScheme
        import androidx.compose.runtime.Composable

        private val DarkColorScheme = darkColorScheme(
            primary = Purple80,
            secondary = PurpleGrey80,
            tertiary = Pink80
        )

        private val LightColorScheme = lightColorScheme(
            primary = Purple40,
            secondary = PurpleGrey40,
            tertiary = Pink40
        )

        @Composable
        fun ${themePrefix}Theme(
            darkTheme: Boolean = isSystemInDarkTheme(),
            content: @Composable () -> Unit
        ) {
            val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
            MaterialTheme(
                colorScheme = colorScheme,
                typography = Typography,
                content = content
            )
        }
        """.trimIndent() + "\n"
    )
}

private fun writeKotlinTestFiles(testDir: File, androidTestDir: File, packageName: String) {
    File(testDir, "ExampleUnitTest.kt").writeText(
        """
        package $packageName

        import org.junit.Test
        import org.junit.Assert.*

        class ExampleUnitTest {
            @Test
            fun addition_isCorrect() {
                assertEquals(4, 2 + 2)
            }
        }
        """.trimIndent() + "\n"
    )

    File(androidTestDir, "ExampleInstrumentedTest.kt").writeText(
        """
        package $packageName

        import androidx.test.platform.app.InstrumentationRegistry
        import androidx.test.ext.junit.runners.AndroidJUnit4
        import org.junit.Test
        import org.junit.runner.RunWith
        import org.junit.Assert.*

        @RunWith(AndroidJUnit4::class)
        class ExampleInstrumentedTest {
            @Test
            fun useAppContext() {
                val appContext = InstrumentationRegistry.getInstrumentation().targetContext
                assertEquals("$packageName", appContext.getPackageName())
            }
        }
        """.trimIndent() + "\n"
    )
}

fun validateProjectName(name: String): String {
    val n = name.trim()
    if (n.isBlank()) return "Project name is required"
    if (n.length > 50) return "Project name is too long (max 50 chars)"
    return ""
}

fun validatePackageName(pkg: String): String {
    val p = pkg.trim()
    if (p.isBlank()) return "Package name is required"
    if (p.contains(" ")) return "Package name cannot contain spaces"
    if (!p.contains(".")) return "Must contain at least one dot (e.g. com.example)"
    if (p != p.lowercase()) return "Use only lowercase letters"
    if (p.startsWith(".") || p.endsWith(".")) return "Cannot start or end with a dot"
    if (p.contains("..")) return "Cannot contain consecutive dots"

    val keywords = setOf(
        "abstract","assert","boolean","break","byte","case","catch","char","class","const",
        "continue","default","do","double","else","enum","extends","final","finally","float",
        "for","goto","if","implements","import","instanceof","int","interface","long","native",
        "new","package","private","protected","public","return","short","static","strictfp",
        "super","switch","synchronized","this","throw","throws","transient","try","void",
        "volatile","while"
    )

    val parts = p.split(".")
    if (parts.size < 2) return "Must have at least two segments (e.g. com.app)"
    for (part in parts) {
        if (part.isEmpty()) return "Segments cannot be empty"
        if (!part[0].isLetter()) return "Each segment must start with a letter"
        if (!part.matches(Regex("^[a-z][a-z0-9_]*\$"))) return "Only lowercase letters, numbers, and underscores"
        if (part in keywords) return "Cannot use reserved keyword '$part'"
    }
    return ""
}

@Composable
fun CreateProjectPage(
    onBack: () -> Unit,
    onCreate: (name: String, packageName: String, language: String, minSdk: Int, targetSdk: Int) -> Unit
) {
    val context = LocalContext.current

    var projectName by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("com.example.myapplication") }
    var packageEdited by remember { mutableStateOf(false) }
    var showFinalError by remember { mutableStateOf("") }
    var hasAttemptedSubmit by remember { mutableStateOf(false) }

    var selectedToolchain by remember { mutableStateOf("AGP9") }
    var selectedLanguage by remember { mutableStateOf("Kotlin") }
    var selectedMinSdk by remember { mutableIntStateOf(26) }
    var selectedTargetSdk by remember { mutableIntStateOf(36) }

    val scrollState = rememberScrollState()

    BackHandler { onBack() }

    val suggestions = remember(projectName) {
        generateSmartPackageSuggestions(projectName)
    }

    LaunchedEffect(projectName) {
        if (!packageEdited) {
            val asciiId = transliterateToAsciiIdentifier(projectName)
            packageName = "com.example.$asciiId"
        }
    }

    val nameError = validateProjectName(projectName)
    val pkgError = validatePackageName(packageName)
    val isSdkValid = selectedTargetSdk >= selectedMinSdk

    val toolchainBannerText = if (selectedToolchain == "AGP9") {
        "Toolchain: AGP 9.3.1 • Gradle 9.5.0 • Kotlin 2.4.21"
    } else {
        "Toolchain: AGP 8.13.2 • Gradle 8.13 • Kotlin 2.1.21"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .systemBarsPadding()
    ) {
        // ================= টপ হেডার বার =================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.85f else 1f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
                label = "backBtnScale"
            )

            IconButton(
                onClick = onBack,
                interactionSource = interactionSource,
                modifier = Modifier.scale(scale).size(36.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Create Project", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Universal Android Studio, CodeAssist & CodeStudio", color = TextSecondary, fontSize = 11.sp)
            }
        }

        // ================= ফুল-স্ক্রিন প্রিমিয়াম Rounded Card =================
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                .shadow(12.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(colors = listOf(Color(0xFF1F1F24), Color(0xFF121215))))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pageVerticalScrollbar(scrollState)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(NeonLime.copy(alpha = 0.08f))
                            .border(0.8.dp, NeonLime.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = toolchainBannerText,
                            color = NeonLime,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // ০. Toolchain Compatibility Preset
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "AGP9" to "AGP 9.3 (Modern)",
                            "AGP8" to "AGP 8.13 (Universal)"
                        ).forEach { (preset, label) ->
                            val active = selectedToolchain == preset
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (active) NeonLime.copy(alpha = 0.15f) else SurfaceDark)
                                    .border(1.dp, if (active) NeonLime else SurfaceLight, RoundedCornerShape(50))
                                    .clickable { selectedToolchain = preset },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (active) NeonLime else Color.LightGray,
                                    fontSize = 11.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    // ১. Project Name
                    Text("Project name", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = {
                            projectName = it
                            showFinalError = ""
                        },
                        placeholder = { Text("My Application", color = TextSecondary.copy(alpha = 0.55f)) },
                        singleLine = true,
                        isError = (hasAttemptedSubmit && nameError.isNotEmpty()) || (projectName.isNotBlank() && nameError.isNotEmpty()),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceDark, unfocusedContainerColor = SurfaceDark,
                            errorContainerColor = SurfaceDark,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            errorTextColor = Color.White,
                            focusedBorderColor = if (projectName.isNotBlank() && nameError.isNotEmpty()) Color(0xFFFF5252) else NeonLime,
                            unfocusedBorderColor = if (hasAttemptedSubmit && nameError.isNotEmpty()) Color(0xFFFF5252) else Color.White.copy(alpha = 0.12f),
                            errorBorderColor = Color(0xFFFF5252),
                            cursorColor = NeonLime,
                            errorCursorColor = NeonLime
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (hasAttemptedSubmit && nameError.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(nameError, color = Color(0xFFFF5252), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ২. Package Name
                    Text("Package name", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = packageName,
                        onValueChange = { input ->
                            val filtered = input.lowercase().replace(" ", "")
                            packageName = filtered
                            packageEdited = true
                            showFinalError = ""
                        },
                        singleLine = true,
                        isError = hasAttemptedSubmit && pkgError.isNotEmpty(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceDark, unfocusedContainerColor = SurfaceDark,
                            errorContainerColor = SurfaceDark,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            errorTextColor = Color.White,
                            focusedBorderColor = if (pkgError.isNotEmpty()) Color(0xFFFF5252) else NeonLime,
                            unfocusedBorderColor = if (hasAttemptedSubmit && pkgError.isNotEmpty()) Color(0xFFFF5252) else Color.White.copy(alpha = 0.12f),
                            errorBorderColor = Color(0xFFFF5252),
                            cursorColor = NeonLime,
                            errorCursorColor = NeonLime
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (pkgError.isEmpty() && packageName.isNotEmpty()) {
                        Text("Valid professional package structure", color = NeonLime, fontSize = 11.sp)
                    } else if (pkgError.isNotEmpty()) {
                        Text(pkgError, color = Color(0xFFFF5252), fontSize = 11.sp)
                    }

                    if (suggestions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            suggestions.forEach { sugg ->
                                val isSelected = packageName == sugg
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) NeonLime.copy(alpha = 0.2f) else SurfaceDark)
                                        .border(0.8.dp, if (isSelected) NeonLime else SurfaceLight, RoundedCornerShape(6.dp))
                                        .clickable {
                                            packageName = sugg
                                            packageEdited = true
                                            showFinalError = ""
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = sugg,
                                        color = if (isSelected) NeonLime else Color.LightGray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // ৩. Language
                    Text("Language", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Kotlin", "Java", "C++").forEach { lang ->
                            val isSelected = selectedLanguage == lang
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (isSelected) NeonLime.copy(alpha = 0.15f) else SurfaceDark)
                                    .border(
                                        1.dp,
                                        if (isSelected) NeonLime else SurfaceLight,
                                        RoundedCornerShape(50)
                                    )
                                    .clickable { selectedLanguage = lang },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = lang,
                                    color = if (isSelected) NeonLime else Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // ৪. Minimum SDK
                    Text("Minimum SDK", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    val minSdks = listOf(
                        24 to "API 24\nAndroid 7.0",
                        26 to "API 26\nAndroid 8.0",
                        28 to "API 28\nAndroid 9",
                        30 to "API 30\nAndroid 11",
                        33 to "API 33\nAndroid 13",
                        35 to "API 35\nAndroid 15"
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        minSdks.chunked(3).forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (sdk, label) ->
                                    val isSelected = selectedMinSdk == sdk
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(if (isSelected) NeonLime.copy(alpha = 0.15f) else SurfaceDark)
                                            .border(
                                                1.dp,
                                                if (isSelected) NeonLime else SurfaceLight,
                                                RoundedCornerShape(50)
                                            )
                                            .clickable {
                                                selectedMinSdk = sdk
                                                showFinalError = ""
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) NeonLime else Color.White,
                                            fontSize = 9.sp,
                                            lineHeight = 11.sp,
                                            textAlign = TextAlign.Center,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // ৫. Target SDK
                    Text("Target SDK", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    val targetSdks = listOf(
                        33 to "API 33\nAndroid 13",
                        34 to "API 34\nAndroid 14",
                        35 to "API 35\nAndroid 15",
                        36 to "API 36\nAndroid 16",
                        37 to "API 37\nAndroid 17"
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        targetSdks.chunked(3).forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (sdk, label) ->
                                    val isSelected = selectedTargetSdk == sdk
                                    val isError = !isSdkValid && isSelected

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(
                                                if (isSelected) (if (isError) Color(0xFFFF5252).copy(alpha = 0.15f) else NeonLime.copy(alpha = 0.15f))
                                                else SurfaceDark
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) (if (isError) Color(0xFFFF5252) else NeonLime)
                                                else SurfaceLight,
                                                RoundedCornerShape(50)
                                            )
                                            .clickable {
                                                selectedTargetSdk = sdk
                                                showFinalError = ""
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) (if (isError) Color(0xFFFF5252) else NeonLime) else Color.White,
                                            fontSize = 9.sp,
                                            lineHeight = 11.sp,
                                            textAlign = TextAlign.Center,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
                        }
                    }

                    if (!isSdkValid) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFF5252).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = "Warning", tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Target SDK must be greater than or equal to Minimum SDK.", color = Color(0xFFFF5252), fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // বটম সাবমিট বাটন ও ওয়ার্নিং কার্ড
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (showFinalError.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                                .background(Color(0xFFFF5252).copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = showFinalError,
                                color = Color(0xFFFF8A80),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Button(
                        onClick = {
                            hasAttemptedSubmit = true
                            if (nameError.isNotEmpty()) {
                                showFinalError = nameError
                            } else if (pkgError.isNotEmpty()) {
                                showFinalError = pkgError
                            } else if (!isSdkValid) {
                                showFinalError = "Target SDK must be equal or greater than Minimum SDK"
                            } else {
                                showFinalError = ""
                                createProjectByLanguage(
                                    context = context,
                                    projectName = projectName,
                                    packageName = packageName,
                                    language = selectedLanguage,
                                    minSdk = selectedMinSdk,
                                    targetSdk = selectedTargetSdk,
                                    toolchainPreset = selectedToolchain
                                )
                                onCreate(projectName, packageName, selectedLanguage, selectedMinSdk, selectedTargetSdk)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonLime, contentColor = Color.Black),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text("Create Project", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}