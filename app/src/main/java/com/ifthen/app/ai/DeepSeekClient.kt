package com.ifthen.app.ai

import com.ifthen.app.data.ApiSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class AiCandidate(val ifText: String, val thenText: String)

class DeepSeekClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    /** 困扰描述 -> 3~5 条候选预案；只返回候选，由用户确认后入库 */
    fun generateCandidates(settings: ApiSettings, concern: String): List<AiCandidate> {
        require(settings.apiKey.isNotBlank()) { "请先在设置页填写 API Key" }
        val body = buildString {
            append("""{"model":${jsonStr(settings.model)},"temperature":0.8,"messages":""")
            append("""[{"role":"system","content":${jsonStr(SYSTEM_PROMPT)}},""")
            append("""{"role":"user","content":${jsonStr(concern)}}]}""")
            append("}")
        }
        val request = Request.Builder()
            .url(settings.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer ${settings.apiKey}")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: error("空响应")
            if (!resp.isSuccessful) error("接口错误 ${resp.code}: ${text.take(200)}")
            val content = json.parseToJsonElement(text).jsonObject
                .getValue("choices").jsonArray.first().jsonObject
                .getValue("message").jsonObject
                .getValue("content").jsonPrimitive.content
            return parseCandidates(content)
        }
    }

    private fun parseCandidates(content: String): List<AiCandidate> {
        val cleaned = content.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val arr = cleaned.substringAfter('[').substringBeforeLast(']')
        val items = arr.split("\n").mapNotNull { line ->
            val obj = line.substringAfter('{').substringBeforeLast('}').let { "{$it}" }
                .takeIf { it.contains("\"if\"") && it.contains("\"then\"") } ?: return@mapNotNull null
            runCatching {
                val el = json.parseToJsonElement(obj).jsonObject
                AiCandidate(
                    ifText = el["if"]?.jsonPrimitive?.content?.removeSurrounding("\"") ?: "",
                    thenText = el["then"]?.jsonPrimitive?.content?.removeSurrounding("\"") ?: ""
                )
            }.getOrNull()
        }.filter { it.ifText.isNotBlank() && it.thenText.isNotBlank() }
        if (items.isEmpty()) error("模型没有返回有效预案，请重试")
        return items
    }

    private fun jsonStr(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> sb.append(c)
        }
        return sb.append('"').toString()
    }

    companion object {
        private const val SYSTEM_PROMPT =
            "你是行为心理学（implementation intentions，if-then 预案）助手。" +
                "用户描述一个困扰，你给出 3 到 5 条候选预案。每条必须是：" +
                "if=具体可觉察的触发情境（时间/地点/情绪/动作），then=立刻可做的一件小事（不超过20字，具体、低门槛）。" +
                "只输出 JSON 数组，每行一个对象，不要任何其他文字。格式：" +
                """[{"if":"当我感到...","then":"我就..."}]"""
    }
}
