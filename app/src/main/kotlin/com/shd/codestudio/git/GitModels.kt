package com.shd.codestudio.git

import android.content.Context

data class GitCredentials(
    val token: String = "",
    val username: String = "",
    val name: String = "",
    val email: String = "",
    val avatarUrl: String = ""
)

data class GitHubRepoItem(
    val id: Long,
    val name: String,
    val fullName: String,
    val description: String?,
    val htmlUrl: String,
    val cloneUrl: String,
    val language: String?,
    val isFork: Boolean,
    val isPrivate: Boolean,
    val updatedAt: String,
    val defaultBranch: String
)

data class GitActionResult(
    val success: Boolean,
    val message: String,
    val detail: String = ""
)

object GitStorage {
    private const val PREFS_NAME = "CodeStudioGitPrefs"
    private const val KEY_TOKEN = "github_token"
    private const val KEY_USERNAME = "github_username"
    private const val KEY_NAME = "github_name"
    private const val KEY_EMAIL = "github_email"
    private const val KEY_AVATAR = "github_avatar"

    fun saveCredentials(context: Context, creds: GitCredentials) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_TOKEN, creds.token.trim())
            .putString(KEY_USERNAME, creds.username.trim())
            .putString(KEY_NAME, creds.name.trim())
            .putString(KEY_EMAIL, creds.email.trim())
            .putString(KEY_AVATAR, creds.avatarUrl.trim())
            .apply()
    }

    fun loadCredentials(context: Context): GitCredentials {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return GitCredentials(
            token = prefs.getString(KEY_TOKEN, "") ?: "",
            username = prefs.getString(KEY_USERNAME, "") ?: "",
            name = prefs.getString(KEY_NAME, "") ?: "",
            email = prefs.getString(KEY_EMAIL, "") ?: "",
            avatarUrl = prefs.getString(KEY_AVATAR, "") ?: ""
        )
    }

    fun clearCredentials(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    fun saveRepoRemote(context: Context, projectName: String, remoteUrl: String, branch: String) {
        val prefs = context.getSharedPreferences("CodeStudioGitRepo_$projectName", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("remote_url", remoteUrl.trim())
            .putString("branch", branch.trim())
            .apply()
    }

    fun loadRepoRemote(context: Context, projectName: String): Pair<String, String> {
        val prefs = context.getSharedPreferences("CodeStudioGitRepo_$projectName", Context.MODE_PRIVATE)
        val url = prefs.getString("remote_url", "") ?: ""
        val branch = prefs.getString("branch", "main") ?: "main"
        return url to branch
    }
}