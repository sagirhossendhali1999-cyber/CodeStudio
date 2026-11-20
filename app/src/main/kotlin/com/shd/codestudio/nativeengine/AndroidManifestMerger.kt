package com.shd.codestudio.nativeengine

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

data class ManifestMergeResult(
    val success: Boolean,
    val mergedManifestFile: File,
    val mergedLibrariesCount: Int = 0,
    val warnings: List<String> = emptyList()
)

object AndroidManifestMerger {

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val TOOLS_NS = "http://schemas.android.com/tools"

    fun merge(
        module: ModuleConfig,
        libraries: List<ResolvedLibrary>,
        outputManifestFile: File,
        injectDebugCrashBridge: Boolean = false
    ): ManifestMergeResult {
        val warnings = mutableListOf<String>()
        outputManifestFile.parentFile?.mkdirs()

        val dbf = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val builder = dbf.newDocumentBuilder()

        // ১. মূল অ্যাপ ম্যানিফেস্ট লোড বা ডিফল্ট তৈরি
        val mainDoc: Document = if (module.manifestFile != null && module.manifestFile.exists()) {
            builder.parse(module.manifestFile)
        } else {
            createDefaultManifestDocument(builder, module)
        }

        val root = mainDoc.documentElement
        ensureNamespaceAttrs(root)
        root.setAttribute("package", module.namespace)
        if (!root.hasAttributeNS(ANDROID_NS, "versionCode")) {
            root.setAttribute("android:versionCode", module.versionCode.toString())
        }
        if (!root.hasAttributeNS(ANDROID_NS, "versionName")) {
            root.setAttribute("android:versionName", module.versionName)
        }

        // <uses-sdk> নিশ্চিত করা
        ensureUsesSdk(mainDoc, root, module.minSdk, module.targetSdk)

        // <application> ট্যাগ নিশ্চিত করা ও রিলেটিভ কম্পোনেন্ট নাম (যেমন .MainActivity) প্রসারিত করা
        val appEl = findOrCreateChildElement(mainDoc, root, "application")
        expandRelativeComponentNames(appEl, module.namespace)

        val existingTopPermissions = collectKeySet(root, setOf("uses-permission", "uses-permission-sdk-23", "uses-feature"))
        val existingAppComponents = collectComponentMap(
            appEl,
            setOf("activity", "activity-alias", "service", "receiver", "provider", "meta-data", "property", "uses-library")
        )
        val removedAppKeys = collectToolsRemovedKeys(appEl)

        // ২. প্রতিটি .aar লাইব্রেরির AndroidManifest.xml মার্জ করা
        var mergedLibs = 0
        for (lib in libraries) {
            val libManifest = lib.manifestFile ?: continue
            if (!libManifest.exists()) continue
            try {
                val libDoc = builder.parse(libManifest)
                val libRoot = libDoc.documentElement ?: continue
                val libPkg = libRoot.getAttribute("package").trim()

                val libChildren = libRoot.childNodes
                for (i in 0 until libChildren.length) {
                    val child = libChildren.item(i)
                    if (child.nodeType != Node.ELEMENT_NODE) continue
                    val el = child as Element
                    val tag = el.tagName

                    when (tag) {
                        "uses-permission", "uses-permission-sdk-23", "uses-feature" -> {
                            val nameAttr = getAndroidAttr(el, "name")
                            val key = "$tag|$nameAttr"
                            if (nameAttr.isNotEmpty() && key !in existingTopPermissions) {
                                val imported = mainDoc.importNode(el, true) as Element
                                resolvePlaceholdersRecursively(imported, module.applicationId, libPkg)
                                root.appendChild(imported)
                                existingTopPermissions.add(key)
                            }
                        }
                        "queries" -> {
                            val imported = mainDoc.importNode(el, true) as Element
                            resolvePlaceholdersRecursively(imported, module.applicationId, libPkg)
                            root.appendChild(imported)
                        }
                        "application" -> {
                            val factoryAttr = getAndroidAttr(el, "appComponentFactory")
                            if (factoryAttr.isNotEmpty() && getAndroidAttr(appEl, "appComponentFactory").isEmpty()) {
                                appEl.setAttribute("android:appComponentFactory", factoryAttr)
                            }
                            mergeApplicationChildren(
                                mainDoc = mainDoc,
                                targetAppEl = appEl,
                                sourceAppEl = el,
                                applicationId = module.applicationId,
                                libPackage = libPkg,
                                existingComponents = existingAppComponents,
                                removedKeys = removedAppKeys
                            )
                        }
                    }
                }
                mergedLibs++
            } catch (e: Exception) {
                warnings.add("Manifest merge skipped for ${lib.coordinate.notation}: ${e.message}")
            }
        }

        // ৩. Debug বিল্ডে ক্র্যাশ ডিবাগার কম্পোনেন্ট যুক্ত করা
        if (injectDebugCrashBridge) {
            val crashProviderKey = "provider|com.codestudio.debug.CrashBridgeProvider"
            if (crashProviderKey !in existingAppComponents) {
                val provider = mainDoc.createElement("provider").apply {
                    setAttribute("android:name", "com.codestudio.debug.CrashBridgeProvider")
                    setAttribute("android:authorities", "${module.applicationId}.codestudio-crashbridge")
                    setAttribute("android:exported", "false")
                    setAttribute("android:initOrder", "2147483647")
                }
                appEl.appendChild(provider)
                existingAppComponents[crashProviderKey] = provider
            }

            val crashActivityKey = "activity|com.codestudio.debug.CrashBridgeActivity"
            if (crashActivityKey !in existingAppComponents) {
                val activity = mainDoc.createElement("activity").apply {
                    setAttribute("android:name", "com.codestudio.debug.CrashBridgeActivity")
                    setAttribute("android:exported", "false")
                    setAttribute("android:process", ":crash")
                    setAttribute("android:theme", "@android:style/Theme.DeviceDefault.NoActionBar")
                }
                appEl.appendChild(activity)
                existingAppComponents[crashActivityKey] = activity
            }
        }

        // ৪. ${applicationId} প্লেসহোল্ডার রিজল্ভ ও tools:node ক্লিনআপ
        resolvePlaceholdersRecursively(root, module.applicationId, module.namespace)
        stripToolsMergeDirectives(root)

        val serialized = serializeDocument(mainDoc)
        outputManifestFile.writeText(serialized, Charsets.UTF_8)

        return ManifestMergeResult(
            success = true,
            mergedManifestFile = outputManifestFile,
            mergedLibrariesCount = mergedLibs,
            warnings = warnings
        )
    }

