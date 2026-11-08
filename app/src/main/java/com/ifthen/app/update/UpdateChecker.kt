package com.ifthen.app.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** 一次检查更新的结果 */
data class UpdateInfo(
    val latestVersion: String, // 归一化后，如 "0.13.0"
    val tagName: String,       // 原始标签，如 "v0.13"
    val releaseUrl: String,    // Release 页面（去下载）
    val notes: String,         // Release 说明
    val isNewer: Boolean       // 最新版是否比当前新
)

/**
 * 查 GitHub 最新 Release 与当前版本比较。仓库是公开的，无需鉴权；
 * 未登录调用有 60 次/小时的限流，个人手动点「检查更新」足够。
 */
class UpdateChecker(private val repo: String = "heeyezzz/if-then") {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    fun check(currentVersion: String): UpdateInfo {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Bugu-IfThen-Android")
            .get()
            .build()
        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: error("空响应")
            if (!resp.isSuccessful) error("HTTP ${resp.code}：${text.take(150)}")
            val o = json.parseToJsonElement(text).jsonObject
            val tag = o["tag_name"]?.jsonPrimitive?.content?.trim().orEmpty()
            val htmlUrl = o["html_url"]?.jsonPrimitive?.content.orEmpty()
            val body = o["body"]?.jsonPrimitive?.content.orEmpty()
            if (tag.isBlank()) error("未取到最新版本号")
            val latest = normalize(tag)
            return UpdateInfo(
                latestVersion = latest,
                tagName = tag,
                releaseUrl = htmlUrl,
                notes = body,
                isNewer = compareVersions(latest, normalize(currentVersion)) > 0
            )
        }
    }

    companion object {
        /** 去掉前缀 v/V，只留版本数字 */
        fun normalize(v: String): String = v.trim().removePrefix("v").removePrefix("V").trim()

        /** 逐段按数字比较：a 比 b 新返回正数，相同为 0，更旧为负数；缺失段按 0 处理 */
        fun compareVersions(a: String, b: String): Int {
            fun parts(s: String) = s.split('.', '-', '+')
                .map { seg -> seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
            val pa = parts(a)
            val pb = parts(b)
            val n = maxOf(pa.size, pb.size)
            for (i in 0 until n) {
                val x = pa.getOrElse(i) { 0 }
                val y = pb.getOrElse(i) { 0 }
                if (x != y) return x - y
            }
            return 0
        }
    }
}
