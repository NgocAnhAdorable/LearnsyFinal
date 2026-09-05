package com.learnsypro.app.data

import com.learnsypro.app.BuildConfig
import com.learnsypro.app.ui.auth.LoginResult
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
private data class LoginRequestBody(val username: String, val password: String)

@Serializable
private data class StudentPayload(
    val id: String,
    val username: String,
    val displayName: String? = null,
    val className: String? = null
)

@Serializable
private data class LoginResponseBody(
    val ok: Boolean,
    val msg: String? = null,
    val student: StudentPayload? = null
)

/**
 * ── AuthRepository ──
 * Gọi thẳng Supabase Edge Function `student-login` (server tự xử lý HMAC
 * pepper + bcrypt cost 12 + auto-migrate hash cũ, timing-safe compare).
 * Client KHÔNG tự so sánh password_hash — an toàn và đúng cách.
 */
class AuthRepository {

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private val endpoint = "${BuildConfig.SUPA_URL}/functions/v1/student-login"

    suspend fun checkLogin(username: String, password: String): LoginResult {
        return try {
            val response: LoginResponseBody = client.post(endpoint) {
                contentType(ContentType.Application.Json)
                header("apikey", BuildConfig.SUPA_KEY)
                header("Authorization", "Bearer ${BuildConfig.SUPA_KEY}")
                setBody(LoginRequestBody(username, password))
            }.body()

            if (response.ok && response.student != null) {
                LoginResult(ok = true, studentId = response.student.id)
            } else {
                LoginResult(ok = false, message = response.msg ?: "Sai tên đăng nhập hoặc mật khẩu!")
            }
        } catch (e: Exception) {
            LoginResult(ok = false, message = "Lỗi kết nối, thử lại nhé!")
        }
    }
}
