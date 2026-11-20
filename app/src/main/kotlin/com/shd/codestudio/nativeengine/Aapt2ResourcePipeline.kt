package com.shd.codestudio.nativeengine

import android.content.Context
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.StringReader
import java.util.concurrent.TimeUnit

data class Aapt2PipelineResult(
    val success: Boolean,
    val resourcesApk: File,
    val generatedJavaDir: File,
    val diagnostics: List<BuildDiagnosticItem> = emptyList(),
    val errorMessage: String = ""
)

object Aapt2ResourcePipeline {

    private val AAPT2_ERR_REGEX = Regex(
        """^(.+?\.(?:xml|png|webp|jpg|flat)):(\d+):(?:\d+:)?\s*(error|warn|warning):\s*(.+)$""",
        RegexOption.IGNORE_CASE
    )

    fun resolveAapt2Binary(context: Context): File? {
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        val jniAapt2 = File(nativeLibDir, "libaapt2.so")
        if (jniAapt2.exists() && jniAapt2.isFile) {
            return jniAapt2
        }

        val engineDir = File(context.filesDir, "CodeStudioEngine")
        val toolsAapt2 = File(engineDir, "tools/aapt2")
        if (toolsAapt2.exists() && toolsAapt2.isFile) {
            toolsAapt2.setExecutable(true, false)
            return toolsAapt2
        }

        val buildToolsRoot = File(engineDir, "android-sdk/build-tools")
        val btAapt2 = buildToolsRoot.walkTopDown()
            .firstOrNull { it.isFile && it.name == "aapt2" }
        if (btAapt2 != null) {
            btAapt2.setExecutable(true, false)
            return btAapt2
        }
        return null
    }

    fun resolveAndroidJar(context: Context, compileSdk: Int): File? {
        val sdkDir = File(context.filesDir, "CodeStudioEngine/android-sdk")
        val exact = File(sdkDir, "platforms/android-$compileSdk/android.jar")
        if (exact.exists() && exact.isFile && exact.length() > 100_000L) return exact

        return File(sdkDir, "platforms").listFiles()
            ?.filter {
                it.isDirectory &&
                    it.name.startsWith("android-") &&
                    File(it, "android.jar").let { j -> j.exists() && j.isFile && j.length() > 100_000L }
            }
            ?.sortedByDescending { it.name.removePrefix("android-").toIntOrNull() ?: 0 }
            ?.map { File(it, "android.jar") }
            ?.firstOrNull()
    }

    fun execute(
        context: Context,
        module: ModuleConfig,
        variant: EngineBuildVariant,
        libraries: List<ResolvedLibrary>,
        mergedManifestFile: File,
        workDir: File,
        extraProjectModules: List<ModuleConfig> = emptyList(),
        isCancelled: () -> Boolean = { false },
        onLog: (String) -> Unit
    ): Aapt2PipelineResult {
        val resourcesApOut = File(workDir, "intermediates/res_linked/resources.ap_").apply {
            parentFile?.mkdirs()
            if (exists()) delete()
        }
        val genJavaDir = File(workDir, "generated/source/java").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }

        val aapt2Bin = resolveAapt2Binary(context)
            ?: return Aapt2PipelineResult(
                success = false,
                resourcesApk = resourcesApOut,
                generatedJavaDir = genJavaDir,
                errorMessage = "ARM64 AAPT2 binary missing. Place libaapt2.so in app/src/main/jniLibs/arm64-v8a/."
            )

        val androidJar = resolveAndroidJar(context, module.compileSdk)
            ?: return Aapt2PipelineResult(
                success = false,
                resourcesApk = resourcesApOut,
                generatedJavaDir = genJavaDir,
                errorMessage = "Android SDK Platform (android-${module.compileSdk}/android.jar) not found."
            )

        val compiledZips = ArrayList<File>()
        val diagnostics = ArrayList<BuildDiagnosticItem>()

        val libResCacheDir = File(MavenDependencyResolver.getCacheRoot(context), "compiled-res")
        if (!libResCacheDir.exists()) libResCacheDir.mkdirs()

