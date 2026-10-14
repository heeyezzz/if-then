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

/** AI 复盘：一句总结 + 若干条建议，rule 是发出去的那份清单里的序号（1 起），由调用方换回预案 id */
data class ReviewInsight(val summary: String, val suggestions: List<ReviewSuggestion>)
data class ReviewSuggestion(val text: String, val rule: Int)

class DeepSeekClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    /** 用 GET /models 验证地址与 key，不消耗 token；返回可见模型数 */
    fun testConnection(settings: ApiSettings): Int {
        require(settings.apiKey.isNotBlank()) { "API Key 为空" }
        val request = Request.Builder()
            .url(settings.baseUrl.trimEnd('/') + "/models")
            .header("Authorization", "Bearer ${settings.apiKey}")
            .get()
            .build()
        http.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: error("空响应")
            if (!resp.isSuccessful) error("HTTP ${resp.code}: ${text.take(150)}")
            return json.parseToJsonElement(text).jsonObject
                .getValue("data").jsonArray.size
        }
    }

    /** 困扰描述 -> 3~5 条候选预案；只返回候选，由用户确认后入库 */
    fun generateCandidates(settings: ApiSettings, concern: String): List<AiCandidate> {
        val content = chat(settings, SYSTEM_PROMPT, concern)
        return parseCandidates(content)
    }

    /** 复盘点评：输入是调用方拼好的数据描述，输出一句总结加若干条建议 */
    fun reviewSummary(settings: ApiSettings, payload: String): ReviewInsight {
        val content = chat(settings, REVIEW_PROMPT, payload)
        val cleaned = content.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) error("模型没有返回有效点评，请重试")
        val o = json.parseToJsonElement(cleaned.substring(start, end + 1)).jsonObject
        val summary = o["summary"]?.jsonPrimitive?.content?.trim().orEmpty()
        val suggestions = o["suggestions"]?.jsonArray.orEmpty().mapNotNull { el ->
            runCatching {
                val s = el.jsonObject
                ReviewSuggestion(
                    text = s["text"]?.jsonPrimitive?.content?.trim().orEmpty(),
                    rule = s["rule"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                )
            }.getOrNull()
        }.filter { it.text.isNotBlank() }
        if (summary.isBlank()) error("模型没有返回有效点评，请重试")
        return ReviewInsight(summary, suggestions.take(2))
    }

    private fun chat(settings: ApiSettings, systemPrompt: String, userText: String): String {
        require(settings.apiKey.isNotBlank()) { "请先在设置页填写 API Key" }
        val body = buildJsonObject {
            put("model", settings.model)
            put("temperature", 0.8)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", userText)
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
            return json.parseToJsonElement(text).jsonObject
                .getValue("choices").jsonArray.first().jsonObject
                .getValue("message").jsonObject
                .getValue("content").jsonPrimitive.content
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

        private const val REVIEW_PROMPT =
            "你是 if-then 习惯预案的复盘助手。用户会给你一段他自己的执行数据：" +
                "编号预案清单，以及带时间的打卡流水。你的任务是像教练一样指出下一步该动哪里。" +
                "规则：summary 不超过 40 字，说一个具体判断，不要空泛鼓励、不要复述数字；" +
                "suggestions 最多 2 条，每条不超过 30 字，必须是「改掉/拆开/换掉某一步」这类能立刻动手的动作，" +
                "rule 填该建议针对的预案编号（必须是清单里存在的编号），针对链条整体就填 0；" +
                "数据少的时候就直说样本不够，不要编造结论。" +
                "只输出 JSON，不要任何其他文字。格式：" +
                """{"summary":"...","suggestions":[{"text":"...","rule":3}]}"""
    }
}
