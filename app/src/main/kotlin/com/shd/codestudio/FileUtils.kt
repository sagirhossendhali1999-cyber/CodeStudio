package com.shd.codestudio

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

// ================= ১. প্রজেক্ট লিস্ট সেভ ও লোড =================
fun saveProjects(context: Context, projects: List<Project>) {
    try {
        val prefs = context.getSharedPreferences("CodeStudioPrefs", Context.MODE_PRIVATE)
        val text = projects.joinToString("\n") { "${it.name}|${it.type}" }
        prefs.edit().putString("saved_projects", text).apply()
    } catch (e: Exception) { }
}

fun loadProjects(context: Context): List<Project> {
    try {
        val prefs = context.getSharedPreferences("CodeStudioPrefs", Context.MODE_PRIVATE)
        val text = prefs.getString("saved_projects", "") ?: ""
        if (text.isBlank()) return emptyList()
        
        val list = mutableListOf<Project>()
        for (line in text.split("\n")) {
            if (line.isBlank()) continue
            val parts = line.split("|")
            if (parts.size >= 2) {
                list.add(Project(parts[0], parts[1], Color(0xFFA855F7))) 
            }
        }
        return list
    } catch (e: Exception) { return emptyList() }
}

// ================= ২. স্টোরেজ পারমিশন ও জিপ ফাইল =================
fun hasStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

fun loadZipFiles(context: Context): List<ZipItem> {
    val list = mutableListOf<ZipItem>()
    val seen = mutableSetOf<String>()
    try {
        val collections = listOf(MediaStore.Files.getContentUri("external"), MediaStore.Downloads.getContentUri("external"))
        for (collection in collections) {
            val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.SIZE, MediaStore.Files.FileColumns.DATE_MODIFIED)
            val selection = MediaStore.Files.FileColumns.DISPLAY_NAME + " LIKE ?"
            val selectionArgs = arrayOf("%.zip")
            val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            try {
                context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameCol) ?: continue
                        if (!name.lowercase().endsWith(".zip")) continue
                        if (seen.contains(name)) continue
                        seen.add(name)
                        val id = cursor.getLong(idCol)
                        val size = try { cursor.getLong(sizeCol) } catch (e: Exception) { 0L }
                        val date = try { cursor.getLong(dateCol) } catch (e: Exception) { 0L }
                        val uri = ContentUris.withAppendedId(collection, id)
                        list.add(ZipItem(name, uri, size, date))
                    }
                }
            } catch (e: Exception) { }
        }
    } catch (e: Exception) { }
    return list.sortedByDescending { it.date }
}

fun formatSize(size: Long): String {
    return if (size < 1024) "$size B" else if (size < 1024 * 1024) "${size / 1024} KB" else "${size / (1024 * 1024)} MB"
}

fun formatDate(sec: Long): String {
    if (sec == 0L) return ""
    return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(sec * 1000L))
}