        val extraPackages = LinkedHashSet<String>()
        for (lib in libraries) {
            if (isCancelled()) {
                return Aapt2PipelineResult(false, resourcesApOut, genJavaDir, diagnostics, "Cancelled")
            }
            lib.manifestFile?.let { mf ->
                readManifestPackage(mf)?.takeIf { it.isNotBlank() && it != module.namespace }?.let {
                    extraPackages.add(it)
                }
            }
            val libResDir = lib.resDir ?: continue
            if (!hasAnyResourceFiles(libResDir)) continue

            val key = "${lib.coordinate.group}_${lib.coordinate.artifact}_${lib.coordinate.version}"
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
            val outZip = File(libResCacheDir, "$key.res.zip")
            val marker = File(libResCacheDir, "$key.ok")
            if (!marker.exists() || !outZip.exists() || outZip.length() == 0L) {
                val (ok, errTxt, diags) = runAapt2CompileDir(aapt2Bin, libResDir, outZip)
                diagnostics.addAll(diags)
                if (ok && outZip.exists() && outZip.length() > 0L) {
                    marker.writeText("ok")
                } else if (!ok) {
                    onLog("  WARN: Skipped library resources for ${lib.coordinate.artifact}: ${errTxt.lines().firstOrNull() ?: "compile error"}")
                }
            }
            if (outZip.exists() && outZip.length() > 0L) {
                compiledZips.add(outZip)
            }
        }

        val subModFlatDir = File(workDir, "intermediates/submod_res_flat").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        extraProjectModules.forEachIndexed { mIdx, subMod ->
            if (subMod.namespace.isNotBlank() && subMod.namespace != module.namespace) {
                extraPackages.add(subMod.namespace)
            }
            subMod.resDirs.forEachIndexed { rIdx, rDir ->
                if (hasAnyResourceFiles(rDir)) {
                    val subZip = File(subModFlatDir, "mod_${mIdx}_res_$rIdx.zip")
                    val (ok, outTxt, diags) = runAapt2CompileDir(aapt2Bin, rDir, subZip)
                    diagnostics.addAll(diags)
                    if (!ok) {
                        onLog("ERROR: Module ${subMod.moduleName} resource compile failed:\n$outTxt")
                        return Aapt2PipelineResult(false, resourcesApOut, genJavaDir, diagnostics, "Module ${subMod.moduleName} AAPT2 compile failed")
                    }
                    if (subZip.exists() && subZip.length() > 0L) {
                        compiledZips.add(subZip)
                    }
                }
            }
        }

        if (isCancelled()) {
            return Aapt2PipelineResult(false, resourcesApOut, genJavaDir, diagnostics, "Cancelled")
        }

