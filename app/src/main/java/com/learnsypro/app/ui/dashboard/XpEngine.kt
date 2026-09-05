package com.learnsypro.app.ui.dashboard

/**
 * ── XpEngine ──
 * Hàm thuần tính XP + Level, không side-effect, không phụ thuộc Supabase
 * schema — XP luôn derive lại từ history (score/total) đã có sẵn, không
 * cần cột mới hay lưu trạng thái riêng dễ lệch dữ liệu.
 *
 * Công thức: 10 XP / câu đúng. Ngưỡng lên cấp tăng dần nhẹ:
 * level N cần 100 + (N-1)*50 XP (level 1→2: 100, 2→3: 150, 3→4: 200...).
 */

const val XP_PER_CORRECT_ANSWER = 10

// ── Thử thách tốc độ ──
// Trả lời trong ngưỡng nhanh (<=SPEED_FAST_SECONDS) được X2 XP câu đó; trong
// ngưỡng khá (<=SPEED_OK_SECONDS) được +50%; chậm hơn thì giữ nguyên XP gốc
// (không phạt trừ điểm — chỉ thưởng thêm cho tốc độ, câu sai vẫn luôn 0 XP
// dù trả lời nhanh cỡ nào, xử lý ở nơi gọi bằng cách chỉ đưa vào danh sách
// correctAnswerTimesSec những câu ĐÃ đúng).
const val SPEED_FAST_SECONDS = 5
const val SPEED_OK_SECONDS = 12

data class LevelState(
    val level: Int,
    val totalXp: Int,
    val xpIntoLevel: Int,
    val xpNeededForLevel: Int
) {
    val progressPct: Float
        get() = if (xpNeededForLevel > 0) (xpIntoLevel.toFloat() / xpNeededForLevel * 100f) else 0f
}

/** XP kiếm được từ 1 lần nộp bài, dựa trên số câu đúng (không phải %). */
fun xpForResult(score: Int, total: Int): Int {
    // score có thể là điểm phần (partial), làm tròn xuống để tránh XP ảo từ
    // câu trả lời 1 phần — công bằng hơn là XP luôn là số nguyên câu đúng.
    val correctCount = score.coerceAtLeast(0)
    return correctCount * XP_PER_CORRECT_ANSWER
}

/**
 * XP kiếm được ở chế độ Thử thách tốc độ — CHỈ nhận danh sách thời gian trả
 * lời (giây) của những câu ĐÃ ĐÚNG (nơi gọi lọc trước bằng isAnswerCorrect),
 * nên hàm này không cần biết đúng/sai, chỉ cần cộng dồn XP + bonus tốc độ
 * cho từng câu đúng trong danh sách.
 */
fun xpForSpeedResult(correctAnswerTimesSec: List<Int>): Int =
    correctAnswerTimesSec.sumOf { t ->
        val base = XP_PER_CORRECT_ANSWER
        when {
            t <= SPEED_FAST_SECONDS -> base * 2
            t <= SPEED_OK_SECONDS -> (base * 1.5).toInt()
            else -> base
        }
    }

private fun xpNeededFor(level: Int): Int = 100 + (level - 1) * 50

/** Tính level hiện tại + tiến độ trong level từ tổng XP tích luỹ. */
fun levelForXp(totalXp: Int): LevelState {
    var level = 1
    var remaining = totalXp.coerceAtLeast(0)
    while (remaining >= xpNeededFor(level)) {
        remaining -= xpNeededFor(level)
        level++
    }
    return LevelState(
        level = level,
        totalXp = totalXp,
        xpIntoLevel = remaining,
        xpNeededForLevel = xpNeededFor(level)
    )
}
