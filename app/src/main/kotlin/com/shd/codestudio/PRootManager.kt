package com.shd.codestudio

import android.content.Context
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

object PRootManager {

    fun getRootfsDir(context: Context): File = File(context.filesDir, "ubuntu-rootfs")
    
    // libproot.so এখন filesDir-এ থাকবে
    fun getProotBinary(context: Context): File = File(context.filesDir, "libproot.so")

    fun setupEnvironment(context: Context, onOutput: (String) -> Unit) {
        Thread {
            try {
                val prootBin = getProotBinary(context)
                // ১. assets থেকে libproot.so কপি করা
                if (!prootBin.exists()) {
                    onOutput("Copying PRoot binary...")
                    context.assets.open("libproot.so").use { input ->
                        prootBin.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    prootBin.setExecutable(true, false) // chmod +x
                    onOutput("✅ PRoot binary ready.")
                }

                val rootfsDir = getRootfsDir(context)
                if (rootfsDir.exists() && rootfsDir.listFiles()?.isNotEmpty() == true) {
                    onOutput("✅ Ubuntu RootFS already installed.")
                    return@Thread
                }
                rootfsDir.mkdirs()

                // ২. Ubuntu RootFS আনজিপ করা
                onOutput("Extracting Ubuntu RootFS (This may take 1-2 minutes)...")
                context.assets.open("ubuntu-rootfs.tar.gz").use { inputStream ->
                    GzipCompressorInputStream(inputStream).use { gzip ->
                        TarArchiveInputStream(gzip).use { tar ->
                            var entry = tar.nextEntry
                            while (entry != null) {
                                val newFile = File(rootfsDir, entry.name)
                                if (newFile.canonicalPath.startsWith(rootfsDir.canonicalPath)) {
                                    if (entry.isDirectory) {
                                        newFile.mkdirs()
                                    } else {
                                        newFile.parentFile?.mkdirs()
                                        FileOutputStream(newFile).use { fos ->
                                            tar.copyTo(fos)
                                        }
                                    }
                                }
                                entry = tar.nextEntry
                            }
                        }
                    }
                }
                onOutput("✅ RootFS Extraction Complete!")
            } catch (e: Exception) {
                onOutput("❌ Error: ${e.message ?: e.toString()}")
                e.printStackTrace()
            }
        }.start()
    }

    fun runInPRoot(
        context: Context,
        command: String,
        onOutput: (String) -> Unit,
        onComplete: (Boolean) -> Unit
    ) {
        Thread {
            try {
                val prootBin = getProotBinary(context)
                val rootfsDir = getRootfsDir(context)

                if (!prootBin.exists()) {
                    onOutput("❌ libproot.so not found.")
                    onComplete(false)
                    return@Thread
                }

                val cmdList = listOf(
                    prootBin.absolutePath,
                    "--link2symlink",
                    "-0", 
                    "-r", rootfsDir.absolutePath,
                    "-b", "/dev", "-b", "/proc", "-b", "/sys",
                    "-w", "/root",
                    "/usr/bin/env", "-i",
                    "HOME=/root",
                    "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                    "TERM=xterm-256color",
                    "/bin/bash", "-c", command
                )

                onOutput("Running: $command")

                val processBuilder = ProcessBuilder(cmdList)
                processBuilder.environment()["PROOT_TMP_DIR"] = context.cacheDir.absolutePath
                processBuilder.redirectErrorStream(true)

                val process = processBuilder.start()
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    line?.let { onOutput(it) }
                }

                val exitCode = process.waitFor()
                onOutput("Process finished with code: $exitCode")
                onComplete(exitCode == 0)

            } catch (e: Exception) {
                e.printStackTrace()
                onOutput("❌ ERROR: ${e.message ?: e.toString()}")
                onComplete(false)
            }
        }.start()
    }
}