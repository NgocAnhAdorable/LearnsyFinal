package com.learnsypro.app.admin.cache

// BuildConfig thuộc namespace THẬT của module app (com.learnsypro.app) — UPSTASH_URL/
// UPSTASH_TOKEN đã có sẵn field cùng tên trong BuildConfig chung (LearnsyPro khai báo
// riêng cho ux-nung.jsx cache trước đây), không cần khai lại.
import com.learnsypro.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

// Tương đương cache.js: proxy Upstash Redis REST.
// Native gọi thẳng UPSTASH_URL, không cần Cloudflare Function ở giữa
// (đó tồn tại để giấu UPSTASH_TOKEN khỏi client web; native token nằm
// trong BuildConfig, không lộ qua network tab của người dùng).

object UpstashCache {
    private const val LESSONS_CACHE_KEY = "lessons_cache"

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json".toMediaType()

    private suspend fun cmd(vararg args: String): String? = withContext(Dispatchers.IO) {
        try {
            val body = json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()),
                args.toList()
            ).toRequestBody(jsonMedia)

            val req = Request.Builder()
                .url(BuildConfig.UPSTASH_URL)
                .header("Authorization", "Bearer ${BuildConfig.UPSTASH_TOKEN}")
                .post(body)
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val text = resp.body?.string() ?: return@withContext null
                val el: JsonElement = json.parseToJsonElement(text)
                el.jsonObjectOrNull()?.get("result")?.jsonPrimitive?.contentOrNull
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun JsonElement.jsonObjectOrNull() =
        try { this as? kotlinx.serialization.json.JsonObject } catch (e: Exception) { null }

    suspend fun get(key: String): String? = cmd("GET", key)

    suspend fun set(key: String, value: String): Boolean = cmd("SET", key, value) != null

    suspend fun del(key: String): Boolean = cmd("DEL", key) != null

    // Tương đương invalidateCache() trong ux-nung.jsx
    suspend fun invalidateLessonsCache() = del(LESSONS_CACHE_KEY)

    suspend fun getLessonsCache(): String? = get(LESSONS_CACHE_KEY)

    suspend fun setLessonsCache(value: String): Boolean = set(LESSONS_CACHE_KEY, value)
}
