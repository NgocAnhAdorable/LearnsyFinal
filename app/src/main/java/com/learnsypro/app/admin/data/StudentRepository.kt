package com.learnsypro.app.admin.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class SetPasswordBody(val studentId: String, val password: String? = null)

@Serializable
data class SetPasswordResponse(val generatedPassword: String? = null)

// Tương đương phần Supabase calls trong StudentManager (student-manager.jsx).
// Bcrypt+pepper hashing xảy ra trong Edge Function 'student-set-password' —
// admin native KHÔNG tự hash mật khẩu, chỉ gọi function này giống bản web.
class StudentRepository {
    private val table = SupabaseConfig.client.from("students")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchAll(): List<Student> =
        table.select { order("created_at", Order.DESCENDING) }.decodeList<Student>()

    // Tương đương doAdd() bước 1 — insert với password_hash='PENDING' placeholder
    // FIX: trước đây dùng mapOf("is_active" to true, "username" to "...", ...)
    // — Kotlin suy luận kiểu Map<String, Any> vì trộn String và Boolean, và
    // kotlinx.serialization không có serializer cho Any -> throw
    // "Serializer for class 'Any' is not found" ngay khi insert() cố
    // serialize map này. buildJsonObject encode từng field tường minh nên
    // không gặp vấn đề suy luận kiểu.
    suspend fun createPending(username: String, displayName: String, className: String): Student {
        val body = buildJsonObject {
            put("username", username.trim().lowercase())
            put("password_hash", "PENDING")
            put("display_name", displayName.trim().ifBlank { username.trim() })
            put("class_name", className.trim())
            put("is_active", true)
        }
        val result = table.insert(body) { select() }
        return result.decodeSingle<Student>()
    }

    suspend fun rollbackDelete(id: String) {
        table.delete { filter { eq("id", id) } }
    }

    // Tương đương gọi Edge Function 'student-set-password' với header x-admin-secret.
    suspend fun setPassword(studentId: String, password: String?): String? {
        val body = SetPasswordBody(studentId, password?.ifBlank { null })
        val response = SupabaseConfig.client.functions.invoke(
            function = "student-set-password",
            body = body,
            headers = io.ktor.http.headersOf("x-admin-secret", SupabaseConfig.ADMIN_API_KEY)
        )
        val parsed = json.decodeFromString<SetPasswordResponse>(response.bodyAsText())
        return password ?: parsed.generatedPassword
    }

    suspend fun update(id: String, displayName: String, className: String) {
        table.update(buildJsonObject {
            put("display_name", displayName.trim())
            put("class_name", className.trim())
        }) { filter { eq("id", id) } }
    }

    suspend fun updateField(id: String, field: String, value: String) {
        table.update(buildJsonObject { put(field, value) }) { filter { eq("id", id) } }
    }

    suspend fun toggleActive(id: String, active: Boolean) {
        table.update(buildJsonObject { put("is_active", active) }) { filter { eq("id", id) } }
    }

    suspend fun bulkToggleActive(ids: List<String>, active: Boolean) {
        table.update(buildJsonObject { put("is_active", active) }) { filter { isIn("id", ids) } }
    }

    // FK ON DELETE CASCADE tự xoá quiz_results liên quan — không cần xoá tay
    suspend fun delete(id: String) {
        table.delete { filter { eq("id", id) } }
    }

    suspend fun bulkDelete(ids: List<String>): Pair<Int, Int> {
        var ok = 0; var fail = 0
        ids.forEach { id ->
            try {
                table.delete { filter { eq("id", id) } }
                ok++
            } catch (e: Exception) {
                fail++
            }
        }
        return ok to fail
    }
}
