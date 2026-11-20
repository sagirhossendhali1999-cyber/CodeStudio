package com.shd.codestudio.git

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Html
import android.util.Base64
import com.shd.codestudio.homescreen.ImportProgress
import com.shd.codestudio.homescreen.getProjectDirectory
import com.shd.codestudio.homescreen.loadProjects
import com.shd.codestudio.homescreen.saveProjects
import com.shd.codestudio.models.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.zip.ZipInputStream

data class GitHubWorkflowRunInfo(
    val id: Long,
    val status: String,
    val conclusion: String?,
    val htmlUrl: String
)

data class AutoPushBuildResult(
    val success: Boolean,
    val owner: String = "",
    val repoName: String = "",
    val branch: String = "main",
    val commitSha: String = "",
    val repoHtmlUrl: String = "",
    val message: String = ""
)

object GitManager {

    private val IGNORED_PUSH_DIRS = setOf(".git", ".gradle", "build", ".kotlin", ".cxx", "captures", "node_modules", ".idea")

    fun parseGitHubUrl(rawUrl: String): Pair<String, String>? {
        val clean = rawUrl.trim().removeSuffix(".git").removeSuffix("/")
        val regex = Regex("github\\.com[:/]([a-zA-Z0-9_.-]+)/([a-zA-Z0-9_.-]+)")
        val match = regex.find(clean) ?: return null
        return match.groupValues[1] to match.groupValues[2]
    }

    fun sanitizeRepoName(projectName: String): String {
        val cleaned = projectName.trim()
            .replace(" ", "-")
            .replace(Regex("[^a-zA-Z0-9_.-]"), "")
        return cleaned.ifEmpty { "CodeStudioApp" }
    }

    private fun extractGitHubErrorMessage(code: Int, body: String): String {
        return try {
            val json = JSONObject(body)
            val msg = json.optString("message", "").trim()
            if (msg.isNotEmpty()) "$msg (HTTP $code)" else "HTTP $code"
        } catch (_: Exception) {
            "HTTP $code"
        }
    }

