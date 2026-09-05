package com.learnsypro.app.admin.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Student(
    val id: String,
    val username: String,
    @SerialName("password_hash") val passwordHash: String = "",
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("class_name") val className: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("is_active") val isActive: Boolean = true
)

// Tương đương genPass() — Google-style password generator
object PasswordGenerator {
    private const val UPPER = "ABCDEFGHJKLMNPQRTUVWXYZ"
    private const val LOWER = "abcdefghjkmnpqrtuvwxyz"
    private const val DIGIT = "2346789"
    private val ALL = UPPER + LOWER + DIGIT

    private fun segment(len: Int = 3): String {
        val chars = mutableListOf(UPPER.random(), LOWER.random(), DIGIT.random())
        repeat((len - 3).coerceAtLeast(0)) { chars.add(ALL.random()) }
        chars.shuffle()
        return chars.joinToString("")
    }

    fun generate(segments: Int = 4, segLen: Int = 3, sep: String = "-"): String =
        (1..segments).joinToString(sep) { segment(segLen) }
}