    private fun expandRelativeComponentNames(appEl: Element, namespace: String) {
        if (namespace.isBlank()) return
        val appCustomName = getAndroidAttr(appEl, "name")
        if (appCustomName.isNotBlank()) {
            val expanded = expandComponentName(appCustomName, namespace)
            if (expanded != appCustomName) {
                appEl.setAttribute("android:name", expanded)
            }
        }

        val componentTags = setOf("activity", "activity-alias", "service", "receiver", "provider")
        val children = appEl.childNodes
        for (i in 0 until children.length) {
            val n = children.item(i)
            if (n.nodeType != Node.ELEMENT_NODE) continue
            val el = n as Element
            if (el.tagName in componentTags) {
                val rawName = getAndroidAttr(el, "name")
                val expanded = expandComponentName(rawName, namespace)
                if (expanded.isNotEmpty() && expanded != rawName) {
                    el.setAttribute("android:name", expanded)
                }
            }
        }
    }

    private fun mergeApplicationChildren(
        mainDoc: Document,
        targetAppEl: Element,
        sourceAppEl: Element,
        applicationId: String,
        libPackage: String,
        existingComponents: MutableMap<String, Element>,
        removedKeys: Set<String>
    ) {
        val nodes = sourceAppEl.childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n.nodeType != Node.ELEMENT_NODE) continue
            val el = n as Element
            val tag = el.tagName
            val toolsNode = getToolsAttr(el, "node")
            if (toolsNode == "remove") continue

            val rawName = getAndroidAttr(el, "name")
            val resolvedName = expandComponentName(rawName, libPackage)
            val key = "$tag|$resolvedName"

            if (key in removedKeys) continue

            val existingTarget = if (resolvedName.isNotEmpty()) existingComponents[key] else null
            if (existingTarget != null) {
                // ডুপ্লিকেট প্রোভাইডার/সার্ভিসের (যেমন androidx.startup.InitializationProvider) ভেতরের <meta-data> মার্জ করা
                mergeNestedMetadataChildren(mainDoc, existingTarget, el, applicationId, libPackage)
                continue
            }