    // ================= রিপোজিটরি GitHub-এ আসলেই আছে কিনা রিয়েল-টাইম যাচাই =================
    suspend fun checkRepositoryExists(
        token: String,
        repoUrl: String
    ): Result<GitHubRepoItem?> = withContext(Dispatchers.IO) {
        val (owner, repoName) = parseGitHubUrl(repoUrl)
            ?: return@withContext Result.failure(Exception("Invalid GitHub repository URL"))
        try {
            val res = executeHttp("https://api.github.com/repos/$owner/$repoName", "GET", token, null)
            when {
                res.code in 200..299 -> {
                    val obj = JSONObject(res.body)
                    Result.success(
                        GitHubRepoItem(
                            id = obj.optLong("id"),
                            name = obj.optString("name"),
                            fullName = obj.optString("full_name"),
                            description = obj.optString("description").takeIf { it != "null" && it.isNotBlank() },
                            htmlUrl = obj.optString("html_url"),
                            cloneUrl = obj.optString("clone_url"),
                            language = obj.optString("language").takeIf { it != "null" && it.isNotBlank() },
                            isFork = obj.optBoolean("fork", false),
                            isPrivate = obj.optBoolean("private", false),
                            updatedAt = obj.optString("updated_at"),
                            defaultBranch = obj.optString("default_branch", "main")
                        )
                    )
                }
                res.code == 404 -> Result.success(null)
                else -> Result.failure(Exception(extractGitHubErrorMessage(res.code, res.body)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ================= সম্পূর্ণ ইন-অ্যাপ গিটহাব প্রোফাইল ভেরিফিকেশন =================
    suspend fun verifyRealGitHubAccount(emailOrUsername: String): Result<GitCredentials> = withContext(Dispatchers.IO) {
        val input = emailOrUsername.trim()
        if (input.isBlank()) {
            return@withContext Result.failure(Exception("Please enter your GitHub email or username"))
        }

        try {
            if (input.contains("@")) {
                var hitRateLimit = false

                val emailQuery = URLEncoder.encode("$input in:email", "UTF-8")
                val userRes = executeHttp("https://api.github.com/search/users?q=$emailQuery", "GET", "", null)
                if (userRes.code == 403) hitRateLimit = true
                if (userRes.code in 200..299) {
                    val json = JSONObject(userRes.body)
                    if (json.optInt("total_count", 0) > 0) {
                        val item = json.getJSONArray("items").getJSONObject(0)
                        val login = item.optString("login", "")
                        val avatar = item.optString("avatar_url", "")
                        if (login.isNotEmpty()) {
                            return@withContext Result.success(
                                GitCredentials(token = "", username = login, name = login, email = input, avatarUrl = avatar)
                            )
                        }
                    }
                }

                val commitQuery = URLEncoder.encode("author-email:$input", "UTF-8")
                val commitRes = executeHttp("https://api.github.com/search/commits?q=$commitQuery&per_page=1", "GET", "", null)
                if (commitRes.code == 403) hitRateLimit = true
                if (commitRes.code in 200..299) {
                    val json = JSONObject(commitRes.body)
                    if (json.optInt("total_count", 0) > 0) {
                        val firstCommit = json.getJSONArray("items").getJSONObject(0)
                        val author = firstCommit.optJSONObject("author") ?: firstCommit.optJSONObject("committer")
                        val login = author?.optString("login", "") ?: ""
                        val avatar = author?.optString("avatar_url", "") ?: ""
                        if (login.isNotEmpty()) {
                            return@withContext Result.success(
                                GitCredentials(token = "", username = login, name = login, email = input, avatarUrl = avatar)
                            )
                        }
                    }
                }

                val exactRes = executeHttp("https://api.github.com/search/users?q=${URLEncoder.encode(input, "UTF-8")}", "GET", "", null)
                if (exactRes.code == 403) hitRateLimit = true
                if (exactRes.code in 200..299) {
                    val json = JSONObject(exactRes.body)
                    if (json.optInt("total_count", 0) > 0) {
                        val item = json.getJSONArray("items").getJSONObject(0)
                        val login = item.optString("login", "")
                        val avatar = item.optString("avatar_url", "")
                        if (login.isNotEmpty()) {
                            return@withContext Result.success(
                                GitCredentials(token = "", username = login, name = login, email = input, avatarUrl = avatar)
                            )
                        }
                    }
                }

                if (hitRateLimit) {
                    return@withContext Result.failure(
                        Exception("GitHub API rate limit reached (403). Please enter your GitHub Username or use Token (PAT).")
                    )
                }
                return@withContext Result.failure(
                    Exception("No GitHub account found for '$input'. Create an account on GitHub or sign in with your GitHub Username / PAT.")
                )
            } else {
                val cleanUser = input.removePrefix("@")
                val apiRes = executeHttp("https://api.github.com/users/${URLEncoder.encode(cleanUser, "UTF-8")}", "GET", "", null)
                if (apiRes.code in 200..299) {
                    val obj = JSONObject(apiRes.body)
                    val login = obj.optString("login", cleanUser)
                    val name = obj.optString("name", login)
                    val avatar = obj.optString("avatar_url", "")
                    return@withContext Result.success(
                        GitCredentials(token = "", username = login, name = name, email = "", avatarUrl = avatar)
                    )
                } else if (apiRes.code == 404) {
                    return@withContext Result.failure(Exception("GitHub user '$cleanUser' does not exist."))
                }

                val webRes = executeWebGet("https://github.com/$cleanUser")
                if (webRes.code in 200..299) {
                    return@withContext Result.success(
                        GitCredentials(token = "", username = cleanUser, name = cleanUser, email = "", avatarUrl = "")
                    )
                }
                return@withContext Result.failure(Exception("No GitHub account found for '$cleanUser'."))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error verifying account: ${e.message ?: "Check internet connection"}"))
        }
    }

    // ================= ইউজার ভ্যালিডেশন ও তথ্য ফেচ =================
    suspend fun verifyTokenAndGetUser(token: String): Result<GitCredentials> = withContext(Dispatchers.IO) {
        try {
            val res = executeHttp("https://api.github.com/user", "GET", token, null)
            if (res.code in 200..299) {
                val json = JSONObject(res.body)
                val username = json.optString("login", "")
                val name = json.optString("name", username)
                val email = json.optString("email", "")
                val avatar = json.optString("avatar_url", "")
                Result.success(GitCredentials(token = token, username = username, name = name, email = email, avatarUrl = avatar))
            } else {
                Result.failure(Exception("Invalid Token (${extractGitHubErrorMessage(res.code, res.body)})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ================= ইউজারের রিপোজিটরি লিস্ট ফেচ =================
    suspend fun fetchUserRepositories(token: String, username: String = ""): Result<List<GitHubRepoItem>> = withContext(Dispatchers.IO) {
        try {
            val url = if (token.isNotBlank()) {
                "https://api.github.com/user/repos?per_page=100&sort=updated&affiliation=owner,collaborator"
            } else {
                "https://api.github.com/users/${URLEncoder.encode(username, "UTF-8")}/repos?per_page=100&sort=updated"
            }

            val res = executeHttp(url, "GET", token, null)
            if (res.code in 200..299) {
                val array = JSONArray(res.body)
                val list = ArrayList<GitHubRepoItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        GitHubRepoItem(
                            id = obj.optLong("id"),
                            name = obj.optString("name"),
                            fullName = obj.optString("full_name"),
                            description = obj.optString("description").takeIf { it != "null" && it.isNotBlank() },
                            htmlUrl = obj.optString("html_url"),
                            cloneUrl = obj.optString("clone_url"),
                            language = obj.optString("language").takeIf { it != "null" && it.isNotBlank() },
                            isFork = obj.optBoolean("fork", false),
                            isPrivate = obj.optBoolean("private", false),
                            updatedAt = obj.optString("updated_at"),
                            defaultBranch = obj.optString("default_branch", "main")
                        )
                    )
                }
                return@withContext Result.success(list)
            }

            if (token.isBlank() && username.isNotBlank() && (res.code == 403 || res.code == 429)) {
                val fallbackRepos = fetchRepositoriesFromWebHtml(username)
                if (fallbackRepos != null) {
                    return@withContext Result.success(fallbackRepos)
                }
            }

            Result.failure(Exception("Failed to fetch repositories (${extractGitHubErrorMessage(res.code, res.body)})"))
        } catch (e: Exception) {
            if (token.isBlank() && username.isNotBlank()) {
                val fallbackRepos = fetchRepositoriesFromWebHtml(username)
                if (fallbackRepos != null) {
                    return@withContext Result.success(fallbackRepos)
                }
            }
            Result.failure(e)
        }
    }

    private fun fetchRepositoriesFromWebHtml(username: String): List<GitHubRepoItem>? {
        return try {
            val webRes = executeWebGet("https://github.com/$username?tab=repositories")
            if (webRes.code !in 200..299) return null
            val html = webRes.body

            val liRegex = Regex("""<li[^>]*class="[^"]*source|fork[^"]*"[^>]*>(.*?)</li>""", RegexOption.DOT_MATCHES_ALL)
            val fallbackLiRegex = Regex("""<li[^>]*itemprop="owns"[^>]*>(.*?)</li>""", RegexOption.DOT_MATCHES_ALL)
            var blocks = fallbackLiRegex.findAll(html).map { it.groupValues[1] }.toList()
            if (blocks.isEmpty()) {
                blocks = liRegex.findAll(html).map { it.groupValues[1] }.toList()
            }

            val nameRegex = Regex("""href="/([^/"]+)/([^/"]+)"[^>]*itemprop="name codeRepository"|itemprop="name codeRepository"[^>]*href="/([^/"]+)/([^/"]+)"""")
            val descRegex = Regex("""<p[^>]*itemprop="description"[^>]*>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
            val langRegex = Regex("""<span[^>]*itemprop="programmingLanguage"[^>]*>([^<]+)</span>""")
            val timeRegex = Regex("""datetime="([^"]+)"""")

            val result = ArrayList<GitHubRepoItem>()
            var idSeq = 1L
            for (block in blocks) {
                val nm = nameRegex.find(block) ?: continue
                val owner = nm.groupValues[1].ifEmpty { nm.groupValues[3] }
                val repoName = nm.groupValues[2].ifEmpty { nm.groupValues[4] }
                if (owner.isEmpty() || repoName.isEmpty()) continue

                val rawDesc = descRegex.find(block)?.groupValues?.getOrNull(1)?.trim()
                val cleanDesc = rawDesc?.let {
                    @Suppress("DEPRECATION")
                    Html.fromHtml(it).toString().trim().takeIf { s -> s.isNotEmpty() }
                }
                val lang = langRegex.find(block)?.groupValues?.getOrNull(1)?.trim()
                val updated = timeRegex.find(block)?.groupValues?.getOrNull(1)?.trim() ?: ""
                val isFork = block.contains("Forked from", ignoreCase = true)

                result.add(
                    GitHubRepoItem(
                        id = idSeq++,
                        name = repoName,
                        fullName = "$owner/$repoName",
                        description = cleanDesc,
                        htmlUrl = "https://github.com/$owner/$repoName",
                        cloneUrl = "https://github.com/$owner/$repoName.git",
                        language = lang,
                        isFork = isFork,
                        isPrivate = false,
                        updatedAt = updated,
                        defaultBranch = "main"
                    )
                )
            }
            result
        } catch (_: Exception) {
            null
        }
    }

    // ================= নতুন GitHub রিপোজিটরি তৈরি (বিস্তারিত এরর পার্সিংসহ) =================
    suspend fun createRepository(
        token: String,
        repoName: String,
        description: String,
        isPrivate: Boolean,
        autoInit: Boolean
    ): Result<GitHubRepoItem> = withContext(Dispatchers.IO) {
        if (token.isBlank()) {
            return@withContext Result.failure(Exception("Personal Access Token (PAT) is required to create a repository."))
        }
        val cleanName = sanitizeRepoName(repoName)
        if (cleanName.isBlank()) {
            return@withContext Result.failure(Exception("Repository name cannot be empty"))
        }

        try {
            val payload = JSONObject().apply {
                put("name", cleanName)
                if (description.isNotBlank()) put("description", description.trim())
                put("private", isPrivate)
                put("auto_init", autoInit)
            }
            val res = executeHttp("https://api.github.com/user/repos", "POST", token, payload.toString())
            if (res.code in 200..299) {
                val obj = JSONObject(res.body)
                val created = GitHubRepoItem(
                    id = obj.optLong("id"),
                    name = obj.optString("name"),
                    fullName = obj.optString("full_name"),
                    description = obj.optString("description").takeIf { it != "null" && it.isNotBlank() },
                    htmlUrl = obj.optString("html_url"),
                    cloneUrl = obj.optString("clone_url"),
                    language = obj.optString("language").takeIf { it != "null" && it.isNotBlank() },
                    isFork = obj.optBoolean("fork", false),
                    isPrivate = obj.optBoolean("private", false),
                    updatedAt = obj.optString("updated_at"),
                    defaultBranch = obj.optString("default_branch", "main")
                )
                Result.success(created)
            } else {
                val detailedMsg = try {
                    val json = JSONObject(res.body)
                    val baseMsg = json.optString("message", "HTTP ${res.code}")
                    val errorsArr = json.optJSONArray("errors")
                    val subDetail = if (errorsArr != null && errorsArr.length() > 0) {
                        errorsArr.getJSONObject(0).optString("message", "")
                    } else ""

                    when {
                        subDetail.contains("already exists", ignoreCase = true) ->
                            "Repository '$cleanName' already exists on your GitHub account."
                        res.code == 401 ->
                            "Invalid or expired GitHub Token (401)."
                        res.code == 403 ->
                            "Token lacks permission to create repos (needs 'repo' scope)."
                        subDetail.isNotBlank() -> "$baseMsg ($subDetail)"
                        else -> "$baseMsg (HTTP ${res.code})"
                    }
                } catch (_: Exception) {
                    "HTTP ${res.code}"
                }
                Result.failure(Exception(detailedMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ================= GitHub রিপোজিটরি পার্মানেন্টলি ডিলিট =================
    suspend fun deleteRepository(
        token: String,
        owner: String,
        repoName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (token.isBlank()) {
            return@withContext Result.failure(Exception("Personal Access Token (PAT) with 'delete_repo' scope is required to delete."))
        }
        try {
            val url = "https://api.github.com/repos/$owner/$repoName"
            val res = executeHttp(url, "DELETE", token, null)
            if (res.code == 204 || res.code in 200..299) {
                Result.success(Unit)
            } else {
                val errMsg = try {
                    JSONObject(res.body).optString("message", "HTTP ${res.code}")
                } catch (_: Exception) {
                    "HTTP ${res.code}"
                }
                if (res.code == 403) {
                    Result.failure(Exception("Permission denied. Ensure your Token has 'delete_repo' scope checked on GitHub."))
                } else {
                    Result.failure(Exception("Failed to delete repository: $errMsg"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ================= CLONE GITHUB REPOSITORY (Cancel সাপোর্টসহ) =================
    suspend fun cloneRepository(
        context: Context,
        repoUrl: String,
        targetFolderName: String,
        branch: String,
        token: String,
        isCancelled: () -> Boolean = { false },
        onProgress: (ImportProgress) -> Unit
    ): GitActionResult = withContext(Dispatchers.IO) {
        val (owner, repoName) = parseGitHubUrl(repoUrl)
            ?: return@withContext GitActionResult(false, "Invalid GitHub repository URL")

        val targetBranch = branch.trim().ifEmpty { "main" }
        val projectName = targetFolderName.trim().ifEmpty { repoName }
        val destDir = getProjectDirectory(context, projectName)

        try {
            if (destDir.exists()) {
                destDir.deleteRecursively()
            }
            destDir.mkdirs()

            val downloadUrl = if (token.isNotBlank()) {
                "https://api.github.com/repos/$owner/$repoName/zipball/$targetBranch"
            } else {
                "https://github.com/$owner/$repoName/archive/refs/heads/$targetBranch.zip"
            }

            val conn = URL(downloadUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "CodeStudio-Android")
            if (token.isNotBlank()) {
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("Authorization", "Bearer $token")
            }
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000

            val code = conn.responseCode
            if (code !in 200..299) {
                destDir.deleteRecursively()
                return@withContext GitActionResult(false, "Failed to download repository (HTTP $code). Verify branch/access.")
            }

            val totalBytes = conn.contentLengthLong.coerceAtLeast(0L)
            var extractedBytes = 0L
            var filesCount = 0
            val buffer = ByteArray(131_072)

            BufferedInputStream(conn.inputStream, 131_072).use { bis ->
                ZipInputStream(bis).use { zis ->
                    var entry = zis.nextEntry
                    var rootPrefix: String? = null

                    while (entry != null) {
                        if (isCancelled()) {
                            destDir.deleteRecursively()
                            return@withContext GitActionResult(false, "Clone cancelled by user")
                        }

                        val name = entry.name.replace("\\", "/")
                        if (rootPrefix == null) {
                            rootPrefix = if (name.contains("/")) name.substringBefore("/") + "/" else ""
                        }
                        val relativeName = if (rootPrefix.isNotEmpty() && name.startsWith(rootPrefix)) {
                            name.removePrefix(rootPrefix)
                        } else {
                            name
                        }

                        if (relativeName.isNotEmpty()) {
                            val targetFile = File(destDir, relativeName)
                            if (entry.isDirectory) {
                                targetFile.mkdirs()
                            } else {
                                targetFile.parentFile?.mkdirs()
                                BufferedOutputStream(FileOutputStream(targetFile), 131_072).use { bos ->
                                    var r: Int
                                    while (zis.read(buffer).also { r = it } > 0) {
                                        if (isCancelled()) {
                                            bos.close()
                                            destDir.deleteRecursively()
                                            return@withContext GitActionResult(false, "Clone cancelled by user")
                                        }
                                        bos.write(buffer, 0, r)
                                        extractedBytes += r
                                        onProgress(
                                            ImportProgress(
                                                filesCount = filesCount,
                                                bytesCopied = extractedBytes,
                                                totalBytes = totalBytes,
                                                currentItem = relativeName.substringAfterLast("/")
                                            )
                                        )
                                    }
                                }
                                filesCount++
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            val projects = loadProjects(context)
            if (projects.none { it.name == projectName }) {
                saveProjects(context, projects + Project(projectName, "Android • Git Clone", androidx.compose.ui.graphics.Color(0xFFA855F7)))
            }
            GitStorage.saveRepoRemote(context, projectName, repoUrl, targetBranch)

            GitActionResult(true, "Cloned '$projectName' successfully!", "$filesCount files downloaded")
        } catch (e: Exception) {
            destDir.deleteRecursively()
            GitActionResult(false, "Clone failed: ${e.message ?: "Network error"}")
        }
    }

    // ================= অটোমেটিক PUSH + CLOUD CI BUILD পাইপলাইন (অটো-রিপো ক্রিয়েট বন্ধ) =================
    suspend fun autoPushAndBuild(
        context: Context,
        projectName: String,
        token: String,
        customRepoUrl: String = "",
        preferredBranch: String = "main",
        commitMessage: String = "",
        isPrivateRepo: Boolean = false,
        injectWorkflowIfMissing: Boolean = true,
        allowAutoCreate: Boolean = false,
        onLog: (String) -> Unit
    ): AutoPushBuildResult = withContext(Dispatchers.IO) {
        if (token.isBlank()) {
            return@withContext AutoPushBuildResult(false, message = "GitHub Personal Access Token (PAT) is required.")
        }

        val rootDir = getProjectDirectory(context, projectName)
        if (!rootDir.exists()) {
            return@withContext AutoPushBuildResult(false, message = "Local project directory not found.")
        }

        try {
            onLog("[1/6] Authenticating with GitHub...")
            val userRes = executeHttp("https://api.github.com/user", "GET", token, null)
            if (userRes.code !in 200..299) {
                return@withContext AutoPushBuildResult(
                    false,
                    message = "Authentication failed: ${extractGitHubErrorMessage(userRes.code, userRes.body)}. Check your PAT token."
                )
            }
            val authLogin = JSONObject(userRes.body).optString("login", "")
            if (authLogin.isBlank()) {
                return@withContext AutoPushBuildResult(false, message = "Could not resolve GitHub account username.")
            }
            onLog("Authenticated as '$authLogin'")

            val parsedCustom = if (customRepoUrl.isNotBlank()) parseGitHubUrl(customRepoUrl) else null
            val owner = parsedCustom?.first ?: authLogin
            val repoName = parsedCustom?.second ?: sanitizeRepoName(projectName)
            var targetBranch = preferredBranch.trim().ifEmpty { "main" }
            val repoWebUrl = "https://github.com/$owner/$repoName"

            onLog("[2/6] Checking repository '$owner/$repoName'...")
            val repoCheck = executeHttp("https://api.github.com/repos/$owner/$repoName", "GET", token, null)
            if (repoCheck.code == 404) {
                if (!allowAutoCreate) {
                    return@withContext AutoPushBuildResult(
                        false,
                        message = "Repository '$owner/$repoName' was not found on GitHub. Please create or link a repository from Settings first."
                    )
                }
                onLog("Repository not found. Creating '$repoName' on GitHub...")
                val createPayload = JSONObject().apply {
                    put("name", repoName)
                    put("description", "Android project built with CodeStudio IDE")
                    put("private", isPrivateRepo)
                    put("auto_init", false)
                }
                val createRes = executeHttp("https://api.github.com/user/repos", "POST", token, createPayload.toString())
                if (createRes.code !in 200..299) {
                    return@withContext AutoPushBuildResult(
                        false,
                        message = "Failed to create repository: ${extractGitHubErrorMessage(createRes.code, createRes.body)}"
                    )
                }
                onLog("Created repository '$owner/$repoName'.")
                delay(1000)
            } else if (repoCheck.code in 200..299) {
                val defBranch = JSONObject(repoCheck.body).optString("default_branch", targetBranch)
                if (preferredBranch.isBlank()) targetBranch = defBranch
                onLog("Repository verified ($owner/$repoName).")
            } else {
                return@withContext AutoPushBuildResult(
                    false,
                    message = "Cannot access '$owner/$repoName': ${extractGitHubErrorMessage(repoCheck.code, repoCheck.body)}"
                )
            }

            if (injectWorkflowIfMissing) {
                onLog("[3/6] Checking GitHub Actions CI workflow...")
                ensureAndroidWorkflowFile(rootDir, targetBranch, onLog)
            }

            onLog("[4/6] Scanning project files...")
            val filesToUpload = mutableListOf<File>()
            fun collect(dir: File) {
                dir.listFiles()?.forEach { f ->
                    if (f.isDirectory) {
                        if (f.name !in IGNORED_PUSH_DIRS) collect(f)
                    } else if (f.isFile && !f.name.endsWith(".saving.tmp") && f.length() <= 25L * 1024L * 1024L) {
                        filesToUpload.add(f)
                    }
                }
            }
            collect(rootDir)

            if (filesToUpload.isEmpty()) {
                return@withContext AutoPushBuildResult(false, message = "No files found in project to push.")
            }

            val headRefUrl = "https://api.github.com/repos/$owner/$repoName/git/refs/heads/$targetBranch"
            val headRefRes = executeHttp(headRefUrl, "GET", token, null)

            var headCommitSha: String? = null
            var baseTreeSha: String? = null
            var initializedRelPath: String? = null

            if (headRefRes.code in 200..299) {
                headCommitSha = JSONObject(headRefRes.body).getJSONObject("object").getString("sha")
                val commitRes = executeHttp("https://api.github.com/repos/$owner/$repoName/git/commits/$headCommitSha", "GET", token, null)
                if (commitRes.code in 200..299) {
                    baseTreeSha = JSONObject(commitRes.body).getJSONObject("tree").getString("sha")
                }
            } else {
                val allRefsRes = executeHttp("https://api.github.com/repos/$owner/$repoName/git/refs", "GET", token, null)
                val isRepoCompletelyEmpty = allRefsRes.code == 409 ||
                    allRefsRes.code == 404 ||
                    (allRefsRes.code in 200..299 && allRefsRes.body.trim() == "[]")

                if (isRepoCompletelyEmpty) {
                    onLog("Repository is empty. Initializing '$targetBranch' via Contents API...")
                    val seedFile = filesToUpload.firstOrNull {
                        !it.relativeTo(rootDir).path.replace("\\", "/").startsWith(".github/")
                    } ?: filesToUpload.first()
                    val seedRelPath = seedFile.relativeTo(rootDir).path.replace("\\", "/")
                    val encodedSeedPath = seedRelPath.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
                    val seedBase64 = Base64.encodeToString(seedFile.readBytes(), Base64.NO_WRAP)

                    val initPayload = JSONObject().apply {
                        put("message", "Initialize repository from CodeStudio")
                        put("content", seedBase64)
                        put("branch", targetBranch)
                    }

                    var initRes = executeHttp(
                        "https://api.github.com/repos/$owner/$repoName/contents/$encodedSeedPath",
                        "PUT",
                        token,
                        initPayload.toString()
                    )
                    if (initRes.code == 409 || initRes.code == 404) {
                        delay(800)
                        initRes = executeHttp(
                            "https://api.github.com/repos/$owner/$repoName/contents/$encodedSeedPath",
                            "PUT",
                            token,
                            initPayload.toString()
                        )
                    }

                    if (initRes.code !in 200..299) {
                        return@withContext AutoPushBuildResult(
                            false,
                            message = "Failed to initialize empty repository with '$seedRelPath': ${extractGitHubErrorMessage(initRes.code, initRes.body)}"
                        )
                    }

                    val initJson = JSONObject(initRes.body)
                    val commitObj = initJson.optJSONObject("commit")
                    headCommitSha = commitObj?.optString("sha", "")?.takeIf { it.isNotEmpty() }
                    baseTreeSha = commitObj?.optJSONObject("tree")?.optString("sha", "")?.takeIf { it.isNotEmpty() }

                    if (headCommitSha == null || baseTreeSha == null) {
                        delay(500)
                        val refAfterInit = executeHttp(headRefUrl, "GET", token, null)
                        if (refAfterInit.code in 200..299) {
                            headCommitSha = JSONObject(refAfterInit.body).getJSONObject("object").getString("sha")
                            val cRes = executeHttp("https://api.github.com/repos/$owner/$repoName/git/commits/$headCommitSha", "GET", token, null)
                            if (cRes.code in 200..299) {
                                baseTreeSha = JSONObject(cRes.body).getJSONObject("tree").getString("sha")
                            }
                        }
                    }

                    initializedRelPath = seedRelPath
                    onLog("Initialized '$targetBranch' with '$seedRelPath'.")
                } else {
                    onLog("Branch '$targetBranch' is new. Creating branch commit tree...")
                }
            }

            val remainingFiles = if (initializedRelPath != null && filesToUpload.size > 1) {
                filesToUpload.filter { it.relativeTo(rootDir).path.replace("\\", "/") != initializedRelPath }
            } else if (initializedRelPath != null && filesToUpload.size == 1 && headCommitSha != null) {
                emptyList()
            } else {
                filesToUpload
            }

            val finalCommitSha: String
            if (remainingFiles.isEmpty() && headCommitSha != null) {
                finalCommitSha = headCommitSha
                onLog("[5/6] All project files committed on '$targetBranch'.")
            } else {
                val treeArray = JSONArray()
                val totalFiles = remainingFiles.size
                for (idx in remainingFiles.indices) {
                    val f = remainingFiles[idx]
                    val relPath = f.relativeTo(rootDir).path.replace("\\", "/")
                    if (idx == 0 || (idx + 1) % 5 == 0 || idx == totalFiles - 1) {
                        onLog("Uploading blobs (${idx + 1}/$totalFiles): ${f.name}")
                    }

                    val bytes = f.readBytes()
                    val base64Content = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val blobPayload = JSONObject().apply {
                        put("content", base64Content)
                        put("encoding", "base64")
                    }
                    val blobRes = executeHttp("https://api.github.com/repos/$owner/$repoName/git/blobs", "POST", token, blobPayload.toString())
                    if (blobRes.code !in 200..299) {
                        return@withContext AutoPushBuildResult(
                            false,
                            message = "Failed uploading '$relPath': ${extractGitHubErrorMessage(blobRes.code, blobRes.body)}"
                        )
                    }
                    val blobSha = JSONObject(blobRes.body).getString("sha")
                    val fileMode = if (relPath == "gradlew" || relPath.endsWith(".sh")) "100755" else "100644"
                    treeArray.put(JSONObject().apply {
                        put("path", relPath)
                        put("mode", fileMode)
                        put("type", "blob")
                        put("sha", blobSha)
                    })
                }

                onLog("[5/6] Creating Git Tree & Atomic Commit...")
                val treePayload = JSONObject().apply {
                    if (baseTreeSha != null) {
                        put("base_tree", baseTreeSha)
                    }
                    put("tree", treeArray)
                }
                val treeRes = executeHttp("https://api.github.com/repos/$owner/$repoName/git/trees", "POST", token, treePayload.toString())
                if (treeRes.code !in 200..299) {
                    return@withContext AutoPushBuildResult(
                        false,
                        message = "Failed creating Git Tree: ${extractGitHubErrorMessage(treeRes.code, treeRes.body)}"
                    )
                }
                val newTreeSha = JSONObject(treeRes.body).getString("sha")

                val finalCommitMsg = commitMessage.ifBlank { "Build & update $projectName from CodeStudio" }
                val newCommitPayload = JSONObject().apply {
                    put("message", finalCommitMsg)
                    put("tree", newTreeSha)
                    if (headCommitSha != null) {
                        put("parents", JSONArray().put(headCommitSha))
                    } else {
                        put("parents", JSONArray())
                    }
                }
                val newCommitRes = executeHttp("https://api.github.com/repos/$owner/$repoName/git/commits", "POST", token, newCommitPayload.toString())
                if (newCommitRes.code !in 200..299) {
                    return@withContext AutoPushBuildResult(
                        false,
                        message = "Failed creating Commit: ${extractGitHubErrorMessage(newCommitRes.code, newCommitRes.body)}"
                    )
                }
                finalCommitSha = JSONObject(newCommitRes.body).getString("sha")

                if (headCommitSha != null) {
                    val updateRefUrl = "https://api.github.com/repos/$owner/$repoName/git/refs/heads/$targetBranch"
                    val pushPayload = JSONObject().apply {
                        put("sha", finalCommitSha)
                        put("force", true)
                    }
                    val pushRes = executeHttp(updateRefUrl, "PATCH", token, pushPayload.toString())
                    if (pushRes.code !in 200..299) {
                        val errDetails = extractGitHubErrorMessage(pushRes.code, pushRes.body)
                        val hint = if (errDetails.contains("workflow", ignoreCase = true)) {
                            " Make sure your GitHub Token (PAT) has the 'workflow' scope enabled!"
                        } else ""
                        return@withContext AutoPushBuildResult(false, message = "Push failed: $errDetails.$hint")
                    }
                } else {
                    val createRefUrl = "https://api.github.com/repos/$owner/$repoName/git/refs"
                    val createPayload = JSONObject().apply {
                        put("ref", "refs/heads/$targetBranch")
                        put("sha", finalCommitSha)
                    }
                    val createRefRes = executeHttp(createRefUrl, "POST", token, createPayload.toString())
                    if (createRefRes.code !in 200..299) {
                        val errDetails = extractGitHubErrorMessage(createRefRes.code, createRefRes.body)
                        val hint = if (errDetails.contains("workflow", ignoreCase = true)) {
                            " Make sure your GitHub Token (PAT) has the 'workflow' scope enabled!"
                        } else ""
                        return@withContext AutoPushBuildResult(false, message = "Create branch failed: $errDetails.$hint")
                    }
                }

                onLog("Pushed ${filesToUpload.size} files to $owner/$repoName ($targetBranch) [${finalCommitSha.take(7)}]")
            }

            GitStorage.saveRepoRemote(context, projectName, repoWebUrl, targetBranch)

            onLog("[6/6] Triggering GitHub Actions Cloud APK Build...")
            delay(2500)
            val existingRun = getLatestWorkflowRun(token, owner, repoName, targetBranch).getOrNull()
            if (existingRun == null || (existingRun.status != "queued" && existingRun.status != "in_progress")) {
                val dispatchUrl = "https://api.github.com/repos/$owner/$repoName/actions/workflows/android.yml/dispatches"
                executeHttp(dispatchUrl, "POST", token, JSONObject().apply { put("ref", targetBranch) }.toString())
            }

            AutoPushBuildResult(
                success = true,
                owner = owner,
                repoName = repoName,
                branch = targetBranch,
                commitSha = finalCommitSha,
                repoHtmlUrl = repoWebUrl,
                message = "Pushed & Build Triggered!"
            )
        } catch (e: Exception) {
            AutoPushBuildResult(false, message = "Pipeline error: ${e.message ?: "Network error"}")
        }
    }

    private fun ensureAndroidWorkflowFile(rootDir: File, targetBranch: String, onLog: (String) -> Unit) {
        val workflowDir = File(rootDir, ".github/workflows")
        val workflowFile = File(workflowDir, "android.yml")
        if (workflowFile.exists()) {
            onLog("Existing '.github/workflows/android.yml' found.")
            return
        }
        val branchesList = listOf(targetBranch, "main", "master").distinct().joinToString(", ")
        workflowDir.mkdirs()
        workflowFile.writeText(
            """
            name: Build Android APK

            on:
              push:
                branches: [ $branchesList ]
              workflow_dispatch:

            jobs:
              build:
                runs-on: ubuntu-latest
                steps:
                  - name: Checkout Repository
                    uses: actions/checkout@v4

                  - name: Set up JDK 17
                    uses: actions/setup-java@v4
                    with:
                      java-version: '17'
                      distribution: 'temurin'

                  - name: Setup Gradle & Permissions
                    run: |
                      if [ -f "./gradlew" ]; then
                        chmod +x gradlew
                      fi

                  - name: Build Debug APK
                    run: |
                      if [ -f "./gradlew" ] && [ -f "./gradle/wrapper/gradle-wrapper.jar" ]; then
                        ./gradlew assembleDebug --no-daemon
                      else
                        gradle assembleDebug --no-daemon
                      fi

                  - name: Upload Debug APK
                    uses: actions/upload-artifact@v4
                    with:
                      name: app-debug-apk
                      path: app/build/outputs/apk/debug/*.apk
                      if-no-files-found: warn
            """.trimIndent() + "\n"
        )
        onLog("Generated '.github/workflows/android.yml' for automated APK build.")
    }

    // ================= সর্বশেষ GitHub Actions Workflow Run স্ট্যাটাস পোলিং =================
    suspend fun getLatestWorkflowRun(
        token: String,
        owner: String,
        repoName: String,
        branch: String
    ): Result<GitHubWorkflowRunInfo?> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$owner/$repoName/actions/runs?branch=${URLEncoder.encode(branch, "UTF-8")}&per_page=1"
            val res = executeHttp(url, "GET", token, null)
            if (res.code !in 200..299) {
                return@withContext Result.failure(Exception("HTTP ${res.code}"))
            }
            val runs = JSONObject(res.body).optJSONArray("workflow_runs")
            if (runs == null || runs.length() == 0) {
                return@withContext Result.success(null)
            }
            val latest = runs.getJSONObject(0)
            Result.success(
                GitHubWorkflowRunInfo(
                    id = latest.optLong("id"),
                    status = latest.optString("status", "queued"),
                    conclusion = latest.optString("conclusion").takeIf { it != "null" && it.isNotBlank() },
                    htmlUrl = latest.optString("html_url", "https://github.com/$owner/$repoName/actions")
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ================= GitHub Actions থেকে APK আর্টিফ্যাক্ট সরাসরি ডাউনলোড =================
    suspend fun downloadWorkflowApk(
        context: Context,
        token: String,
        owner: String,
        repoName: String,
        runId: Long
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val artifactsUrl = "https://api.github.com/repos/$owner/$repoName/actions/runs/$runId/artifacts"
            val artRes = executeHttp(artifactsUrl, "GET", token, null)
            if (artRes.code !in 200..299) {
                return@withContext Result.failure(Exception("Failed to fetch artifacts (HTTP ${artRes.code})"))
            }
            val artifacts = JSONObject(artRes.body).optJSONArray("artifacts")
            if (artifacts == null || artifacts.length() == 0) {
                return@withContext Result.failure(Exception("No APK artifact found for this build run."))
            }

            val downloadApiUrl = artifacts.getJSONObject(0).getString("archive_download_url")

            val conn = URL(downloadApiUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "CodeStudio-Android")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 20_000
            conn.readTimeout = 30_000

            val code = conn.responseCode
            val redirectUrl = if (code in 300..399) {
                conn.getHeaderField("Location")
            } else if (code in 200..299) {
                downloadApiUrl
            } else {
                return@withContext Result.failure(Exception("Artifact download failed (HTTP $code)"))
            }

            if (redirectUrl.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Missing download redirect URL"))
            }

            val dlConn = URL(redirectUrl).openConnection() as HttpURLConnection
            dlConn.requestMethod = "GET"
            dlConn.connectTimeout = 20_000
            dlConn.readTimeout = 90_000

            if (dlConn.responseCode !in 200..299) {
                return@withContext Result.failure(Exception("Storage download failed (HTTP ${dlConn.responseCode})"))
            }

            val outApkName = "${repoName}-debug.apk"
            val buffer = ByteArray(131_072)
            var savedLocation = ""
            var extractedApk = false

            ZipInputStream(BufferedInputStream(dlConn.inputStream, 131_072)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                        savedLocation = saveStreamToDownloadsApks(context, outApkName) { outStream ->
                            BufferedOutputStream(outStream, 131_072).use { bos ->
                                var r: Int
                                while (zis.read(buffer).also { r = it } > 0) {
                                    bos.write(buffer, 0, r)
                                }
                            }
                        }
                        extractedApk = savedLocation.isNotEmpty()
                        break
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (extractedApk) {
                Result.success(savedLocation)
            } else {
                Result.failure(Exception("APK file not found inside artifact zip."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun saveStreamToDownloadsApks(
        context: Context,
        fileName: String,
        writer: (java.io.OutputStream) -> Unit
    ): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CodeStudio_APKs")
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return ""
            resolver.openOutputStream(uri)?.use { writer(it) }
            "Downloads/CodeStudio_APKs/$fileName"
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CodeStudio_APKs")
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, fileName)
            FileOutputStream(outFile).use { writer(it) }
            outFile.absolutePath
        }
    }

    suspend fun pushProject(
        context: Context,
        projectName: String,
        repoUrl: String,
        branch: String,
        commitMessage: String,
        token: String,
        onProgress: (status: String) -> Unit
    ): GitActionResult {
        val res = autoPushAndBuild(
            context = context,
            projectName = projectName,
            token = token,
            customRepoUrl = repoUrl,
            preferredBranch = branch,
            commitMessage = commitMessage,
            isPrivateRepo = false,
            injectWorkflowIfMissing = false,
            allowAutoCreate = false,
            onLog = onProgress
        )
        return GitActionResult(res.success, res.message, res.commitSha.take(7))
    }

    suspend fun triggerWorkflowBuild(
        repoUrl: String,
        branch: String,
        token: String
    ): GitActionResult = withContext(Dispatchers.IO) {
        val (owner, repoName) = parseGitHubUrl(repoUrl)
            ?: return@withContext GitActionResult(false, "Invalid repository URL")
        if (token.isBlank()) {
            return@withContext GitActionResult(false, "GitHub Token is required for cloud build")
        }

        try {
            val targetBranch = branch.trim().ifEmpty { "main" }
            val dispatchUrl = "https://api.github.com/repos/$owner/$repoName/actions/workflows/android.yml/dispatches"
            val payload = JSONObject().apply { put("ref", targetBranch) }
            val res = executeHttp(dispatchUrl, "POST", token, payload.toString())
            if (res.code in 200..299 || res.code == 204) {
                GitActionResult(true, "GitHub Build Triggered!", "Check GitHub Actions tab in repository")
            } else {
                GitActionResult(false, "Cloud build trigger failed (${extractGitHubErrorMessage(res.code, res.body)}).")
            }
        } catch (e: Exception) {
            GitActionResult(false, "Build request error: ${e.message}")
        }
    }

    private data class HttpResponse(val code: Int, val body: String)

    private fun executeHttp(urlStr: String, method: String, token: String, jsonBody: String?): HttpResponse {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "CodeStudio-Android")
        if (token.isNotBlank()) {
            conn.setRequestProperty("Authorization", "Bearer $token")
        }
        conn.connectTimeout = 20_000
        conn.readTimeout = 45_000

        if (jsonBody != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(jsonBody) }
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.let {
            BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
        } ?: ""

        return HttpResponse(code, body)
    }

    private fun executeWebGet(urlStr: String): HttpResponse {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile) CodeStudio")
        conn.connectTimeout = 20_000
        conn.readTimeout = 40_000
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.let {
            BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
        } ?: ""
        return HttpResponse(code, body)
    }
}