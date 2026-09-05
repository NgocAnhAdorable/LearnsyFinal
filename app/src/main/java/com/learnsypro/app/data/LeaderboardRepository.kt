package com.learnsypro.app.data

import com.learnsypro.app.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LeaderboardEntry(
    val displayName: String,
    val totalXp: Int,
    val rank: Int,
    val isSelf: Boolean = false
)

@Serializable
private data class LeaderboardRequestBody(val studentId: String)

@Serializable
private data class LeaderboardResponseBody(
    val ok: Boolean,
    val msg: String? = null,
    val className: String? = null,
    val entries: List<LeaderboardEntry>? = null
)

sealed class LeaderboardResult {
    data class Success(val className: String, val entries: List<LeaderboardEntry>) : LeaderboardResult()
    data class Failure(val message: String) : LeaderboardResult()
}

/**
 * ── LeaderboardRepository ──
 * Gọi Edge Function `class-leaderboard` — cùng phong cách AuthRepository:
 * client CHỈ gửi studentId, server (service role) tự tra lớp + cộng XP +
 * trả về danh sách đã ẩn danh (không username/id thật). Không tự query
 * students/quiz_results chéo học sinh bằng anon key ở đây, vì RLS hiện tại
 * chỉ cho phép mỗi học sinh đọc đúng dòng của chính mình.
 */
class LeaderboardRepository {

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private val endpoint = "${BuildConfig.SUPA_URL}/functions/v1/class-leaderboard"

    suspend fun fetchClassLeaderboard(studentId: String): LeaderboardResult {
        if (studentId.isBlank()) return LeaderboardResult.Failure("Thiếu thông tin học sinh")
        return try {
            val response: LeaderboardResponseBody = client.post(endpoint) {
                contentType(ContentType.Application.Json)
                header("apikey", BuildConfig.SUPA_KEY)
                header("Authorization", "Bearer ${BuildConfig.SUPA_KEY}")
                setBody(LeaderboardRequestBody(studentId))
            }.body()

            if (response.ok && response.entries != null && response.className != null) {
                LeaderboardResult.Success(response.className, response.entries)
            } else {
                LeaderboardResult.Failure(response.msg ?: "Không tải được bảng xếp hạng")
            }
        } catch (e: Exception) {
            LeaderboardResult.Failure("Lỗi kết nối, thử lại nhé!")
        }
    }
}
