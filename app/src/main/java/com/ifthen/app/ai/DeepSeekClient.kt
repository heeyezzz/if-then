package com.ifthen.app.ai

import com.ifthen.app.data.ApiSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class AiCandidate(val ifText: String, val thenText: String)

class DeepSeekClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    /** 困扰描述 -> 3~5 条候选预案；只返回候选，由用户确认后入库 */
    fun generateCandidates(settings: ApiSettings, concern: String): List<AiCandidate> {
        require(settings.apiKey.isNotBlank()) { "请先在设置页填写 API Key" }
        val body = buildJsonObject {
            put("model", settings.model)
            put("temperature", 0.8)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", concern)
                }
            }
        }.toString()
        val request = Request.Builder()
            .url(settings.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer ${settings.apiKey}")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: error("空响应")
            if (!resp.isSuccessful) error("接口错误 ${resp.code}（模型 ${settings.model} 是否有效？）: ${text.take(200)}")
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
        val start = cleaned.indexOf('[')
        val end = cleaned.lastIndexOf(']')
        if (start < 0 || end <= start) error("模型没有返回有效预案，请重试")
        val items = json.parseToJsonElement(cleaned.substring(start, end + 1)).jsonArray
            .mapNotNull { el ->
                runCatching {
                    val o = el.jsonObject
                    AiCandidate(
                        ifText = o["if"]?.jsonPrimitive?.content ?: "",
                        thenText = o["then"]?.jsonPrimitive?.content ?: ""
                    )
                }.getOrNull()
            }
            .filter { it.ifText.isNotBlank() && it.thenText.isNotBlank() }
        if (items.isEmpty()) error("模型没有返回有效预案，请重试")
        return items
    }

    companion object {
        private const val SYSTEM_PROMPT =
            "你是行为心理学（implementation intentions，if-then 预案）助手。" +
                "用户描述一个困扰，你给出 3 到 5 条候选预案。每条必须是：" +
                "if=具体可觉察的触发情境（时间/地点/情绪/动作），then=立刻可做的一件小事（不超过20字，具体、低门槛）。" +
                "用中文回答。只输出 JSON 数组，不要任何其他文字。格式：" +
                """[{"if":"当我感到...","then":"我就..."}]"""
    }
}
