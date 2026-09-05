package com.learnsypro.app.data

import com.learnsypro.app.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * ── UpstashClient (thay window.upstashCmd trong bản web) ──
 *
 * Bản web gọi qua proxy Cloudflare Function /api/cache để giấu Upstash token.
 * Vì bản Android đã bỏ tầng Cloudflare KV theo yêu cầu, ta gọi thẳng Upstash
 * REST API — Upstash hỗ trợ REST API có sẵn cơ chế Bearer token, tương tự
 * cách Supabase anon key hoạt động: token này có quyền giới hạn theo config
 * Upstash, không phải secret toàn quyền như DB password.
 *
 * Cần thêm vào local.properties:
 *   UPSTASH_URL=https://xxxx.upstash.io
 *   UPSTASH_TOKEN=xxxxxxx
 *
 * Và build.gradle.kts (app module) — buildConfigField tương tự SUPA_URL/SUPA_KEY.
 */
class UpstashClient {

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private val baseUrl = BuildConfig.UPSTASH_URL
    private val token = BuildConfig.UPSTASH_TOKEN

    @Serializable
    private data class UpstashResult(val result: String? = null)

    suspend fun get(key: String): String? = try {
        val response: UpstashResult = client.get("$baseUrl/get/$key") {
            header("Authorization", "Bearer $token")
        }.body()
        response.result
    } catch (e: Exception) {
        null
    }

    suspend fun set(key: String, value: String, expireSeconds: Long? = null): Boolean = try {
        val url = if (expireSeconds != null) {
            "$baseUrl/set/$key/$value/EX/$expireSeconds"
        } else {
            "$baseUrl/set/$key/$value"
        }
        client.get(url) {
            header("Authorization", "Bearer $token")
        }
        true
    } catch (e: Exception) {
        false
    }

    suspend fun delete(key: String): Boolean = try {
        client.get("$baseUrl/del/$key") {
            header("Authorization", "Bearer $token")
        }
        true
    } catch (e: Exception) {
        false
    }
}
