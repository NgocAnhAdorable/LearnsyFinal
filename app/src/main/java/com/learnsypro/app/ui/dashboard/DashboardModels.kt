package com.learnsypro.app.ui.dashboard

/**
 * ── HistoryEntry ──
 * Tương đương object history item (h.lessonTitle, h.pct, h.ts, h.subject,
 * h.total, h.score).
 */
data class HistoryEntry(
    val lessonTitle: String,
    val pct: Double,
    val timestampMillis: Long,
    val subject: String? = null,
    val total: Int = 0,
    val score: Int = 0
)

/** Học sinh đang đăng nhập — tương đương student trong bản web. */
data class Student(
    val username: String,
    val displayName: String? = null
) {
    val nameOrUsername: String get() = displayName?.takeIf { it.isNotBlank() } ?: username
}