fun unzipFile(context: Context, zipUri: Uri, projectName: String): Boolean {
    return try {
        val destDir = File(context.getExternalFilesDir(null), "CodeStudioProjects/$projectName")
        if (!destDir.exists()) destDir.mkdirs()
        context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zis ->
                var zipEntry = zis.nextEntry
                while (zipEntry != null) {
                    val newFile = File(destDir, zipEntry.name)
                    if (newFile.canonicalPath.startsWith(destDir.canonicalPath)) {
                        if (zipEntry.isDirectory) newFile.mkdirs()
                        else {
                            newFile.parentFile?.mkdirs()
                            FileOutputStream(newFile).use { fos ->
                                val buffer = ByteArray(1024)
                                var len: Int
                                while (zis.read(buffer).also { len = it } > 0) fos.write(buffer, 0, len)
                            }
                        }
                    }
                    zis.closeEntry()
                    zipEntry = zis.nextEntry
                }
            }
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

// ================= ৩. ১০০% স্ট্যান্ডার্ড প্রজেক্ট স্ট্রাকচার জেনারেটর =================
fun getProjectDirectory(context: Context, projectName: String): File {
    val rootDir = File(context.getExternalFilesDir(null), "CodeStudioProjects")
    if (!rootDir.exists()) rootDir.mkdirs()
    return File(rootDir, projectName)
}

fun createDefaultProjectFiles(context: Context, projectName: String, packageName: String, language: String, minSdk: Int, targetSdk: Int) {
    val projectDir = getProjectDirectory(context, projectName)
    
    // 🚨 ১. পুরোনো সব ফাইল ও ফোল্ডার মুছে ফ্রেশ জায়গা তৈরি করা (ক্লিনআপ)
    // এটি নিশ্চিত করবে যে আগের কোনো ভুল ফাইল (যেমন রুটে থাকা MainActivity.kt বা AndroidManifest.xml) থাকবে না।
    if (projectDir.exists()) {
        projectDir.deleteRecursively()
    }
    projectDir.mkdirs()

    // 🚨 ২. রুট লেভেলের স্ট্যান্ডার্ড ফাইল তৈরি
    File(projectDir, "settings.gradle.kts").writeText("""pluginManagement {
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
    }
}

rootProject.name = "$projectName"
include(":app")""")

    File(projectDir, "build.gradle.kts").writeText("""// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}""")

    File(projectDir, "gradle.properties").writeText("""org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true""")

    File(projectDir, "local.properties").writeText("""# Location of the Android SDK. This file should not be checked into version control.
sdk.dir=/path/to/your/android/sdk""")

    File(projectDir, "gradlew").writeText("#!/bin/sh\n# Gradle Wrapper script (simplified for mobile IDE)")
    File(projectDir, "gradlew.bat").writeText("@rem Gradle Wrapper script (simplified for mobile IDE)")

    // Gradle Wrapper ফোল্ডার
    val gradleWrapperDir = File(projectDir, "gradle/wrapper")
    gradleWrapperDir.mkdirs()
    File(gradleWrapperDir, "gradle-wrapper.properties").writeText("""distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\\://services.gradle.org/distributions/gradle-8.2-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists""")

    // 🚨 ৩. app মডিউলের স্ট্যান্ডার্ড ফাইল তৈরি
    val appDir = File(projectDir, "app")
    val srcMainDir = File(appDir, "src/main")
    val packagePath = packageName.replace(".", "/")
    val codeDirName = if (language == "Java") "java" else "kotlin"
    val codeDir = File(srcMainDir, "$codeDirName/$packagePath")
    
    val resValuesDir = File(srcMainDir, "res/values")
    val resLayoutDir = File(srcMainDir, "res/layout")
    val resDrawableDir = File(srcMainDir, "res/drawable")
    val resMipmapDir = File(srcMainDir, "res/mipmap-anydpi-v26")
    val resXmlDir = File(srcMainDir, "res/xml")

    codeDir.mkdirs()
    resValuesDir.mkdirs()
    resDrawableDir.mkdirs()
    resMipmapDir.mkdirs()
    resXmlDir.mkdirs()

    File(appDir, "build.gradle.kts").writeText("""plugins {
    id("com.android.application")
    ${if (language == "Kotlin") "id(\"org.jetbrains.kotlin.android\")" else ""}
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
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    ${if (language == "Kotlin") "kotlinOptions { jvmTarget = \"17\" }" else ""}
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    ${if (language == "Java") "implementation(\"androidx.appcompat:appcompat:1.6.1\")" else ""}
}""")

    File(srcMainDir, "AndroidManifest.xml").writeText("""<?xml version="1.0" encoding="utf-8"?>
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
        android:theme="@style/Theme.App"
        tools:targetApi="31">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.App">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>""")

    File(resXmlDir, "backup_rules.xml").writeText("""<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="sharedpref" path="device.xml"/>
</full-backup-content>""")
    File(resXmlDir, "data_extraction_rules.xml").writeText("""<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="sharedpref" path="device.xml"/>
    </cloud-backup>
</data-extraction-rules>""")

    // সোর্স কোড (শুধু সঠিক ফোল্ডারে তৈরি হবে)
    if (language == "Java") {
        File(codeDir, "MainActivity.java").writeText("""package $packageName;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}""")
        resLayoutDir.mkdirs()
        File(resLayoutDir, "activity_main.xml").writeText("""<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Hello $projectName!"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>""")
    } else {
        File(codeDir, "MainActivity.kt").writeText("""package $packageName

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Hello from $projectName!")
            }
        }
    }
}""")
    }

    // রিসোর্স ভ্যালু
    File(resValuesDir, "strings.xml").writeText("""<resources>
    <string name="app_name">$projectName</string>
</resources>""")
    File(resValuesDir, "colors.xml").writeText("""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="purple_200">#FFBB86FC</color>
    <color name="purple_500">#FF6200EE</color>
    <color name="purple_700">#FF3700B3</color>
    <color name="teal_200">#FF03DAC5</color>
    <color name="teal_700">#FF018786</color>
    <color name="black">#FF000000</color>
    <color name="white">#FFFFFFFF</color>
</resources>""")
    File(resValuesDir, "themes.xml").writeText("""<resources>
    <style name="Theme.App" parent="android:Theme.Material.Light.NoActionBar" />
</resources>""")

    // mipmap আইকন (ডামি ফাইল)
    File(resMipmapDir, "ic_launcher.xml").writeText("""<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/purple_500" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>""")
    File(resMipmapDir, "ic_launcher_round.xml").writeText("""<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/purple_500" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>""")
    File(resDrawableDir, "ic_launcher_foreground.xml").writeText("""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M0,0h108v108h-108z" />
</vector>""")

    // proguard-rules.pro
    File(appDir, "proguard-rules.pro").writeText("# Add project specific ProGuard rules here.")
}

// ফাইল রিড এবং সেভ
fun readFileContent(context: Context, projectName: String, fileName: String): String {
    val file = File(getProjectDirectory(context, projectName), fileName)
    return if (file.exists()) file.readText() else "// File not found"
}

fun saveFileContent(context: Context, projectName: String, fileName: String, content: String) {
    val file = File(getProjectDirectory(context, projectName), fileName)
    file.parentFile?.mkdirs()
    file.writeText(content)
}

// ================= ৪. ট্রি স্ট্রাকচার তৈরি করা =================
data class FileTreeNode(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val children: MutableList<FileTreeNode> = mutableListOf()
)

fun getProjectFileTree(context: Context, projectName: String): List<FileTreeNode> {
    val rootDir = getProjectDirectory(context, projectName)
    
    fun buildTree(dir: File): List<FileTreeNode> {
        val files = dir.listFiles() ?: return emptyList()
        // ফোল্ডার আগে দেখাবে, তারপর ফাইল (A-Z)
        val sortedFiles = files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        
        return sortedFiles.map { file ->
            val node = FileTreeNode(
                name = file.name,
                path = file.relativeTo(rootDir).path,
                isDirectory = file.isDirectory
            )
            if (file.isDirectory) {
                node.children.addAll(buildTree(file))
            }
            node
        }
    }
    
    return buildTree(rootDir)
}