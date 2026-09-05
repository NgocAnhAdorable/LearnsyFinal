package com.learnsypro.app.filemanager
import com.learnsypro.app.R

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.learnsypro.app.filemanager.adapters.RemoteDidlAdapter
import com.learnsypro.app.filemanager.adapters.RemoteServerAdapter
import com.learnsypro.app.databinding.ActivityFileBrowserBinding
import com.learnsypro.app.filemanager.dlna.RemoteContentDirectoryClient
import com.learnsypro.app.filemanager.dlna.RemoteDidlItem
import com.learnsypro.app.filemanager.dlna.RemoteMediaServer
import com.learnsypro.app.filemanager.util.ArchiveUtils
import com.learnsypro.app.filemanager.util.FileTypeUtils
import com.learnsypro.app.filemanager.util.LogBus
import kotlinx.coroutines.launch
import java.io.File
import java.util.ArrayDeque

/**
 * Màn hình "Duyệt máy chủ DLNA khác" — phần control point kiểu BubbleUPnP: dò các MediaServer
 * khác trong mạng (NAS, điện thoại khác cũng chạy MyFile Manager, TV chia sẻ file...), duyệt
 * thư mục của họ, và mở/tải file tìm được.
 *
 * Dùng CHUNG layout activity_file_browser.xml với FileBrowserActivity/CloudBrowserActivity để
 * đồng bộ giao diện toàn app: cùng toolbar + nút tìm kiếm, cùng thanh đường dẫn màu xanh nhạt,
 * cùng hàng sắp xếp, cùng SwipeRefreshLayout với overlay rỗng/đang tải.
 *
 * "Bê toàn bộ tính năng Lưu trữ sang DLNA": giống Cloud, mỗi file có menu "..." (Tải về máy/
 * Chia sẻ/Chi tiết) và bấm vào file sẽ mở đúng loại preview (ảnh/video/audio phát trực tiếp qua
 * URL DLNA không cần tải trước; JSON/text/archive phải tải về cache trước rồi mới mở được, vì
 * CodeEditorActivity/ArchivePreviewActivity đọc từ đường dẫn file thật trên máy, không đọc được
 * qua network stream). Không có Đổi tên/Xóa vì máy chủ DLNA là CỦA NGƯỜI KHÁC — app chỉ có
 * quyền đọc qua UPnP ContentDirectory:Browse, không có action nào để sửa đổi dữ liệu của họ.
 *
 * 2 trạng thái dùng chung 1 RecyclerView (rv_files):
 *  - Chưa chọn server: hiển thị danh sách server tìm thấy (RemoteServerAdapter).
 *  - Đã chọn server: hiển thị nội dung thư mục hiện tại (RemoteDidlAdapter), có ngăn xếp
 *    breadcrumb (folderStack) để hỗ trợ nút "Lên thư mục cha" và nút Back của hệ thống.
 */
class RemoteServersActivity : LearnsyFileManagerActivity() {

    private lateinit var binding: ActivityFileBrowserBinding
    private lateinit var serverAdapter: RemoteServerAdapter
    private lateinit var didlAdapter: RemoteDidlAdapter

    private var currentServer: RemoteMediaServer? = null
    /** Ngăn xếp (objectId, tên hiển thị) các thư mục đã vào, để hỗ trợ quay lại đúng đường dẫn. */
    private val folderStack = ArrayDeque<Pair<String, String>>()

    // Tìm kiếm cục bộ trong danh sách đang hiển thị (server HOẶC entry thư mục hiện tại) —
    // giống hành vi search của FileBrowserActivity/CloudBrowserActivity, không gọi lại mạng.
    private var rawServers: List<RemoteMediaServer> = emptyList()
    private var rawEntries: List<RemoteDidlItem> = emptyList()
    private var searchQuery: String = ""
    private var sortAscending = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Đồng bộ với CategoryFilesActivity: cộng thêm padding-top đúng chiều cao status bar
        // để toolbar không bị đồng hồ/status bar hệ thống đè lên khi vẽ edge-to-edge.
        com.learnsypro.app.filemanager.util.WindowInsetsUtils.applyTopInsetHeight(binding.statusBarSpacer)

        binding.toolbar.title = getString(R.string.title_remote_servers)

        // Duyệt DLNA chỉ đọc: không có tải lên/tạo thư mục/chọn nhiều/pill dung lượng — ẩn hẳn
        // để không hiện các nút bấm vào rồi không làm gì.
        binding.bottomActionBar.visibility = View.GONE
        binding.selectionBar.visibility = View.GONE
        binding.quotaBarContainer.visibility = View.GONE
        com.learnsypro.app.filemanager.util.WindowInsetsUtils.applyBottomInsetPadding(binding.swipeRefresh)

