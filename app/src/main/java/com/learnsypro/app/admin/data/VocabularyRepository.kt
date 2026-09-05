package com.learnsypro.app.admin.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

// Tương đương các hàm CRUD trong vocabulary-manager.jsx (admin web)
class VocabularyRepository {
    private val courses = SupabaseConfig.client.from("vocab_courses")
    private val units = SupabaseConfig.client.from("vocab_units")
    private val words = SupabaseConfig.client.from("vocab_words")

    // ── Bài học (courses) ──
    suspend fun fetchCourses(): List<VocabCourse> =
        courses.select {
            order("sort_order", Order.ASCENDING)
            order("created_at", Order.DESCENDING)
        }.decodeList<VocabCourse>()

    suspend fun createCourse(title: String, description: String): VocabCourse {
        val row = VocabCourse(id = genVocabId(), title = title, description = description, sortOrder = 0)
        courses.insert(row)
        return row
    }

    suspend fun updateCourse(id: String, title: String, description: String) {
        courses.update({
            set("title", title)
            set("description", description)
        }) { filter { eq("id", id) } }
    }

    suspend fun deleteCourse(id: String) {
        courses.delete { filter { eq("id", id) } }
    }

    // ── Unit ──
    suspend fun fetchUnits(courseId: String): List<VocabUnit> =
        units.select {
            filter { eq("course_id", courseId) }
            order("sort_order", Order.ASCENDING)
            order("created_at", Order.ASCENDING)
        }.decodeList<VocabUnit>()

    suspend fun createUnit(courseId: String, title: String, level: String): VocabUnit {
        val row = VocabUnit(id = genVocabId(), courseId = courseId, title = title, level = level, sortOrder = 0)
        units.insert(row)
        return row
    }

    suspend fun updateUnit(id: String, title: String, level: String) {
        units.update({
            set("title", title)
            set("level", level)
        }) { filter { eq("id", id) } }
    }

    suspend fun deleteUnit(id: String) {
        units.delete { filter { eq("id", id) } }
    }

    // ── Từ vựng (words) ──
    suspend fun fetchWords(unitId: String): List<VocabWord> =
        words.select {
            filter { eq("unit_id", unitId) }
            order("sort_order", Order.ASCENDING)
            order("created_at", Order.ASCENDING)
        }.decodeList<VocabWord>()

    suspend fun createWord(unitId: String, word: String, pos: String, ipa: String, meaning: String, example: String): VocabWord {
        val row = VocabWord(id = genVocabId(), unitId = unitId, word = word, pos = pos, ipa = ipa, meaning = meaning, example = example, sortOrder = 0)
        words.insert(row)
        return row
    }

    suspend fun updateWord(id: String, word: String, pos: String, ipa: String, meaning: String, example: String) {
        words.update({
            set("word", word)
            set("pos", pos)
            set("ipa", ipa)
            set("meaning", meaning)
            set("example", example)
        }) { filter { eq("id", id) } }
    }

    suspend fun deleteWord(id: String) {
        words.delete { filter { eq("id", id) } }
    }

    // Tương đương handleBulkSave() trong BulkWordModal — chèn nhiều từ cùng lúc
    suspend fun bulkInsertWords(unitId: String, lines: List<ParsedWordLine>): List<VocabWord> {
        val rows = lines.map {
            VocabWord(
                id = genVocabId(), unitId = unitId, word = it.word, pos = it.pos,
                ipa = it.ipa, meaning = it.meaning, example = it.example, sortOrder = 0
            )
        }
        if (rows.isNotEmpty()) words.insert(rows)
        return rows
    }
}
