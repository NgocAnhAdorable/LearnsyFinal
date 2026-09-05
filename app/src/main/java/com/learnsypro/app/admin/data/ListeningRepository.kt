package com.learnsypro.app.admin.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant

// Tương đương các hàm CRUD trong ListeningManager (listening-panel.jsx).
class ListeningRepository {
    private val table = SupabaseConfig.client.from("listening_items")

    suspend fun fetchAll(): List<ListeningItem> =
        table.select {
            order("sort_order", Order.ASCENDING)
            order("created_at", Order.ASCENDING)
        }.decodeList<ListeningItem>()

    // Tương đương check trùng nội dung (text) trong save() — không phân biệt hoa/thường
    fun isDuplicateText(text: String, excludeId: String?, items: List<ListeningItem>): Boolean {
        val norm = cleanListeningStr(text).lowercase()
        return items.any { it.id != excludeId && cleanListeningStr(it.text).lowercase() == norm }
    }

    suspend fun create(item: ListeningItem): ListeningItem {
        table.insert(item)
        return item
    }

    suspend fun update(item: ListeningItem) {
        table.update(item) { filter { eq("id", item.id) } }
    }

    suspend fun delete(id: String) {
        table.delete { filter { eq("id", id) } }
    }

    suspend fun bulkDelete(ids: List<String>) {
        table.delete { filter { isIn("id", ids) } }
    }

    // Tương đương duplicate() — sao chép, id mới, sortOrder = max+1
    suspend fun duplicate(item: ListeningItem, items: List<ListeningItem>): ListeningItem {
        val sortMax = items.maxOfOrNull { it.sortOrder } ?: 0
        val newItem = item.copy(
            id = genListeningId(),
            sortOrder = sortMax + 1,
            createdAt = Instant.now().toString()
        )
        table.insert(newItem)
        return newItem
    }

    // Tương đương persistOrder() — cập nhật sort_order cho từng item sau kéo-thả
    suspend fun persistOrder(orderedIds: List<String>) {
        orderedIds.forEachIndexed { i, id ->
            table.update({ set("sort_order", i) }) { filter { eq("id", id) } }
        }
    }

    // Tương đương doImport() — upsert mảng, tự gán id/sort_order nếu thiếu
    suspend fun importItems(items: List<ListeningItem>, existing: List<ListeningItem>): List<ListeningItem> {
        val sortBase = existing.maxOfOrNull { it.sortOrder } ?: 0
        val toInsert = items.mapIndexed { i, it ->
            it.copy(
                id = it.id.ifBlank { "imp${System.currentTimeMillis()}$i" },
                sortOrder = if (it.sortOrder == 0) sortBase + i + 1 else it.sortOrder
            )
        }
        table.upsert(toInsert)
        return fetchAll()
    }
}