        binding.toolbar.setNavigationOnClickListener {
            // handleBackNavigation() chỉ xử lý khi đang ở trong 1 server/thư mục con (điều hướng
            // "lùi 1 cấp"); ở màn hình gốc (chưa chọn server) nó trả về false và trước đây
            // KHÔNG CÓ gì gọi tiếp — khiến nút back trên toolbar không phản hồi gì cả. Phải tự
            // đóng Activity trong trường hợp đó, giống hành vi nút back hệ thống.
            if (!handleBackNavigation()) finish()
        }

        serverAdapter = RemoteServerAdapter { server -> openServer(server) }
        didlAdapter = RemoteDidlAdapter(
            onClick = { entry -> handleEntryClick(entry) },
            onMoreClick = { entry, view -> showEntryMenu(entry, view) }
        )

        binding.rvFiles.layoutManager = LinearLayoutManager(this)
        binding.rvFiles.adapter = serverAdapter

        binding.btnGoUp.setOnClickListener { goUpOneLevel() }
        binding.swipeRefresh.setOnRefreshListener {
            if (currentServer != null) loadCurrentFolder() else scanForServers()
        }

        setupSearchBar()
        setupSortRow()

        onBackPressedDispatcher.addCallback(this) {
            // handleBackNavigation() trả false nghĩa là không còn gì để "lùi 1 cấp" bên trong
            // màn hình này (đang ở danh sách server gốc) — phải thoát Activity thẳng, KHÔNG
            // disable rồi gọi lại dispatcher (cách cũ dễ khiến back bị "nuốt" im lặng khi đây
            // là callback duy nhất đăng ký, khiến nút back hệ thống trông như không hoạt động).
            if (!handleBackNavigation()) finish()
        }

