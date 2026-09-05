package com.learnsypro.app.data

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.learnsypro.app.ui.listening.ListeningItem
import com.learnsypro.app.ui.quiz.Lesson
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private val Application.offlineCacheDataStore by preferencesDataStore(name = "learnsy_offline_cache")

private val QUIZ_LESSONS_KEY = stringPreferencesKey("cache_quiz_lessons")
private val LISTENING_ITEMS_KEY = stringPreferencesKey("cache_listening_items")
private val LEARNING_FILES_KEY = stringPreferencesKey("cache_learning_files")
private val DOWNLOADED_LESSON_IDS_KEY = stringSetPreferencesKey("downloaded_lesson_ids")
private val DOWNLOADED_LISTENING_IDS_KEY = stringSetPreferencesKey("downloaded_listening_ids")

/**
 * ── OfflineCacheStore ──
 * Lưu offline toàn bộ danh sách bài quiz (`lessons`) và bài nghe
 * (`listening_items`) bằng DataStore Preferences (JSON), để mở lại được
 * khi không có mạng.
 *
 * Cơ chế 2 lớp theo đúng yêu cầu:
 * 1. TỰ ĐỘNG cache: mỗi lần tải thành công từ Supabase, ghi đè toàn bộ
 *    cache — luôn giữ bản mới nhất đã từng thấy được.
 * 2. TẢI THỦ CÔNG: học sinh đánh dấu 1 bài cụ thể là "đã tải" — set
 *    id được lưu riêng, dùng để hiển thị icon/trạng thái trong danh sách.
 *    Vì toàn bộ danh sách đã tự cache sẵn (bước 1), "tải về" ở đây chủ
 *    yếu là tín hiệu UI cho học sinh biết bài nào chắc chắn dùng offline
 *    được — không cần tải file riêng lẻ.
 *
 * Khi làm bài offline xong: KHÔNG đồng bộ kết quả lên Supabase (theo yêu
 * cầu) — QuizViewModel/ListeningViewModel vẫn tính điểm và hiện kết quả
 * bình thường, chỉ có bước lưu lịch sử lên `quiz_results` là sẽ tự nuốt lỗi
 * mạng (đã có try/catch sẵn trong LearnsyNavHost).
 */
class OfflineCacheStore(application: Application) {

    private val dataStore = application.offlineCacheDataStore
    private val json = Json { ignoreUnknownKeys = true }

    // ═══════════════ Quiz lessons ═══════════════

    suspend fun saveLessons(lessons: List<Lesson>) {
        try {
            val raw = json.encodeToString(lessons)
            dataStore.edit { it[QUIZ_LESSONS_KEY] = raw }
        } catch (e: Exception) {
            // Không chặn luồng chính nếu ghi cache lỗi (VD hết dung lượng) —
            // học sinh vẫn xem được bài vừa tải online bình thường.
        }
    }

    suspend fun loadLessons(): List<Lesson> {
        return try {
            val raw = dataStore.data.first()[QUIZ_LESSONS_KEY] ?: return emptyList()
            json.decodeFromString(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ═══════════════ Listening items ═══════════════

    suspend fun saveListeningItems(items: List<ListeningItem>) {
        try {
            val raw = json.encodeToString(items)
            dataStore.edit { it[LISTENING_ITEMS_KEY] = raw }
        } catch (e: Exception) {
            // xem lý do ở saveLessons()
        }
    }

    suspend fun loadListeningItems(): List<ListeningItem> {
        return try {
            val raw = dataStore.data.first()[LISTENING_ITEMS_KEY] ?: return emptyList()
            json.decodeFromString(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ═══════════════ Tab Tài liệu (learning_files) ═══════════════
    // Cache DANH SÁCH (tên, loại, mô tả) ở đây để hiện lại khi mất mạng.
    // Nội dung file thật (ảnh/PDF/video/audio/docx/xlsx) được cache riêng
    // theo từng file khi lần đầu xem trước — xem previewCacheDir() trong
    // FilePreviews.kt (thư mục filesDir/learning_files_cache, không phải
    // DataStore vì nội dung file có thể nặng hàng chục MB).

    suspend fun saveLearningFiles(files: List<LearningFile>) {
        try {
            val raw = json.encodeToString(files)
            dataStore.edit { it[LEARNING_FILES_KEY] = raw }
        } catch (e: Exception) {
            // xem lý do ở saveLessons()
        }
    }

    suspend fun loadLearningFiles(): List<LearningFile> {
        return try {
            val raw = dataStore.data.first()[LEARNING_FILES_KEY] ?: return emptyList()
            json.decodeFromString(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ═══════════════ "Đã tải về" (đánh dấu thủ công) ═══════════════

    suspend fun downloadedLessonIds(): Set<String> =
        dataStore.data.first()[DOWNLOADED_LESSON_IDS_KEY] ?: emptySet()

    suspend fun markLessonDownloaded(lessonId: String) {
        dataStore.edit { prefs ->
            val cur = prefs[DOWNLOADED_LESSON_IDS_KEY] ?: emptySet()
            prefs[DOWNLOADED_LESSON_IDS_KEY] = cur + lessonId
        }
    }

    suspend fun downloadedListeningIds(): Set<String> =
        dataStore.data.first()[DOWNLOADED_LISTENING_IDS_KEY] ?: emptySet()

    suspend fun markListeningDownloaded(itemId: String) {
        dataStore.edit { prefs ->
            val cur = prefs[DOWNLOADED_LISTENING_IDS_KEY] ?: emptySet()
            prefs[DOWNLOADED_LISTENING_IDS_KEY] = cur + itemId
        }
    }
}