        val appFlatDir = File(workDir, "intermediates/res_flat").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }
        module.resDirs.forEachIndexed { idx, resDir ->
            if (hasAnyResourceFiles(resDir)) {
                val appResZip = File(appFlatDir, "app_res_$idx.zip")
                val (ok, outTxt, diags) = runAapt2CompileDir(aapt2Bin, resDir, appResZip)
                diagnostics.addAll(diags)
                if (!ok) {
                    onLog("ERROR: AAPT2 compile failed:\n$outTxt")
                    return Aapt2PipelineResult(false, resourcesApOut, genJavaDir, diagnostics, "AAPT2 resource compile failed")
                }
                if (appResZip.exists() && appResZip.length() > 0L) {
                    compiledZips.add(appResZip)
                }
            }
        }

        if (isCancelled()) {
            return Aapt2PipelineResult(false, resourcesApOut, genJavaDir, diagnostics, "Cancelled")
        }

        val linkCmd = ArrayList<String>().apply {
            add(aapt2Bin.absolutePath)
            add("link")
            add("-I")
            add(androidJar.absolutePath)
            add("--manifest")
            add(mergedManifestFile.absolutePath)
            add("-o")
            add(resourcesApOut.absolutePath)
            add("--java")
            add(genJavaDir.absolutePath)
            add("--min-sdk-version")
            add(module.minSdk.toString())
            add("--target-sdk-version")
            add(module.targetSdk.toString())
            add("--version-code")
            add(module.versionCode.toString())
            add("--version-name")
            add(module.versionName)
            add("--auto-add-overlay")
            add("--no-version-vectors")

            if (module.applicationId.isNotBlank() && module.applicationId != module.namespace) {
                add("--rename-manifest-package")
                add(module.applicationId)
            }

            if (extraPackages.isNotEmpty()) {
                add("--extra-packages")
                add(extraPackages.joinToString(":"))
            }

            module.assetsDirs.filter { it.exists() }.forEach { aDir ->
                add("-A")
                add(aDir.absolutePath)
            }
            extraProjectModules.flatMap { it.assetsDirs }.filter { it.exists() }.forEach { aDir ->
                add("-A")
                add(aDir.absolutePath)
            }
            libraries.mapNotNull { it.assetsDir }.filter { it.exists() }.forEach { aDir ->
                add("-A")
                add(aDir.absolutePath)
            }

            compiledZips.forEach { zip ->
                add("-R")
                add(zip.absolutePath)
            }
        }

        val (linkOk, linkOut, linkDiags) = runCommand(linkCmd, workDir)
        diagnostics.addAll(linkDiags)
        if (!linkOk) {
            onLog("ERROR: AAPT2 link failed:\n$linkOut")
            return Aapt2PipelineResult(false, resourcesApOut, genJavaDir, diagnostics, "AAPT2 link failed")
        }

        if (module.buildConfigEnabled) {
            generateBuildConfig(module, variant, genJavaDir)
        }

        if (module.viewBindingEnabled) {
            val hasViewBindingLib = libraries.any { it.coordinate.artifact.contains("viewbinding", ignoreCase = true) }
            val hasAnnotationLib = libraries.any { it.coordinate.artifact.contains("annotation", ignoreCase = true) }
            generateViewBindings(module, genJavaDir, hasViewBindingLib, hasAnnotationLib)
        }

        if (variant.isDebug) {
            generateDebugCrashBridge(genJavaDir)
        }

        return Aapt2PipelineResult(
            success = true,
            resourcesApk = resourcesApOut,
            generatedJavaDir = genJavaDir,
            diagnostics = diagnostics
        )
    }

    private fun runAapt2CompileDir(
        aapt2Bin: File,
        resDir: File,
        outZip: File
    ): Triple<Boolean, String, List<BuildDiagnosticItem>> {
        outZip.parentFile?.mkdirs()
        if (outZip.exists()) outZip.delete()
        val cmd = listOf(
            aapt2Bin.absolutePath,
            "compile",
            "--dir",
            resDir.absolutePath,
            "-o",
            outZip.absolutePath
        )
        return runCommand(cmd, resDir.parentFile ?: resDir)
    }

    private fun runCommand(cmd: List<String>, workDir: File): Triple<Boolean, String, List<BuildDiagnosticItem>> {
        return try {
            val pb = ProcessBuilder(cmd)
            pb.directory(workDir)
            pb.redirectErrorStream(true)
            val p = pb.start()
            val output = BufferedReader(InputStreamReader(p.inputStream, Charsets.UTF_8)).use { it.readText() }
            val finished = p.waitFor(90, TimeUnit.SECONDS)
            if (!finished) {
                p.destroyForcibly()
                return Triple(false, "AAPT2 timed out", emptyList())
            }
            val diags = output.lines().mapNotNull { parseDiagnostic(it) }
            Triple(p.exitValue() == 0, output, diags)
        } catch (e: Exception) {
            val raw = e.message ?: "AAPT2 execution error"
            val msg = if (raw.contains("Permission denied", ignoreCase = true) || raw.contains("error=13", ignoreCase = true)) {
                "AAPT2 execution blocked by Android SELinux (W^X). Bundle libaapt2.so inside app/src/main/jniLibs/arm64-v8a/."
            } else {
                raw
            }
            Triple(false, msg, emptyList())
        }
    }

    private fun parseDiagnostic(line: String): BuildDiagnosticItem? {
        val m = AAPT2_ERR_REGEX.find(line) ?: return null
        val path = m.groupValues[1]
        val ln = m.groupValues[2].toIntOrNull() ?: 0
        val kind = m.groupValues[3].lowercase()
        val msg = m.groupValues[4]
        return BuildDiagnosticItem(path, ln, 0, msg, isError = kind == "error")
    }

    private fun hasAnyResourceFiles(dir: File): Boolean {
        if (!dir.exists() || !dir.isDirectory) return false
        return dir.walkTopDown().any { it.isFile && !it.name.startsWith(".") }
    }

    private fun readManifestPackage(manifestFile: File): String? {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(manifestFile.readText(Charsets.UTF_8)))
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && parser.name == "manifest") {
                    return parser.getAttributeValue(null, "package")
                }
                ev = parser.next()
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun generateBuildConfig(module: ModuleConfig, variant: EngineBuildVariant, genJavaDir: File) {
        val pkgDir = File(genJavaDir, module.namespace.replace('.', '/')).apply { mkdirs() }
        val file = File(pkgDir, "BuildConfig.java")
        val buildType = if (variant.isDebug) "debug" else "release"
        file.writeText(
            """
            package ${module.namespace};

            public final class BuildConfig {
                public static final boolean DEBUG = ${variant.isDebug};
                public static final String APPLICATION_ID = "${module.applicationId}";
                public static final String BUILD_TYPE = "$buildType";
                public static final int VERSION_CODE = ${module.versionCode};
                public static final String VERSION_NAME = "${module.versionName}";
            }
            """.trimIndent() + "\n"
        )
    }

    private fun generateDebugCrashBridge(genJavaDir: File) {
        val pkgDir = File(genJavaDir, "com/codestudio/debug").apply { mkdirs() }

        File(pkgDir, "CrashBridgeProvider.java").writeText(
            """
            package com.codestudio.debug;

            import android.app.Application;
            import android.content.ContentProvider;
            import android.content.ContentValues;
            import android.content.Context;
            import android.content.Intent;
            import android.database.Cursor;
            import android.net.Uri;
            import android.os.Build;
            import java.io.PrintWriter;
            import java.io.StringWriter;

            public final class CrashBridgeProvider extends ContentProvider {
                @Override
                public boolean onCreate() {
                    final Context ctx = getContext();
                    if (ctx == null) return true;
                    if (Build.VERSION.SDK_INT >= 28) {
                        String proc = Application.getProcessName();
                        if (proc != null && proc.endsWith(":crash")) {
                            return true;
                        }
                    }
                    final Context appCtx = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
                    final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
                    Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                        @Override
                        public void uncaughtException(Thread thread, Throwable throwable) {
                            try {
                                StringWriter sw = new StringWriter();
                                throwable.printStackTrace(new PrintWriter(sw));
                                String trace = "Thread: " + thread.getName() + "\n\n" + sw.toString();
                                Intent intent = new Intent(appCtx, CrashBridgeActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                intent.putExtra("trace", trace);
                                intent.putExtra("pkg", appCtx.getPackageName());
                                appCtx.startActivity(intent);
                            } catch (Throwable ignored) {
                                if (prev != null) {
                                    prev.uncaughtException(thread, throwable);
                                }
                            }
                            try { Thread.sleep(250); } catch (InterruptedException ignored) {}
                            android.os.Process.killProcess(android.os.Process.myPid());
                            System.exit(10);
                        }
                    });
                    return true;
                }

                @Override
                public Cursor query(Uri u, String[] p, String s, String[] sa, String so) { return null; }
                @Override
                public String getType(Uri u) { return null; }
                @Override
                public Uri insert(Uri u, ContentValues cv) { return null; }
                @Override
                public int delete(Uri u, String s, String[] sa) { return 0; }
                @Override
                public int update(Uri u, ContentValues cv, String s, String[] sa) { return 0; }
            }
            """.trimIndent() + "\n"
        )

        File(pkgDir, "CrashBridgeActivity.java").writeText(
            """
            package com.codestudio.debug;

            import android.app.Activity;
            import android.content.ClipData;
            import android.content.ClipboardManager;
            import android.content.Context;
            import android.graphics.Color;
            import android.graphics.Typeface;
            import android.os.Bundle;
            import android.view.View;
            import android.widget.Button;
            import android.widget.HorizontalScrollView;
            import android.widget.LinearLayout;
            import android.widget.ScrollView;
            import android.widget.TextView;
            import android.widget.Toast;

            public final class CrashBridgeActivity extends Activity {
                @Override
                protected void onCreate(Bundle savedInstanceState) {
                    super.onCreate(savedInstanceState);
                    final String trace = getIntent() != null ? getIntent().getStringExtra("trace") : "Unknown error";
                    final String pkg = getIntent() != null ? getIntent().getStringExtra("pkg") : "";

                    LinearLayout root = new LinearLayout(this);
                    root.setOrientation(LinearLayout.VERTICAL);
                    root.setBackgroundColor(Color.parseColor("#0D0E12"));
                    int pad = (int) (16 * getResources().getDisplayMetrics().density);
                    root.setPadding(pad, pad * 2, pad, pad);

                    TextView title = new TextView(this);
                    title.setText("Debug Crash Report (" + pkg + ")");
                    title.setTextColor(Color.parseColor("#FF5252"));
                    title.setTextSize(17f);
                    title.setTypeface(Typeface.DEFAULT_BOLD);
                    root.addView(title);

                    ScrollView vScroll = new ScrollView(this);
                    LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
                    scrollParams.topMargin = pad;
                    scrollParams.bottomMargin = pad;
                    vScroll.setLayoutParams(scrollParams);
                    vScroll.setBackgroundColor(Color.parseColor("#161820"));

                    HorizontalScrollView hScroll = new HorizontalScrollView(this);
                    TextView tv = new TextView(this);
                    tv.setText(trace != null ? trace : "");
                    tv.setTextColor(Color.parseColor("#E6E6EA"));
                    tv.setTextSize(11f);
                    tv.setTypeface(Typeface.MONOSPACE);
                    tv.setTextIsSelectable(true);
                    tv.setPadding(pad, pad, pad, pad);
                    hScroll.addView(tv);
                    vScroll.addView(hScroll);
                    root.addView(vScroll);

                    LinearLayout btnRow = new LinearLayout(this);
                    btnRow.setOrientation(LinearLayout.HORIZONTAL);

                    Button copyBtn = new Button(this);
                    copyBtn.setText("Copy Error");
                    copyBtn.setTextColor(Color.BLACK);
                    copyBtn.setBackgroundColor(Color.parseColor("#D4FF33"));
                    copyBtn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                    copyBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            if (cm != null) {
                                cm.setPrimaryClip(ClipData.newPlainText("Crash Log", trace != null ? trace : ""));
                                Toast.makeText(CrashBridgeActivity.this, "Copied crash log!", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });

                    Button closeBtn = new Button(this);
                    closeBtn.setText("Close");
                    closeBtn.setTextColor(Color.WHITE);
                    closeBtn.setBackgroundColor(Color.parseColor("#2A2C36"));
                    LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    closeParams.leftMargin = pad / 2;
                    closeBtn.setLayoutParams(closeParams);
                    closeBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            finish();
                        }
                    });

                    btnRow.addView(copyBtn);
                    btnRow.addView(closeBtn);
                    root.addView(btnRow);

                    setContentView(root);
                }
            }
            """.trimIndent() + "\n"
        )
    }

    private fun generateViewBindings(
        module: ModuleConfig,
        genJavaDir: File,
        hasViewBindingLib: Boolean,
        hasAnnotationLib: Boolean
    ) {
        val bindingPkg = "${module.namespace}.databinding"
        val outDir = File(genJavaDir, bindingPkg.replace('.', '/')).apply { mkdirs() }

        val annoImports = if (hasAnnotationLib) {
            "import androidx.annotation.NonNull;\nimport androidx.annotation.Nullable;\n"
        } else ""
        val vbImport = if (hasViewBindingLib) "import androidx.viewbinding.ViewBinding;\n" else ""
        val implementsClause = if (hasViewBindingLib) " implements ViewBinding" else ""
        val nonNull = if (hasAnnotationLib) "@NonNull " else ""
        val nullable = if (hasAnnotationLib) "@Nullable " else ""
        val overrideAnno = if (hasViewBindingLib) "    @Override\n" else ""

        for (resDir in module.resDirs) {
            val layoutDir = File(resDir, "layout")
            if (!layoutDir.exists() || !layoutDir.isDirectory) continue
            layoutDir.listFiles()?.filter { it.isFile && it.name.endsWith(".xml", true) }?.forEach { xmlFile ->
                val layoutName = xmlFile.nameWithoutExtension
                val className = toCamelCaseClass(layoutName) + "Binding"
                val fields = extractIdsFromLayoutXml(xmlFile)

                val fieldDecls = fields.joinToString("\n") { (type, camelName) ->
                    "    public final $type $camelName;"
                }
                val ctorParams = fields.joinToString("") { (type, camelName) ->
                    ", $type $camelName"
                }
                val ctorAssigns = fields.joinToString("\n") { (_, camelName) ->
                    "        this.$camelName = $camelName;"
                }
                val bindLookups = fields.joinToString("\n") { (type, camelName, rawId) ->
                    "        $type $camelName = ($type) rootView.findViewById(${module.namespace}.R.id.$rawId);"
                }
                val bindArgs = fields.joinToString("") { (_, camelName) ->
                    ", $camelName"
                }

                File(outDir, "$className.java").writeText(
                    """
                    package $bindingPkg;

                    import android.view.LayoutInflater;
                    import android.view.View;
                    import android.view.ViewGroup;
                    $annoImports$vbImport
                    public final class $className$implementsClause {
                        private final View rootView;
                    $fieldDecls

                        private $className(${nonNull}View rootView$ctorParams) {
                            this.rootView = rootView;
                    $ctorAssigns
                        }

                    $overrideAnno    ${nonNull}public View getRoot() {
                            return rootView;
                        }

                        public static $nonNull$className inflate(${nonNull}LayoutInflater inflater) {
                            return inflate(inflater, null, false);
                        }

                        public static $nonNull$className inflate(${nonNull}LayoutInflater inflater, ${nullable}ViewGroup parent, boolean attachToParent) {
                            View root = inflater.inflate(${module.namespace}.R.layout.$layoutName, parent, false);
                            if (attachToParent && parent != null) {
                                parent.addView(root);
                            }
                            return bind(root);
                        }

                        public static $nonNull$className bind(${nonNull}View rootView) {
                    $bindLookups
                            return new $className(rootView$bindArgs);
                        }
                    }
                    """.trimIndent() + "\n"
                )
            }
        }
    }

    private fun extractIdsFromLayoutXml(xmlFile: File): List<Triple<String, String, String>> {
        val list = mutableListOf<Triple<String, String, String>>()
        val seen = HashSet<String>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlFile.readText(Charsets.UTF_8)))
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    val tag = parser.name
                    val idAttr = (0 until parser.attributeCount)
                        .firstOrNull { parser.getAttributeName(it).endsWith("id") }
                        ?.let { parser.getAttributeValue(it) }
                    if (idAttr != null && (idAttr.startsWith("@+id/") || idAttr.startsWith("@id/"))) {
                        val rawId = idAttr.substringAfter("/")
                        if (seen.add(rawId)) {
                            val viewClass = resolveViewClassName(tag)
                            val camel = toCamelCaseField(rawId)
                            list.add(Triple(viewClass, camel, rawId))
                        }
                    }
                }
                ev = parser.next()
            }
        } catch (_: Exception) { }
        return list
    }

    private fun resolveViewClassName(tag: String): String {
        if (tag.contains(".")) return tag
        return when (tag) {
            "View", "ViewStub", "ViewGroup", "SurfaceView", "TextureView" -> "android.view.$tag"
            "WebView" -> "android.webkit.WebView"
            else -> "android.widget.$tag"
        }
    }

    private fun toCamelCaseClass(snake: String): String =
        snake.split("_", "-").filter { it.isNotEmpty() }.joinToString("") {
            it.replaceFirstChar { c -> c.uppercaseChar() }
        }

    private fun toCamelCaseField(snake: String): String {
        val pascal = toCamelCaseClass(snake)
        return pascal.replaceFirstChar { it.lowercaseChar() }
    }
}