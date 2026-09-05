package com.learnsypro.app.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.learnsypro.app.admin.data.QuizResult
import com.learnsypro.app.admin.data.ScoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ResultsUiState(
    val results: List<QuizResult> = emptyList(),
    val loading: Boolean = true,
    val clearing: Boolean = false,
    // FIX: trước đây lỗi load() bị nuốt hoàn toàn (catch rỗng), nên nếu query
    // thất bại vì bất kỳ lý do gì (sai tên cột, decode lỗi, mất mạng...), UI
    // hiện y hệt "Chưa có kết quả làm bài" như trường hợp bảng thật sự rỗng —
    // không cách nào phân biệt hay chẩn đoán được. Giữ lại thông báo lỗi để
    // hiển thị rõ ràng thay vì đánh lừa người dùng.
    val error: String? = null
)

// Tương đương ResultsPanel trong dashboard.jsx
class ResultsViewModel(
    private val repo: ScoreRepository = ScoreRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResultsUiState())
    val uiState: StateFlow<ResultsUiState> = _uiState.asStateFlow()

    fun load() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val results = repo.recentResults(50)
                _uiState.update { it.copy(results = results, loading = false, error = null) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(loading = false, error = e.message ?: "Không tải được kết quả (lỗi không rõ)")
                }
            }
        }
    }

    // Xác nhận xoá do UI (dialog) xử lý trước khi gọi hàm này —
    // tương đương window.confirm() trong handleClearAll()
    fun clearAll(onDone: (success: Boolean, message: String) -> Unit) {
        if (_uiState.value.results.isEmpty()) return
        _uiState.update { it.copy(clearing = true) }
        viewModelScope.launch {
            try {
                repo.clearAll()
                val remaining = repo.recentResults(1)
                if (remaining.isNotEmpty()) {
                    throw Exception("Dữ liệu vẫn còn sau khi xoá — có thể do quyền (RLS) chặn.")
                }
                _uiState.update { it.copy(results = emptyList(), clearing = false) }
                onDone(true, "Đã xoá toàn bộ kết quả!")
            } catch (e: Exception) {
                _uiState.update { it.copy(clearing = false) }
                onDone(false, "Xoá thất bại — kiểm tra quyền xoá (RLS) trên bảng quiz_results!")
            }
        }
    }

    fun pct(r: QuizResult): Int = if (r.safeTotal > 0) Math.round(r.safeScore * 100.0 / r.safeTotal).toInt() else 0

    fun avg(): Int {
        val results = _uiState.value.results
        if (results.isEmpty()) return 0
        return Math.round(results.sumOf { pct(it) }.toDouble() / results.size).toInt()
    }

    fun highCount(): Int = _uiState.value.results.count { pct(it) >= 80 }

    fun scoreBins(): List<Pair<String, Int>> {
        val results = _uiState.value.results
        return listOf(
            "0–49" to results.count { pct(it) < 50 },
            "50–69" to results.count { pct(it) in 50..69 },
            "70–84" to results.count { pct(it) in 70..84 },
            "85+" to results.count { pct(it) >= 85 }
        )
    }
}