            val imported = mainDoc.importNode(el, true) as Element
            if (resolvedName.isNotEmpty() && resolvedName != rawName) {
                imported.setAttribute("android:name", resolvedName)
            }
            resolvePlaceholdersRecursively(imported, applicationId, libPackage)
            targetAppEl.appendChild(imported)
            if (resolvedName.isNotEmpty()) {
                existingComponents[key] = imported
            }
        }
    }

    private fun mergeNestedMetadataChildren(
        mainDoc: Document,
        targetEl: Element,
        sourceEl: Element,
        applicationId: String,
        libPackage: String
    ) {
        val existingChildren = HashSet<String>()
        val targetNodes = targetEl.childNodes
        for (i in 0 until targetNodes.length) {
            val tn = targetNodes.item(i)
            if (tn.nodeType == Node.ELEMENT_NODE) {
                val tel = tn as Element
                existingChildren.add("${tel.tagName}|${getAndroidAttr(tel, "name")}")
            }
        }

        val srcNodes = sourceEl.childNodes
        for (i in 0 until srcNodes.length) {
            val sn = srcNodes.item(i)
            if (sn.nodeType != Node.ELEMENT_NODE) continue
            val sel = sn as Element
            if (getToolsAttr(sel, "node") == "remove") continue
            val cKey = "${sel.tagName}|${getAndroidAttr(sel, "name")}"
            if (sel.tagName == "meta-data" && cKey in existingChildren) continue
            val imported = mainDoc.importNode(sel, true) as Element
            resolvePlaceholdersRecursively(imported, applicationId, libPackage)
            targetEl.appendChild(imported)
            existingChildren.add(cKey)
        }
    }

    private fun expandComponentName(rawName: String, pkg: String): String {
        if (rawName.isBlank()) return ""
        if (rawName.startsWith(".") && pkg.isNotBlank()) return "$pkg$rawName"
        if (!rawName.contains(".") && pkg.isNotBlank()) return "$pkg.$rawName"
        return rawName
    }

    private fun resolvePlaceholdersRecursively(el: Element, applicationId: String, pkg: String) {
        val attrs = el.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i)
            val v = attr.nodeValue ?: continue
            if (v.contains("\${")) {
                var replaced = v.replace("\${applicationId}", applicationId)
                if (pkg.isNotBlank()) {
                    replaced = replaced.replace("\${packageName}", pkg)
                }
                attr.nodeValue = replaced
            }
        }
        val children = el.childNodes
        for (i in 0 until children.length) {
            val c = children.item(i)
            if (c.nodeType == Node.ELEMENT_NODE) {
                resolvePlaceholdersRecursively(c as Element, applicationId, pkg)
            }
        }
    }

    private fun stripToolsMergeDirectives(el: Element) {
        listOf("node", "replace", "selector").forEach { attr ->
            if (el.hasAttributeNS(TOOLS_NS, attr)) el.removeAttributeNS(TOOLS_NS, attr)
            if (el.hasAttribute("tools:$attr")) el.removeAttribute("tools:$attr")
        }

        val children = el.childNodes
        for (i in 0 until children.length) {
            val c = children.item(i)
            if (c.nodeType == Node.ELEMENT_NODE) {
                stripToolsMergeDirectives(c as Element)
            }
        }
    }

    private fun ensureUsesSdk(doc: Document, root: Element, minSdk: Int, targetSdk: Int) {
        val list = root.getElementsByTagName("uses-sdk")
        val el = if (list.length > 0) {
            list.item(0) as Element
        } else {
            doc.createElement("uses-sdk").also { root.insertBefore(it, root.firstChild) }
        }
        el.setAttribute("android:minSdkVersion", minSdk.toString())
        el.setAttribute("android:targetSdkVersion", targetSdk.toString())
    }

    private fun findOrCreateChildElement(doc: Document, parent: Element, tagName: String): Element {
        val list = parent.childNodes
        for (i in 0 until list.length) {
            val n = list.item(i)
            if (n.nodeType == Node.ELEMENT_NODE && (n as Element).tagName == tagName) {
                return n
            }
        }
        return doc.createElement(tagName).also { parent.appendChild(it) }
    }

    private fun collectKeySet(parent: Element, tags: Set<String>): MutableSet<String> {
        val set = HashSet<String>()
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val n = children.item(i)
            if (n.nodeType != Node.ELEMENT_NODE) continue
            val el = n as Element
            if (el.tagName in tags) {
                val name = getAndroidAttr(el, "name")
                if (name.isNotEmpty()) set.add("${el.tagName}|$name")
            }
        }
        return set
    }

    private fun collectComponentMap(parent: Element, tags: Set<String>): MutableMap<String, Element> {
        val map = LinkedHashMap<String, Element>()
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val n = children.item(i)
            if (n.nodeType != Node.ELEMENT_NODE) continue
            val el = n as Element
            if (el.tagName in tags) {
                val name = getAndroidAttr(el, "name")
                if (name.isNotEmpty()) map["${el.tagName}|$name"] = el
            }
        }
        return map
    }

    private fun collectToolsRemovedKeys(parent: Element): Set<String> {
        val set = HashSet<String>()
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val n = children.item(i)
            if (n.nodeType != Node.ELEMENT_NODE) continue
            val el = n as Element
            if (getToolsAttr(el, "node") == "remove") {
                val name = getAndroidAttr(el, "name")
                if (name.isNotEmpty()) set.add("${el.tagName}|$name")
            }
        }
        return set
    }

    private fun getAndroidAttr(el: Element, localName: String): String {
        val nsVal = el.getAttributeNS(ANDROID_NS, localName)
        if (!nsVal.isNullOrBlank()) return nsVal
        return el.getAttribute("android:$localName") ?: ""
    }

    private fun getToolsAttr(el: Element, localName: String): String {
        val nsVal = el.getAttributeNS(TOOLS_NS, localName)
        if (!nsVal.isNullOrBlank()) return nsVal
        return el.getAttribute("tools:$localName") ?: ""
    }

    private fun ensureNamespaceAttrs(root: Element) {
        if (!root.hasAttribute("xmlns:android")) {
            root.setAttribute("xmlns:android", ANDROID_NS)
        }
        if (!root.hasAttribute("xmlns:tools")) {
            root.setAttribute("xmlns:tools", TOOLS_NS)
        }
    }

    private fun createDefaultManifestDocument(
        builder: javax.xml.parsers.DocumentBuilder,
        module: ModuleConfig
    ): Document {
        val doc = builder.newDocument()
        val manifest = doc.createElement("manifest")
        manifest.setAttribute("xmlns:android", ANDROID_NS)
        manifest.setAttribute("package", module.namespace)
        doc.appendChild(manifest)
        val app = doc.createElement("application")
        app.setAttribute("android:label", module.applicationId.substringAfterLast("."))
        manifest.appendChild(app)
        return doc
    }

    private fun serializeDocument(doc: Document): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        serializeElement(doc.documentElement, sb, 0)
        return sb.toString()
    }

    private fun serializeElement(el: Element, sb: StringBuilder, indent: Int) {
        val pad = "    ".repeat(indent)
        sb.append(pad).append('<').append(el.tagName)

        val attrs = el.attributes
        for (i in 0 until attrs.length) {
            val a = attrs.item(i)
            sb.append(' ')
                .append(a.nodeName)
                .append("=\"")
                .append(escapeXml(a.nodeValue ?: ""))
                .append('"')
        }

        val childElements = mutableListOf<Element>()
        val textContent = StringBuilder()
        val children = el.childNodes
        for (i in 0 until children.length) {
            val c = children.item(i)
            when (c.nodeType) {
                Node.ELEMENT_NODE -> childElements.add(c as Element)
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
                    val t = c.nodeValue?.trim()
                    if (!t.isNullOrEmpty()) textContent.append(t)
                }
            }
        }

        if (childElements.isEmpty() && textContent.isEmpty()) {
            sb.append(" />\n")
        } else if (childElements.isEmpty()) {
            sb.append('>').append(escapeXml(textContent.toString())).append("</").append(el.tagName).append(">\n")
        } else {
            sb.append(">\n")
            for (child in childElements) {
                serializeElement(child, sb, indent + 1)
            }
            sb.append(pad).append("</").append(el.tagName).append(">\n")
        }
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}