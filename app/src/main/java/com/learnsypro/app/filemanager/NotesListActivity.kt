package com.learnsypro.app.filemanager
import com.learnsypro.app.R

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.learnsypro.app.filemanager.adapters.NoteAdapter
import com.learnsypro.app.databinding.ActivityNotesListBinding
import com.learnsypro.app.filemanager.notes.NoteFileStore
import com.learnsypro.app.filemanager.util.ActivityTransitions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Danh sách ghi chú kiểu Samsung Notes — lưới 2 cột, mỗi thẻ xem trước tiêu đề + vài dòng đầu +
 * ngày sửa gần nhất. Đọc trực tiếp từ thư mục Notes/ trong Bộ nhớ trong (không có database
 * riêng) — mỗi lần quay lại màn hình này đều quét lại thư mục, nên xóa/sửa ghi chú ở nơi khác
 * (vd. xóa file .html qua CategoryFilesActivity) cũng phản ánh đúng ở đây.
 */
class NotesListActivity : LearnsyFileManagerActivity() {

    private lateinit var binding: ActivityNotesListBinding
    private lateinit var adapter: NoteAdapter
    private var allNotes: List<NoteFileStore.NoteSummary> = emptyList()
    private var searchQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotesListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        com.learnsypro.app.filemanager.util.WindowInsetsUtils.applyTopInsetHeight(binding.statusBarSpacer)

        binding.toolbar.setNavigationOnClickListener {
            finish()
            ActivityTransitions.backward(this)
        }
        binding.toolbar.setOnMenuItemClickListener { onMenuItemSelected(it) }

        adapter = NoteAdapter(
            onClick = { note -> openEditor(note.file.absolutePath) },
            onLongClick = { note -> confirmDelete(note) }
        )
        binding.rvNotes.layoutManager = GridLayoutManager(this, 2)
        binding.rvNotes.adapter = adapter

        binding.fabAddNote.setOnClickListener { openEditor(null) }

        setupSearchBar()
    }

    override fun onResume() {
        super.onResume()
        // Quét lại mỗi lần quay lại màn hình (sau khi tạo/sửa/xóa ghi chú ở NoteEditorActivity) —
        // đơn giản và đủ nhanh vì không dùng database, chỉ đọc lại 1 thư mục nhỏ.
        loadNotes()
    }

    private fun setupSearchBar() {
        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty()
                applyFilter()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun onMenuItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_search) {
            val showing = binding.searchBar.visibility == View.VISIBLE
            binding.searchBar.visibility = if (showing) View.GONE else View.VISIBLE
            if (showing) {
                binding.etSearch.setText("")
            } else {
                binding.etSearch.requestFocus()
            }
        }
        return true
    }

    private fun loadNotes() {
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val notes = withContext(Dispatchers.IO) { NoteFileStore.listNotes() }
            binding.progress.visibility = View.GONE
            allNotes = notes
            applyFilter()
        }
    }

    private fun applyFilter() {
        val filtered = if (searchQuery.isBlank()) {
            allNotes
        } else {
            allNotes.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.previewText.contains(searchQuery, ignoreCase = true)
            }
        }
        adapter.submitList(filtered)
        binding.layoutEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openEditor(filePath: String?) {
        val intent = Intent(this, NoteEditorActivity::class.java).apply {
            if (filePath != null) putExtra(NoteEditorActivity.EXTRA_FILE_PATH, filePath)
        }
        ActivityTransitions.startForward(this, intent)
    }

    private fun confirmDelete(note: NoteFileStore.NoteSummary) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.notes_delete_confirm_title))
            .setMessage(getString(R.string.notes_delete_confirm_message))
            .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { note.file.delete() }
                    loadNotes()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