        scanForServers()
    }

    /** Trả về true nếu đã xử lý (đang trong thư mục con hoặc trong 1 server) — chặn back mặc định của Activity. */
    private fun handleBackNavigation(): Boolean {
        return when {
            folderStack.size > 1 -> { goUpOneLevel(); true }
            currentServer != null -> { backToServerList(); true }
            else -> false
        }
    }

    // ---------------- thanh tìm kiếm (giống FileBrowserActivity) ----------------

    private fun setupSearchBar() {
        binding.btnSearch.setOnClickListener {
            binding.searchBar.visibility = View.VISIBLE
            binding.etSearch.requestFocus()
        }
        binding.btnCloseSearch.setOnClickListener {
            binding.searchBar.visibility = View.GONE
            binding.etSearch.setText("")
            searchQuery = ""
            applyFilterAndRender()
        }
        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty()
                binding.btnClearSearch.visibility = if (searchQuery.isEmpty()) View.GONE else View.VISIBLE
                applyFilterAndRender()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.setText("")
        }
    }

    // ---------------- sắp xếp theo tên (giống FileBrowserActivity) ----------------

    private fun setupSortRow() {
        binding.tvSortBy.text = getString(R.string.sort_by_name)
        binding.btnSortBy.isEnabled = false // chỉ có 1 kiểu sắp xếp (tên) cho danh sách từ xa
        // Giống FileBrowserActivity: xoay mũi tên 180° thay vì đổi icon khi đảo chiều sắp xếp.
        binding.btnSortDirection.rotation = if (sortAscending) 0f else 180f
        binding.btnSortDirection.setOnClickListener {
            sortAscending = !sortAscending
            binding.btnSortDirection.rotation = if (sortAscending) 0f else 180f
            applyFilterAndRender()
        }
    }

    private fun applyFilterAndRender() {
        if (currentServer == null) {
            var list = rawServers
            if (searchQuery.isNotBlank()) {
                list = list.filter { it.friendlyName.contains(searchQuery, ignoreCase = true) }
            }
            list = list.sortedBy { it.friendlyName.lowercase() }
            if (!sortAscending) list = list.reversed()
            serverAdapter.submitList(list)
            binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        } else {
            var list = rawEntries
            if (searchQuery.isNotBlank()) {
                list = list.filter { it.title.contains(searchQuery, ignoreCase = true) }
            }
            // Thư mục luôn nổi lên trên, giống FileBrowserActivity/CloudBrowserActivity.
            list = list.sortedWith(compareByDescending<RemoteDidlItem> { it.isContainer }.thenBy { it.title.lowercase() })
            if (!sortAscending) list = list.reversed()
            didlAdapter.submitList(list)
            binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    // ---------------- danh sách server ----------------

    private fun scanForServers() {
        binding.progress.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE
        binding.rvFiles.adapter = serverAdapter
        lifecycleScope.launch {
            val servers = try {
                RemoteContentDirectoryClient.discoverServers()
            } catch (e: Exception) {
                LogBus.error("Lỗi khi dò máy chủ DLNA", source = "DLNA", throwable = e)
                emptyList()
            }
            binding.progress.visibility = View.GONE
            binding.swipeRefresh.isRefreshing = false
            rawServers = servers
            binding.tvEmpty.text = getString(R.string.remote_no_servers)
            applyFilterAndRender()
        }
    }

    private fun openServer(server: RemoteMediaServer) {
        currentServer = server
        folderStack.clear()
        folderStack.push("0" to server.friendlyName)
        binding.toolbar.title = server.friendlyName
        binding.rvFiles.adapter = didlAdapter
        loadCurrentFolder()
    }

    private fun backToServerList() {
        currentServer = null
        folderStack.clear()
        binding.toolbar.title = getString(R.string.title_remote_servers)
        binding.tvCurrentPath.text = "/"
        binding.rvFiles.adapter = serverAdapter
        binding.tvEmpty.visibility = View.GONE
        applyFilterAndRender()
    }

    // ---------------- duyệt thư mục trong 1 server ----------------

    private fun goUpOneLevel() {
        if (folderStack.size <= 1) {
            backToServerList()
            return
        }
        folderStack.pop()
        loadCurrentFolder()
    }

    private fun loadCurrentFolder() {
        val server = currentServer ?: return
        val (objectId, _) = folderStack.peek() ?: ("0" to server.friendlyName)
        binding.tvCurrentPath.text = folderStack.reversed().joinToString(" / ") { it.second }
        binding.progress.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            val entries = try {
                RemoteContentDirectoryClient.browse(server, objectId)
            } catch (e: Exception) {
                LogBus.error("Lỗi khi duyệt máy chủ ${server.friendlyName}", source = "DLNA", throwable = e)
                null
            }
            binding.progress.visibility = View.GONE
            binding.swipeRefresh.isRefreshing = false
            if (entries == null) {
                Toast.makeText(this@RemoteServersActivity, getString(R.string.remote_browse_failed), Toast.LENGTH_SHORT).show()
                rawEntries = emptyList()
                didlAdapter.submitList(emptyList())
                binding.tvEmpty.text = getString(R.string.remote_browse_failed)
                binding.tvEmpty.visibility = View.VISIBLE
                return@launch
            }
            rawEntries = entries
            binding.tvEmpty.text = getString(R.string.remote_empty_folder)
            applyFilterAndRender()
        }
    }

    private fun handleEntryClick(entry: RemoteDidlItem) {
        if (entry.isContainer) {
            folderStack.push(entry.id to entry.title)
            loadCurrentFolder()
            return
        }
        val url = entry.resUrl
        if (url.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.remote_open_failed), Toast.LENGTH_SHORT).show()
            return
        }
        when {
            // Ảnh/video/audio: phát/xem TRỰC TIẾP qua URL streaming, không cần tải về trước —
            // giống hệt cách app hiện tại đã làm.
            entry.mimeType?.startsWith("audio") == true -> playRemoteAudio(url, entry.title)
            entry.mimeType?.startsWith("image") == true || entry.mimeType?.startsWith("video") == true ->
                playRemoteMedia(url, entry.title, entry.mimeType?.startsWith("video") == true)
            // PDF/docx/xlsx: giống archive/text bên dưới — các viewer này đọc bằng đường dẫn file
            // thật trên máy nên phải tải về cache trước, không stream trực tiếp được như ảnh/
            // video/audio (ExoPlayer đọc URL trực tiếp được, còn PdfRenderer/docx/xlsx parser thì không).
            entry.title.substringAfterLast('.', "").lowercase() == "pdf" ->
                downloadThenOpen(entry, PdfViewerActivity::class.java, PdfViewerActivity.EXTRA_FILE_PATH)
            entry.title.substringAfterLast('.', "").lowercase() == "docx" ->
                downloadThenOpen(entry, DocxViewerActivity::class.java, DocxViewerActivity.EXTRA_FILE_PATH)
            entry.title.substringAfterLast('.', "").lowercase() == "xlsx" ->
                downloadThenOpen(entry, XlsxViewerActivity::class.java, XlsxViewerActivity.EXTRA_FILE_PATH)
            // Archive/text: PHẢI tải về cache trước, vì ArchivePreviewActivity/CodeEditorActivity
            // đọc bằng đường dẫn file thật trên máy — giống hệt cách CloudBrowserActivity phải
            // downloadFile() vào cache trước khi mở 2 màn hình đó.
            ArchiveUtils.isArchive(entry.title) ->
                downloadThenOpen(entry, ArchivePreviewActivity::class.java, ArchivePreviewActivity.EXTRA_ARCHIVE_PATH)
            FileTypeUtils.isTextFileName(entry.title) ->
                downloadThenOpen(entry, CodeEditorActivity::class.java, CodeEditorActivity.EXTRA_FILE_PATH)
            else -> downloadToDevice(entry)
        }
    }

    /** Mở file từ xa bằng chính màn hình xem ảnh/video có sẵn trong app — không tải về máy. */
    private fun playRemoteMedia(url: String, name: String, isVideo: Boolean) {
        val intent = Intent(this, MediaViewerActivity::class.java).apply {
            putStringArrayListExtra(MediaViewerActivity.EXTRA_URIS, arrayListOf(url))
            putStringArrayListExtra(MediaViewerActivity.EXTRA_NAMES, arrayListOf(name))
            putStringArrayListExtra(MediaViewerActivity.EXTRA_REAL_PATHS, arrayListOf<String>())
            putExtra(MediaViewerActivity.EXTRA_IS_VIDEO, booleanArrayOf(isVideo))
            putExtra(MediaViewerActivity.EXTRA_START_POSITION, 0)
        }
        startActivity(intent)
    }

    private fun playRemoteAudio(url: String, name: String) {
        val intent = Intent(this, AudioPlayerActivity::class.java).apply {
            putStringArrayListExtra(AudioPlayerActivity.EXTRA_URIS, arrayListOf(url))
            putStringArrayListExtra(AudioPlayerActivity.EXTRA_NAMES, arrayListOf(name))
            putExtra(AudioPlayerActivity.EXTRA_START_INDEX, 0)
        }
        startActivity(intent)
    }

    /** Tải 1 file DLNA về cache rồi mở bằng activity đích (ArchivePreviewActivity/CodeEditorActivity). */
    private fun downloadThenOpen(entry: RemoteDidlItem, target: Class<*>, extraKey: String) {
        val url = entry.resUrl ?: return
        val tempFile = File(cacheDir, "dlna_preview_${System.currentTimeMillis()}_${entry.title}")
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = RemoteContentDirectoryClient.downloadToFile(url, tempFile)
            binding.progress.visibility = View.GONE
            if (result.isFailure) {
                Toast.makeText(this@RemoteServersActivity, getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
                return@launch
            }
            val intent = Intent(this@RemoteServersActivity, target).apply {
                putExtra(extraKey, tempFile.path)
            }
            startActivity(intent)
        }
    }

    /** Tải 1 file DLNA về thư mục Tải xuống của app trên máy — "bê" tính năng Tải về từ Cloud sang DLNA. */
    private fun downloadToDevice(entry: RemoteDidlItem) {
        val url = entry.resUrl ?: return
        val destDir = getExternalFilesDir(null) ?: filesDir
        val destFile = File(destDir, entry.title)
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = RemoteContentDirectoryClient.downloadToFile(url, destFile)
            binding.progress.visibility = View.GONE
            if (result.isFailure) {
                Toast.makeText(this@RemoteServersActivity, getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@RemoteServersActivity, getString(R.string.btn_download), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------------- menu "..." mỗi dòng file (đồng bộ với Cloud) ----------------

    private fun showEntryMenu(entry: RemoteDidlItem, anchor: View) {
        val popup = android.widget.PopupMenu(this, anchor)
        popup.menu.add(getString(R.string.btn_download))
        popup.menu.add(getString(R.string.btn_share))
        popup.menu.add(getString(R.string.btn_details))
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                getString(R.string.btn_download) -> downloadToDevice(entry)
                getString(R.string.btn_share) -> shareRemoteLink(entry)
                getString(R.string.btn_details) -> showEntryDetails(entry)
            }
            true
        }
        popup.show()
    }

    /**
     * "Chia sẻ" 1 file DLNA = chia sẻ thẳng URL streaming HTTP của nó (giống Cloud share link) —
     * KHÔNG tạo link mới vì URL DLNA vốn đã là link truy cập trực tiếp trong mạng LAN hiện tại,
     * khác Cloud (phải gọi API tạo link chia sẻ công khai riêng).
     */
    private fun shareRemoteLink(entry: RemoteDidlItem) {
        val url = entry.resUrl
        if (url.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.remote_open_failed), Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(intent, null))
    }

    /** DLNA không có "Đường dẫn" hệ thống file thật (chỉ có objectId nội bộ của máy chủ họ) —
     *  dùng tên máy chủ hiện tại thay cho đường dẫn, giữ đúng format chung file_details_format. */
    private fun showEntryDetails(entry: RemoteDidlItem) {
        val serverName = currentServer?.friendlyName ?: "-"
        val message = getString(R.string.file_details_format, entry.title, serverName, "-", "-")
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.btn_details))
            .setMessage(message)
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }
}
