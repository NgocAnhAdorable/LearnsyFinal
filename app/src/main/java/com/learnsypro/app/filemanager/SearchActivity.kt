package com.learnsypro.app.filemanager
import com.learnsypro.app.R

import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.learnsypro.app.filemanager.adapters.LocalFileAdapter
import com.learnsypro.app.databinding.ActivitySearchBinding
import com.learnsypro.app.filemanager.model.LocalFile
import com.learnsypro.app.filemanager.util.ActivityTransitions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Tìm kiếm file theo tên trong toàn bộ Bộ nhớ trong (đệ quy mọi thư mục con), tương đương nút
 * tìm kiếm (kính lúp) ở màn hình Home của Samsung My Files. Gõ tới đâu tìm tới đó (debounce
 * 350ms). Có thêm bộ lọc Thời gian sửa đổi + Loại file, và lịch sử "Tìm kiếm gần đây" lưu cục bộ.
 */
class SearchActivity : LearnsyFileManagerActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var adapter: LocalFileAdapter
    private var searchJob: Job? = null
    private var isFilterExpanded = true

    // Thời gian: chỉ 1 lựa chọn tại 1 thời điểm (hoặc không chọn gì = không lọc theo thời gian)
    private var timeFilterMillis: Long? = null
    // Loại file: có thể chọn nhiều cùng lúc (vd Ảnh + Video)
    private val activeTypeFilters = mutableSetOf<FileTypeFilter>()

    private enum class FileTypeFilter(val extensions: Set<String>) {
        IMAGE(setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")),
        VIDEO(setOf("mp4", "mkv", "mov", "avi", "3gp", "webm", "m4v")),
        AUDIO(setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "wma")),
        DOCUMENT(setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv")),
        APK(setOf("apk")),
        ARCHIVE(setOf("zip", "rar", "7z", "tar", "gz"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = LocalFileAdapter(
            iconRes = R.drawable.ic_file,
            onItemClick = { openFile(it) },
            onMoreClick = { _, _ -> }
        )
        binding.rvResults.layoutManager = LinearLayoutManager(this)
        binding.rvResults.adapter = adapter

        binding.btnBack.setOnClickListener {
            finish()
            ActivityTransitions.backward(this)
        }
        binding.btnClear.setOnClickListener {
            binding.etSearch.text?.clear()
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString().orEmpty()
                binding.btnClear.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
                scheduleSearch(query)
            }
        })

        setupFilterToggle()
        setupTimeChips()
        setupTypeChips()
        renderRecentSearches()

        binding.etSearch.requestFocus()
    }

    // ---------- Khối "Bộ lọc" thu gọn/mở rộng ----------

    private fun setupFilterToggle() {
        binding.rowFilterHeader.setOnClickListener {
            isFilterExpanded = !isFilterExpanded
            binding.layoutFilterBody.visibility = if (isFilterExpanded) View.VISIBLE else View.GONE
            binding.ivFilterToggle.animate()
                .rotation(if (isFilterExpanded) 0f else 180f)
                .setDuration(180)
                .start()
        }
    }

    // ---------- Chip "Thời gian" (chọn 1) ----------

    private fun setupTimeChips() {
        val chips = mapOf(
            binding.chipTime1d to TimeUnit.DAYS.toMillis(1),
            binding.chipTime7d to TimeUnit.DAYS.toMillis(7),
            binding.chipTime30d to TimeUnit.DAYS.toMillis(30)
        )
        chips.forEach { (chip, windowMillis) ->
            chip.setOnClickListener {
                // Bấm lại chip đang chọn để bỏ chọn (ChipGroup singleSelection không tự cho bỏ hết)
                if (!chip.isChecked) {
                    timeFilterMillis = null
                } else {
                    chips.keys.filter { it != chip }.forEach { it.isChecked = false }
                    timeFilterMillis = windowMillis
                }
                rerunCurrentSearch()
            }
        }
    }

    // ---------- Chip "Loại" (chọn nhiều) ----------

    private fun setupTypeChips() {
        val chips = mapOf(
            binding.chipTypeImage to FileTypeFilter.IMAGE,
            binding.chipTypeVideo to FileTypeFilter.VIDEO,
            binding.chipTypeAudio to FileTypeFilter.AUDIO,
            binding.chipTypeDocument to FileTypeFilter.DOCUMENT,
            binding.chipTypeApk to FileTypeFilter.APK,
            binding.chipTypeArchive to FileTypeFilter.ARCHIVE
        )
        chips.forEach { (chip, type) ->
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) activeTypeFilters.add(type) else activeTypeFilters.remove(type)
                rerunCurrentSearch()
            }
        }
    }

    private fun rerunCurrentSearch() {
        scheduleSearch(binding.etSearch.text?.toString().orEmpty())
    }

    // ---------- Lịch sử "Tìm kiếm gần đây" ----------

    private fun recentSearchPrefs() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun loadRecentSearches(): List<String> {
        val raw = recentSearchPrefs().getString(KEY_RECENT, null) ?: return emptyList()
        return raw.split(RECENT_SEPARATOR).filter { it.isNotBlank() }
    }

    private fun saveRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val current = loadRecentSearches().toMutableList()
        current.removeAll { it.equals(trimmed, ignoreCase = true) }
        current.add(0, trimmed)
        val capped = current.take(MAX_RECENT_ITEMS)
        recentSearchPrefs().edit().putString(KEY_RECENT, capped.joinToString(RECENT_SEPARATOR)).apply()
        renderRecentSearches()
    }

    private fun removeRecentSearch(query: String) {
        val current = loadRecentSearches().toMutableList()
        current.removeAll { it.equals(query, ignoreCase = true) }
        recentSearchPrefs().edit().putString(KEY_RECENT, current.joinToString(RECENT_SEPARATOR)).apply()
        renderRecentSearches()
    }

    private fun clearAllRecentSearches() {
        recentSearchPrefs().edit().remove(KEY_RECENT).apply()
        renderRecentSearches()
    }

    private fun renderRecentSearches() {
        val items = loadRecentSearches()
        binding.layoutRecentSearches.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        binding.chipGroupRecent.removeAllViews()
        val inflater = LayoutInflater.from(this)
        items.forEach { term ->
            val chip = inflater.inflate(R.layout.item_search_recent_chip, binding.chipGroupRecent, false) as Chip
            chip.text = term
            chip.setOnClickListener {
                binding.etSearch.setText(term)
                binding.etSearch.setSelection(term.length)
            }
            chip.setOnCloseIconClickListener { removeRecentSearch(term) }
            binding.chipGroupRecent.addView(chip)
        }
        binding.btnClearRecent.setOnClickListener { clearAllRecentSearches() }
    }

    // ---------- Tìm kiếm ----------

    private fun scheduleSearch(query: String) {
        searchJob?.cancel()
        val hasFilters = timeFilterMillis != null || activeTypeFilters.isNotEmpty()

        if (query.isBlank() && !hasFilters) {
            showPreSearchState()
            return
        }

        searchJob = lifecycleScope.launch {
            delay(if (query.isBlank()) 0 else 350) // debounce chỉ cần khi đang gõ text
            showLoadingState()
            val results = withContext(Dispatchers.IO) { searchFiles(query.trim()) }
            if (query.isNotBlank()) saveRecentSearch(query)
            showResultsState(results)
        }
    }

    private fun showPreSearchState() {
        binding.scrollPreSearch.visibility = View.VISIBLE
        binding.layoutResults.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.progress.visibility = View.GONE
    }

    private fun showLoadingState() {
        binding.scrollPreSearch.visibility = View.GONE
        binding.layoutResults.visibility = View.GONE
        binding.layoutEmpty.visibility = View.GONE
        binding.progress.visibility = View.VISIBLE
    }

    private fun showResultsState(results: List<LocalFile>) {
        binding.progress.visibility = View.GONE
        binding.scrollPreSearch.visibility = View.GONE
        adapter.submit(results)
        if (results.isEmpty()) {
            binding.layoutResults.visibility = View.GONE
            binding.layoutEmpty.visibility = View.VISIBLE
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.layoutResults.visibility = View.VISIBLE
            binding.tvResultCount.visibility = View.VISIBLE
            binding.tvResultCount.text = getString(R.string.search_results_count, results.size)
        }
    }

    /**
     * Duyệt đệ quy TOÀN BỘ nhớ trong — kể cả mọi cấp thư mục con — khớp tên file/thư mục
     * không phân biệt hoa thường, rồi áp thêm bộ lọc Thời gian sửa đổi và/hoặc Loại file nếu
     * người dùng đang chọn. Giới hạn 500 kết quả và độ sâu để tránh treo máy.
     */
    private fun searchFiles(query: String): List<LocalFile> {
        val root = Environment.getExternalStorageDirectory() ?: return emptyList()
        val lowerQuery = query.lowercase()
        val minModifiedTime = timeFilterMillis?.let { System.currentTimeMillis() - it }
        val result = mutableListOf<LocalFile>()

        fun matchesFilters(f: File): Boolean {
            if (query.isNotEmpty() && !f.name.lowercase().contains(lowerQuery)) return false
            if (minModifiedTime != null && f.lastModified() < minModifiedTime) return false
            if (activeTypeFilters.isNotEmpty()) {
                if (f.isDirectory) return false // bộ lọc Loại chỉ áp dụng cho file, không áp dụng cho thư mục
                val ext = f.name.substringAfterLast('.', "").lowercase()
                if (activeTypeFilters.none { ext in it.extensions }) return false
            }
            return true
        }

        // scan() đi qua MỌI thư mục con bất kể có khớp bộ lọc hay không, để tìm được file nằm
        // sâu bên trong (vd tìm "abc" vẫn thấy /Download/2026/abc.pdf dù thư mục "2026" không khớp).
        fun scan(dir: File, depth: Int) {
            if (result.size >= MAX_RESULTS || depth > MAX_DEPTH) return
            val children = dir.listFiles() ?: return
            for (f in children) {
                if (result.size >= MAX_RESULTS) return
                if (f.name.startsWith(".")) continue // bỏ qua thư mục ẩn/thùng rác nội bộ
                if (matchesFilters(f)) {
                    result.add(
                        LocalFile(
                            name = f.name,
                            path = f.absolutePath,
                            size = if (f.isFile) f.length() else 0L,
                            modifiedTime = f.lastModified(),
                            isDirectory = f.isDirectory,
                            itemCount = if (f.isDirectory) (f.listFiles()?.size ?: 0) else 0
                        )
                    )
                }
                if (f.isDirectory) scan(f, depth + 1)
            }
        }

        try {
            scan(root, 0)
        } catch (e: Exception) {
            // bỏ qua thư mục không đọc được (thiếu quyền)
        }
        return result.sortedByDescending { it.modifiedTime }
    }

    // Các phần mở rộng file text/mã nguồn có thể sửa trực tiếp bằng CodeEditorActivity trong
    // app — đồng bộ với CategoryFilesActivity/FileBrowserActivity để hành vi nhất quán dù mở
    // file từ đâu trong app.
    private val editableExtensions = setOf(
        "kt", "java", "js", "ts", "jsx", "tsx", "html", "htm", "css", "json", "xml",
        "py", "c", "cpp", "h", "cs", "php", "rb", "go", "rs", "sh", "sql", "yml", "yaml",
        "gradle", "properties", "md", "txt", "log", "ini", "env"
    )

    private fun openFile(file: LocalFile) {
        if (file.isDirectory) return // mở thư mục trong kết quả tìm kiếm ít có ích, bỏ qua
        val ext = file.name.substringAfterLast('.', "").lowercase()
        if (ext in editableExtensions && !file.path.startsWith("content://")) {
            val intent = android.content.Intent(this, CodeEditorActivity::class.java)
                .putExtra(CodeEditorActivity.EXTRA_FILE_PATH, file.path)
            startActivity(intent)
            ActivityTransitions.forward(this)
            return
        }
        com.learnsypro.app.filemanager.util.FileOpenUtils.openDefault(this, binding.root, file.path, file.name)
    }

    companion object {
        private const val MAX_RESULTS = 500
        private const val MAX_DEPTH = 8
        private const val PREFS_NAME = "search_prefs"
        private const val KEY_RECENT = "recent_searches"
        private const val RECENT_SEPARATOR = "\u0001"
        private const val MAX_RECENT_ITEMS = 10
    }
}
