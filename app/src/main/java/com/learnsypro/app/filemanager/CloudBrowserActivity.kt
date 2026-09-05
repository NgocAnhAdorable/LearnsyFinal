package com.learnsypro.app.filemanager

import android.content.Intent
import androidx.activity.addCallback
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.learnsypro.app.R
import com.learnsypro.app.filemanager.cloud.CachedCloudFileService
import com.learnsypro.app.filemanager.cloud.CloudServiceFactory
import com.learnsypro.app.databinding.ActivityFileBrowserBinding
import com.learnsypro.app.filemanager.model.CloudProvider
import com.learnsypro.app.filemanager.model.RemoteFile
import com.learnsypro.app.filemanager.adapters.RemoteFileAdapter
import com.learnsypro.app.filemanager.util.ActivityTransitions
import com.learnsypro.app.filemanager.util.ArchiveUtils
import com.learnsypro.app.filemanager.widget.StoragePillView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Duyệt, tải lên/xuống, xóa, tạo thư mục trên tài khoản đám mây đã liên kết
 * (Google Drive / Dropbox / Box). Dùng chung layout với FileBrowserActivity.
 */
class CloudBrowserActivity : LearnsyFileManagerActivity() {

    private lateinit var binding: ActivityFileBrowserBinding
    private lateinit var service: CachedCloudFileService
    private lateinit var adapter: RemoteFileAdapter
    private lateinit var provider: CloudProvider

    // Ngăn xếp id thư mục cha để hỗ trợ nút back giữa các cấp thư mục cloud
    private val folderStack = ArrayDeque<Pair<String, String>>() // (folderId, tên hiển thị)
    private var currentFolderId = ""

    // Giống FileBrowserActivity: lưu danh sách gốc, search/sort áp cục bộ không gọi lại API.
    private var rawFiles: List<RemoteFile> = emptyList()
    private var searchQuery: String = ""
    private var sortMode = CloudSortMode.NAME
    private var sortAscending = true

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { uploadFromUri(it) }
    }

    // Màn hình cấp quyền Google Drive (UserRecoverableAuthIOException.intent) — sau khi người
    // dùng bấm "Cho phép" ở đây, tự động thử tải lại thư mục hiện tại thay vì bắt bấm lại nút.
    private val driveConsentLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            com.learnsypro.app.filemanager.util.LogBus.success("Đã cấp quyền Google Drive, đang tải lại", source = "CLOUD")
            loadCurrentFolder()
        } else {
            com.learnsypro.app.filemanager.util.LogBus.warning("Người dùng từ chối cấp quyền Google Drive", source = "CLOUD")
            showError(getString(R.string.error_generic))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Đồng bộ với CategoryFilesActivity: cộng thêm padding-top đúng chiều cao status bar
        // để toolbar không bị đồng hồ/status bar hệ thống đè lên khi vẽ edge-to-edge.
        com.learnsypro.app.filemanager.util.WindowInsetsUtils.applyTopInsetHeight(binding.statusBarSpacer)

        // 2 thanh nút cố định sát đáy (Tải lên/Tạo mới, và thanh chọn nhiều Sao chép/Di
        // chuyển/Xóa) đều dùng padding 12dp cứng trong XML — trên máy có thanh điều hướng
        // hệ thống (3 nút hoặc cử chỉ), phần dưới nút có thể bị che một phần. Cộng thêm
        // đúng chiều cao system bar tại runtime để nút bấm cuối luôn nằm trong vùng chạm an toàn.
        com.learnsypro.app.filemanager.util.WindowInsetsUtils.applyBottomInsetPadding(binding.bottomActionBar)
        com.learnsypro.app.filemanager.util.WindowInsetsUtils.applyBottomInsetPadding(binding.selectionBar)

        val providerName = intent.getStringExtra(EXTRA_PROVIDER)
        provider = try {
            CloudProvider.valueOf(providerName ?: "")
        } catch (e: Exception) {
            finish()
            return
        }
        service = CloudServiceFactory.get(this, provider)
        // Không set toolbar.title ở đây nữa — loadCurrentFolder() bên dưới tự quyết định:
        // rỗng nếu mở thẳng vào gốc (storage_header đã hiện tên provider), có tên provider nếu
        // khác. Set cứng ở đây trước đây gây trùng chữ với tv_storage_title lúc mở màn ở gốc.
        binding.tvStorageTitle.text = providerDisplayName(provider)
        binding.toolbar.setNavigationOnClickListener { handleBack() }
        onBackPressedDispatcher.addCallback(this) { handleBack() }

        adapter = RemoteFileAdapter(
            onItemClick = { file -> onFileClick(file) },
            onMoreClick = { file, view -> showFileMenu(file, view) },
            selectionEnabled = true,
            onSelectionChanged = { selected -> updateSelectionBar(selected) },
            scope = lifecycleScope,
            getThumbnailRequest = { file -> service.getThumbnailRequest(file) }
        )
        binding.rvFiles.layoutManager = LinearLayoutManager(this)
        binding.rvFiles.adapter = adapter

        // Kéo-để-làm-mới là cách DUY NHẤT người dùng chủ động báo "tôi biết dữ liệu có thể đã
        // đổi, bỏ qua cache đi" — nên đây là chỗ duy nhất gọi forceRefresh=true. Mọi điều hướng
        // bình thường khác (mở thư mục, bấm Back, xoay màn hình) vẫn ưu tiên dùng cache.
        binding.swipeRefresh.setOnRefreshListener { loadCurrentFolder(forceRefresh = true) }
        binding.btnUpload.setOnClickListener { filePickerLauncher.launch("*/*") }
        binding.btnNewFolder.setOnClickListener { showNewFolderDialog() }
        setupSelectionBar()

        setupSearchAndSort()

        loadCurrentFolder()
        loadQuota()
    }

    /** Gắn sự kiện cho thanh tìm kiếm (ẩn/hiện, gõ để lọc) và thanh sắp xếp (đổi tiêu chí, đổi chiều) — giống hệt FileBrowserActivity. */
    private fun setupSearchAndSort() {
        binding.btnSearch.setOnClickListener {
            binding.searchBar.visibility = View.VISIBLE
            binding.etSearch.requestFocus()
        }
        binding.btnCloseSearch.setOnClickListener {
            binding.searchBar.visibility = View.GONE
            binding.etSearch.setText("")
            searchQuery = ""
            applyFilterAndSort()
        }
        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString().orEmpty()
                binding.btnClearSearch.visibility = if (searchQuery.isEmpty()) View.GONE else View.VISIBLE
                applyFilterAndSort()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        binding.btnClearSearch.setOnClickListener { binding.etSearch.setText("") }

        binding.btnSortBy.setOnClickListener { showSortMenu() }
        binding.btnSortDirection.setOnClickListener {
            sortAscending = !sortAscending
            updateSortDirectionIcon()
            applyFilterAndSort()
        }
        updateSortDirectionIcon()
    }

    private fun showSortMenu() {
        val popup = android.widget.PopupMenu(this, binding.btnSortBy)
        popup.menu.add(0, 0, 0, getString(R.string.sort_by_name))
        popup.menu.add(0, 1, 1, getString(R.string.sort_by_size))
        popup.menu.add(0, 2, 2, getString(R.string.sort_by_date))
        popup.setOnMenuItemClickListener { item ->
            sortMode = when (item.itemId) {
                1 -> CloudSortMode.SIZE
                2 -> CloudSortMode.DATE
                else -> CloudSortMode.NAME
            }
            binding.tvSortBy.text = item.title
            applyFilterAndSort()
            true
        }
        popup.show()
    }

    private fun updateSortDirectionIcon() {
        binding.btnSortDirection.rotation = if (sortAscending) 0f else 180f
    }

    /** Lọc theo [searchQuery] rồi sắp xếp theo [sortMode]/[sortAscending] — thư mục luôn đứng trước file. */
    private fun applyFilterAndSort() {
        var result = rawFiles
        if (searchQuery.isNotBlank()) {
            result = result.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
        val comparator = when (sortMode) {
            CloudSortMode.NAME -> compareBy<RemoteFile> { it.name.lowercase() }
            CloudSortMode.SIZE -> compareBy { it.size }
            CloudSortMode.DATE -> compareBy { it.modifiedTime }
        }
        val directionalComparator = if (sortAscending) comparator else comparator.reversed()
        result = result.sortedWith(compareByDescending<RemoteFile> { it.isDirectory }.then(directionalComparator))
        adapter.submit(result)
        binding.tvEmpty.visibility = if (result.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Hiển thị pill mini dung lượng đã dùng/tổng của tài khoản cloud (StoragePillView, đồng bộ
     *  kiểu hiển thị "xanh = đã dùng, xám = trống" với pill Bộ nhớ trong/Thẻ nhớ SD ở trang chính). */
    private fun loadQuota() {
        lifecycleScope.launch {
            val result = service.getStorageQuota()
            if (result.isSuccess) {
                val quota = result.getOrNull() ?: return@launch
                if (quota.totalBytes <= 0) return@launch // provider không cung cấp tổng dung lượng (vd: "không giới hạn")
                binding.quotaBarContainer.visibility = View.VISIBLE
                binding.quotaPillBg.setUsage(
                    quota.usedBytes, quota.totalBytes,
                    getString(R.string.home_storage_detail, formatSize(quota.usedBytes), formatSize(quota.totalBytes))
                )
                // Đồng bộ với header lớn "Bộ nhớ trong / 46,4 GB trống" ở CategoryFilesActivity —
                // dùng đúng dòng dung lượng đã có sẵn (usedBytes/totalBytes) làm phụ đề của
                // storage_header, chỉ khác text chính là tên provider thay vì "Bộ nhớ trong".
                binding.tvStorageFree.text = getString(R.string.home_storage_detail, formatSize(quota.usedBytes), formatSize(quota.totalBytes))
            }
            // Thất bại thì im lặng bỏ qua — không phải provider nào cũng cho phép đọc quota
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val safeGroup = digitGroups.coerceIn(0, units.size - 1)
        return java.text.DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, safeGroup.toDouble())) + " " + units[safeGroup]
    }

    private fun providerDisplayName(p: CloudProvider): String = when (p) {
        CloudProvider.GOOGLE_DRIVE -> getString(R.string.cloud_google_drive)
        CloudProvider.DROPBOX -> getString(R.string.cloud_dropbox)
        CloudProvider.BOX -> getString(R.string.cloud_box)
    }

    private fun loadCurrentFolder(forceRefresh: Boolean = false) {
        binding.tvCurrentPath.text = if (folderStack.isEmpty()) "/" else "/" + folderStack.joinToString("/") { pair -> pair.second }
        // Header lớn (tên provider + dung lượng) chỉ hiện ở thư mục GỐC, giống hệt hành vi
        // storage_header của CategoryFilesActivity (chỉ hiện khi duyệt Bộ nhớ trong ở "/") —
        // vào thư mục con thì ẩn đi để nhường chỗ, tránh choán mất không gian danh sách file.
        val atRoot = folderStack.isEmpty()
        binding.storageHeader.visibility = if (atRoot) View.VISIBLE else View.GONE
        // BUG ĐÃ SỬA: toolbar.title trước đây luôn = providerDisplayName(provider) bất kể có
        // đang hiện storage_header hay không -> 2 dòng chữ TÊN PROVIDER giống hệt nhau chồng lên
        // nhau ngay dưới status bar (chữ nhỏ của toolbar + chữ lớn 26sp của tv_storage_title),
        // nhìn như 1 dòng chữ bị lem/mờ 2 lớp dù màn hình đứng yên — đúng lỗi gặp ở màn Bộ nhớ
        // trong đã sửa trước đó. Không đụng title nếu đang chọn nhiều (updateSelectionBar tự lo).
        if (!adapter.isSelectionMode) binding.toolbar.title = if (atRoot) "" else providerDisplayName(provider)
        showLoading(true)
        lifecycleScope.launch {
            val result = if (forceRefresh) {
                service.listFilesForceRefresh(currentFolderId)
            } else {
                service.listFiles(currentFolderId)
            }
            binding.swipeRefresh.isRefreshing = false
            showLoading(false)
            if (result.isSuccess) {
                val files = result.getOrDefault(emptyList())
                rawFiles = files
                applyFilterAndSort()
                binding.rvFiles.scheduleLayoutAnimation()
            } else {
                val ex = result.exceptionOrNull()
                // Google Drive scope drive.file luôn cần 1 lần consent riêng ở lệnh gọi API đầu
                // tiên (khác với bước "Đăng nhập bằng Google" ban đầu) — trước đây exception này
                // rơi vào nhánh lỗi chung, hiện "key error" khó hiểu và không có cách nào để
                // người dùng tự cấp quyền tiếp; giờ bung đúng màn hình cấp quyền của Google.
                if (ex is com.learnsypro.app.filemanager.cloud.GoogleDriveService.NeedsUserConsentException) {
                    driveConsentLauncher.launch(ex.intent)
                } else {
                    showError(ex?.message ?: getString(R.string.error_generic))
                }
            }
        }
    }

    /**
     * Chạm 1 lần vào file trên Cloud: trước đây LUÔN tải file về máy (destDir) dù là loại xem
     * được ngay - giờ ưu tiên XEM TRƯỚC trong app cho pdf/docx/xlsx/zip/rar/7z, dùng CHUNG các
     * màn hình viewer đang phục vụ Bộ nhớ trong (PdfViewerActivity/DocxViewerActivity/
     * XlsxViewerActivity/ArchivePreviewActivity) để giao diện xem giống hệt nhau. Các loại khác
     * (ảnh/video/audio/còn lại) vẫn tải về như cũ vì chưa có viewer riêng.
     */
    private fun onFileClick(file: RemoteFile) {
        if (file.isDirectory) {
            folderStack.addLast(currentFolderId to file.name)
            currentFolderId = file.cloudFileId ?: file.path
            clearSearchOnNavigate()
            loadCurrentFolder()
            return
        }
        val ext = file.name.substringAfterLast('.', "").lowercase()
        val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        val isImage = mime?.startsWith("image/") == true
        val isVideo = mime?.startsWith("video/") == true
        val isAudio = mime?.startsWith("audio/") == true
        when {
            // Bê nguyên cấu trúc nhận diện loại file từ CategoryFilesActivity.openFile() (màn
            // bộ nhớ trong) sang đây — trước đó CloudBrowserActivity chỉ nhận diện pdf/docx/xlsx/
            // archive, MỌI loại khác (ảnh, video, audio, txt...) rơi vào downloadFile() im lặng,
            // đúng lỗi "bấm vào file không mở được" (ảnh .png chỉ tự tải xuống, không có gì hiện
            // ra cho người dùng thấy). Khác với local (biết trước cả thư mục để vuốt qua lại
            // nhiều ảnh/video liên tiếp), cloud phải tải trước rồi mới mở — nên chỉ mở ĐÚNG 1
            // file đang bấm, không vuốt sang file cloud khác chưa tải.
            isImage || isVideo -> previewMediaFile(file, isVideo)
            isAudio -> previewAudioFile(file)
            ext == "pdf" -> previewCloudFile(file, PdfViewerActivity::class.java, PdfViewerActivity.EXTRA_FILE_PATH)
            ext == "docx" -> previewCloudFile(file, DocxViewerActivity::class.java, DocxViewerActivity.EXTRA_FILE_PATH)
            ext == "xlsx" -> previewCloudFile(file, XlsxViewerActivity::class.java, XlsxViewerActivity.EXTRA_FILE_PATH)
            ArchiveUtils.isArchive(file.name) ->
                previewCloudFile(file, ArchivePreviewActivity::class.java, ArchivePreviewActivity.EXTRA_ARCHIVE_PATH)
            // File text/code (json, kts, xml, txt, log...) — tải về cache rồi mở bằng
            // CodeEditorActivity để xem NỘI DUNG thật ngay trong app, giống hệt Bộ nhớ trong,
            // thay vì rơi vào downloadFile() im lặng như trước (chỉ tải về, không hiện gì).
            com.learnsypro.app.filemanager.util.FileTypeUtils.isTextFileName(file.name) ->
                previewCloudFile(file, CodeEditorActivity::class.java, CodeEditorActivity.EXTRA_FILE_PATH)
            else -> downloadFile(file)
        }
    }

    /** Ảnh: tải về cache rồi mở bằng MediaViewerActivity (ảnh phải tải hết mới hiển thị được,
     *  không có khái niệm "stream" cho ảnh tĩnh). Video: thử phát trực tiếp (streaming) trước
     *  qua getStreamRequest(), chỉ tải về khi provider không hỗ trợ (vd Google Docs/Sheets/Slides
     *  gốc không export được sang stream) — chỉ 1 mục (không vuốt qua file cloud khác chưa tải). */
    private fun previewMediaFile(file: RemoteFile, isVideo: Boolean) {
        if (isVideo) {
            // Kiểm tra hỗ trợ stream TRƯỚC khi register — tránh đăng ký 1 source vô dụng vào
            // LocalStreamServer nếu ngay từ đầu provider đã không hỗ trợ (Google Docs gốc...).
            lifecycleScope.launch {
                val supportsStream = try { service.getStreamRequest(file) != null } catch (e: Exception) { false }
                if (supportsStream) {
                    val source = com.learnsypro.app.filemanager.streamproxy.sources.CloudRemoteSource(service, file, file.name)
                    val url = com.learnsypro.app.filemanager.streamproxy.LocalStreamServer.getInstance().register(source)
                    val intent = Intent(this@CloudBrowserActivity, MediaViewerActivity::class.java).apply {
                        putStringArrayListExtra(MediaViewerActivity.EXTRA_URIS, arrayListOf(url))
                        putStringArrayListExtra(MediaViewerActivity.EXTRA_NAMES, arrayListOf(file.name))
                        putStringArrayListExtra(MediaViewerActivity.EXTRA_REAL_PATHS, arrayListOf(""))
                        putExtra(MediaViewerActivity.EXTRA_IS_VIDEO, booleanArrayOf(true))
                        putExtra(MediaViewerActivity.EXTRA_START_POSITION, 0)
                    }
                    startActivity(intent)
                    return@launch
                }
                downloadThenPreviewMedia(file, isVideo = true)
            }
            return
        }
        downloadThenPreviewMedia(file, isVideo = false)
    }

    /** Đường cũ (tải về cache rồi mở): dùng cho ảnh luôn, và cho video khi provider không hỗ trợ stream. */
    private fun downloadThenPreviewMedia(file: RemoteFile, isVideo: Boolean) {
        val cloudId = file.cloudFileId ?: file.path
        val tempFile = File(cacheDir, "preview_${System.currentTimeMillis()}_${file.name}")
        showLoading(true)
        lifecycleScope.launch {
            val result = service.downloadFile(cloudId, tempFile)
            showLoading(false)
            if (result.isFailure) {
                showError(result.exceptionOrNull()?.message ?: getString(R.string.error_generic))
                return@launch
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this@CloudBrowserActivity, "$packageName.fileprovider", tempFile
            )
            val intent = Intent(this@CloudBrowserActivity, MediaViewerActivity::class.java).apply {
                putStringArrayListExtra(MediaViewerActivity.EXTRA_URIS, arrayListOf(uri.toString()))
                putStringArrayListExtra(MediaViewerActivity.EXTRA_NAMES, arrayListOf(file.name))
                putStringArrayListExtra(MediaViewerActivity.EXTRA_REAL_PATHS, arrayListOf(tempFile.path))
                putExtra(MediaViewerActivity.EXTRA_IS_VIDEO, booleanArrayOf(isVideo))
                putExtra(MediaViewerActivity.EXTRA_START_POSITION, 0)
            }
            startActivity(intent)
        }
    }

    /** Thử phát trực tiếp (streaming) audio qua getStreamRequest() trước — chỉ rơi về tải-rồi-mở
     *  khi provider không hỗ trợ (Google Docs/Sheets/Slides gốc). Không có danh sách phát kế
     *  tiếp từ cloud, chỉ 1 bài đang bấm. */
    private fun previewAudioFile(file: RemoteFile) {
        lifecycleScope.launch {
            val supportsStream = try { service.getStreamRequest(file) != null } catch (e: Exception) { false }
            if (supportsStream) {
                val source = com.learnsypro.app.filemanager.streamproxy.sources.CloudRemoteSource(service, file, file.name)
                val url = com.learnsypro.app.filemanager.streamproxy.LocalStreamServer.getInstance().register(source)
                val intent = Intent(this@CloudBrowserActivity, AudioPlayerActivity::class.java).apply {
                    putStringArrayListExtra(AudioPlayerActivity.EXTRA_URIS, arrayListOf(url))
                    putStringArrayListExtra(AudioPlayerActivity.EXTRA_NAMES, arrayListOf(file.name))
                    putExtra(AudioPlayerActivity.EXTRA_START_INDEX, 0)
                }
                startActivity(intent)
                return@launch
            }
            downloadThenPlayAudio(file)
        }
    }

    private fun downloadThenPlayAudio(file: RemoteFile) {
        val cloudId = file.cloudFileId ?: file.path
        val tempFile = File(cacheDir, "preview_${System.currentTimeMillis()}_${file.name}")
        showLoading(true)
        lifecycleScope.launch {
            val result = service.downloadFile(cloudId, tempFile)
            showLoading(false)
            if (result.isFailure) {
                showError(result.exceptionOrNull()?.message ?: getString(R.string.error_generic))
                return@launch
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this@CloudBrowserActivity, "$packageName.fileprovider", tempFile
            )
            val intent = Intent(this@CloudBrowserActivity, AudioPlayerActivity::class.java).apply {
                putStringArrayListExtra(AudioPlayerActivity.EXTRA_URIS, arrayListOf(uri.toString()))
                putStringArrayListExtra(AudioPlayerActivity.EXTRA_NAMES, arrayListOf(file.name))
                putExtra(AudioPlayerActivity.EXTRA_START_INDEX, 0)
            }
            startActivity(intent)
        }
    }

    /** Tải file cloud về 1 bản tạm trong cache rồi mở thẳng bằng viewer trong app - không lưu vào bộ nhớ máy. */
    private fun previewCloudFile(file: RemoteFile, target: Class<*>, extraKey: String) {
        val cloudId = file.cloudFileId ?: file.path
        val tempFile = File(cacheDir, "preview_${System.currentTimeMillis()}_${file.name}")
        showLoading(true)
        lifecycleScope.launch {
            val result = service.downloadFile(cloudId, tempFile)
            showLoading(false)
            if (result.isFailure) {
                showError(result.exceptionOrNull()?.message ?: getString(R.string.error_generic))
                return@launch
            }
            val intent = Intent(this@CloudBrowserActivity, target).apply {
                putExtra(extraKey, tempFile.path)
            }
            startActivity(intent)
            ActivityTransitions.forward(this@CloudBrowserActivity)
        }
    }

    private fun handleBack() {
        if (handleBackOrExitSelection()) return
        if (folderStack.isNotEmpty()) {
            val (parentId, _) = folderStack.removeLast()
            currentFolderId = parentId
            clearSearchOnNavigate()
            loadCurrentFolder()
        } else {
            finish()
            ActivityTransitions.backward(this)
        }
    }

    /** Đổi thư mục thì xoá ô tìm kiếm của thư mục cũ — tránh hiểu lầm đang lọc nhầm thư mục mới. */
    private fun clearSearchOnNavigate() {
        if (searchQuery.isNotEmpty()) {
            binding.etSearch.setText("")
            searchQuery = ""
        }
    }

    private fun showFileMenu(file: RemoteFile, anchor: View) {
        // Đồng bộ với menu file của Bộ nhớ trong (CategoryFilesActivity.showFileMenu): cùng thứ
        // tự Chia sẻ -> Nén/Giải nén -> Đổi tên -> Chi tiết -> Xóa. Không có "Mở bằng"/"Sao chép
        // đến"/"Di chuyển đến"/"Sao chép vào clipboard"/"Thêm vào màn hình chờ"/"Ghim"/"Ẩn" vì
        // đây là khái niệm chỉ có nghĩa với file THẬT nằm trên máy (đường dẫn cục bộ, shortcut hệ
        // thống, Unix dot-file...) — không áp dụng được cho 1 file sống trên tài khoản cloud.
        val popup = android.widget.PopupMenu(this, anchor)
        popup.menu.add(getString(R.string.btn_share))
        popup.menu.add(getString(R.string.btn_download))
        if (!file.isDirectory) {
            if (ArchiveUtils.isArchive(file.name)) {
                popup.menu.add(getString(R.string.btn_extract))
            } else {
                popup.menu.add(getString(R.string.btn_compress))
            }
        }
        popup.menu.add(getString(R.string.btn_rename))
        popup.menu.add(getString(R.string.btn_details))
        popup.menu.add(getString(R.string.btn_delete))
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                getString(R.string.btn_share) -> shareCloudFile(file)
                getString(R.string.btn_download) -> downloadFile(file)
                getString(R.string.btn_extract) -> extractCloudArchive(file)
                getString(R.string.btn_compress) -> compressCloudFile(file)
                getString(R.string.btn_rename) -> showCloudRenameDialog(file)
                getString(R.string.btn_details) -> showCloudFileDetails(file)
                getString(R.string.btn_delete) -> confirmDelete(file)
            }
            true
        }
        popup.show()
    }

    /**
     * Tạo link chia sẻ công khai (xem-được) qua API của provider rồi mở Android share sheet với
     * chính link đó — KHÔNG tải file về máy trước, khác hẳn shareFile() ở Bộ nhớ trong (share
     * thẳng nội dung file qua FileProvider vì file đã nằm sẵn trên máy).
     */
    private fun shareCloudFile(file: RemoteFile) {
        val cloudId = file.cloudFileId ?: file.path
        showLoading(true)
        lifecycleScope.launch {
            val result = service.getShareLink(cloudId)
            showLoading(false)
            val link = result.getOrNull()
            if (link == null) {
                showError(result.exceptionOrNull()?.message ?: getString(R.string.cloud_share_link_failed))
                return@launch
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, link)
            }
            startActivity(Intent.createChooser(intent, null))
        }
    }

    private fun showCloudRenameDialog(file: RemoteFile) {
        val cloudId = file.cloudFileId ?: file.path
        val input = android.widget.EditText(this).apply {
            setText(file.name)
            setSelection(0, file.name.substringBeforeLast('.', file.name).length)
            setPadding(48, 32, 48, 32)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.btn_rename))
            .setView(input)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isBlank() || newName == file.name) return@setPositiveButton
                showLoading(true)
                lifecycleScope.launch {
                    val result = service.renameFile(cloudId, newName)
                    showLoading(false)
                    if (result.isSuccess) {
                        loadCurrentFolder()
                    } else {
                        showError(result.exceptionOrNull()?.message ?: getString(R.string.cloud_rename_failed))
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    /**
     * Cloud không có "Đường dẫn" hệ thống file thật như local (path chỉ là tên/path_lower nội
     * bộ của provider) — dùng chính tên provider (Google Drive/Dropbox/Box) thay cho đường dẫn,
     * vẫn tái dùng đúng string format file_details_format để đồng bộ với Bộ nhớ trong.
     */
    private fun showCloudFileDetails(file: RemoteFile) {
        val sizeText = if (file.isDirectory) "—" else formatSizeDetail(file.size)
        val dateText = if (file.modifiedTime > 0) {
            java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(file.modifiedTime))
        } else {
            "—"
        }
        val message = getString(R.string.file_details_format, file.name, providerDisplayName(provider), sizeText, dateText)
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.btn_details))
            .setMessage(message)
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    private fun formatSizeDetail(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val safeGroup = digitGroups.coerceIn(0, units.size - 1)
        return java.text.DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, safeGroup.toDouble())) + " " + units[safeGroup]
    }

    /** Tải file .zip/.7z về, giải nén cục bộ, rồi upload từng file/thư mục con lên thư mục hiện tại trên cloud. */
    private fun extractCloudArchive(file: RemoteFile) {
        val cloudId = file.cloudFileId ?: file.path
        showLoading(true)
        lifecycleScope.launch {
            val tempArchive = File(cacheDir, file.name)
            val downloadResult = service.downloadFile(cloudId, tempArchive)
            if (downloadResult.isFailure) {
                showLoading(false)
                showError(getString(R.string.extract_failed))
                return@launch
            }
            val extractDir = File(cacheDir, "extract_${System.currentTimeMillis()}")
            val extractResult = withContext(Dispatchers.IO) {
                when {
                    ArchiveUtils.isZip(file.name) -> ArchiveUtils.unzip(tempArchive, extractDir)
                    ArchiveUtils.isRar(file.name) -> ArchiveUtils.unrar(tempArchive, extractDir)
                    else -> ArchiveUtils.un7z(tempArchive, extractDir)
                }
            }
            if (extractResult.isFailure) {
                showLoading(false)
                tempArchive.delete()
                showError(getString(R.string.extract_failed))
                return@launch
            }
            val ok = uploadDirectoryRecursive(extractDir, currentFolderId)
            showLoading(false)
            tempArchive.delete()
            extractDir.deleteRecursively()
            if (ok) {
                com.google.android.material.snackbar.Snackbar.make(binding.root, getString(R.string.extract_success), com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
                loadCurrentFolder()
            } else {
                showError(getString(R.string.extract_failed))
            }
        }
    }

    /**
     * Giới hạn độ sâu đệ quy: nếu file .zip/.7z tải về có cấu trúc thư mục lồng quá sâu (dữ liệu
     * lỗi, hoặc archive được tạo ra với ý đồ xấu kiểu "zip bomb" dạng thư mục lồng hàng nghìn
     * cấp), đệ quy không giới hạn trước đây có thể gây StackOverflowError -> crash. 64 cấp là dư
     * sức cho mọi cấu trúc thư mục thực tế của người dùng.
     */
    private suspend fun uploadDirectoryRecursive(localDir: File, parentId: String, depth: Int = 0): Boolean {
        if (depth > 64) return false
        val children = localDir.listFiles() ?: return true
        for (child in children) {
            if (child.isDirectory) {
                val createResult = service.createFolder(child.name, parentId)
                if (createResult.isFailure) return false
                // Cần id thư mục vừa tạo để đệ quy tiếp — với giới hạn interface hiện tại,
                // ta liệt kê lại thư mục cha để tìm id thư mục con vừa tạo theo tên.
                val listing = service.listFiles(parentId).getOrDefault(emptyList())
                val createdFolder = listing.firstOrNull { it.isDirectory && it.name == child.name }
                val childId = createdFolder?.cloudFileId ?: parentId
                if (!uploadDirectoryRecursive(child, childId, depth + 1)) return false
            } else {
                val result = service.uploadFile(child, parentId)
                if (result.isFailure) return false
            }
        }
        return true
    }

    /** Nén 1 file từ cloud: tải về, nén cục bộ, upload file .zip kết quả lên cùng thư mục. */
    private fun compressCloudFile(file: RemoteFile) {
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.hint_archive_name)
            setText(file.name.substringBeforeLast('.'))
            setPadding(48, 32, 48, 32)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.btn_compress))
            .setView(input)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val zipName = input.text.toString().trim().ifBlank { "archive" }.let { if (it.endsWith(".zip")) it else "$it.zip" }
                val cloudId = file.cloudFileId ?: file.path
                showLoading(true)
                lifecycleScope.launch {
                    val tempLocal = File(cacheDir, file.name)
                    val downloadResult = service.downloadFile(cloudId, tempLocal)
                    if (downloadResult.isFailure) {
                        showLoading(false)
                        showError(getString(R.string.compress_failed))
                        return@launch
                    }
                    val tempZip = File(cacheDir, zipName)
                    val zipResult = withContext(Dispatchers.IO) { ArchiveUtils.zip(listOf(tempLocal), tempZip) }
                    if (zipResult.isFailure) {
                        showLoading(false)
                        tempLocal.delete()
                        showError(getString(R.string.compress_failed))
                        return@launch
                    }
                    val uploadResult = service.uploadFile(tempZip, currentFolderId)
                    showLoading(false)
                    tempLocal.delete()
                    tempZip.delete()
                    if (uploadResult.isSuccess) {
                        com.google.android.material.snackbar.Snackbar.make(binding.root, getString(R.string.compress_success), com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
                        loadCurrentFolder()
                    } else {
                        showError(getString(R.string.compress_failed))
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun downloadFile(file: RemoteFile) {
        val cloudId = file.cloudFileId ?: file.path
        val destDir = getExternalFilesDir(null) ?: filesDir
        val destFile = File(destDir, file.name)
        showLoading(true)
        lifecycleScope.launch {
            val result = service.downloadFile(cloudId, destFile)
            showLoading(false)
            if (result.isFailure) {
                showError(result.exceptionOrNull()?.message ?: getString(R.string.error_generic))
            }
        }
    }

    /**
     * Bug giống hệt đã sửa ở FileBrowserActivity.uploadFromUri(): đọc file người dùng chọn (từ
     * SAF picker) đồng bộ trên main thread TRƯỚC KHI vào lifecycleScope.launch — với file lớn
     * (video, ảnh RAW...) treo UI hoặc ANR. Bị bỏ sót lúc sửa lần trước vì đây là bản sao riêng
     * cho luồng Cloud (Google Drive/Dropbox/Box), không dùng chung code với FileBrowserActivity.
     */
    private fun uploadFromUri(uri: Uri) {
        val name = queryFileName(uri) ?: "upload_${System.currentTimeMillis()}"
        val tempFile = File(cacheDir, name)
        showLoading(true)
        lifecycleScope.launch {
            val copyOk = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    true
                } catch (e: Exception) {
                    false
                }
            }
            if (!copyOk) {
                showLoading(false)
                showError(getString(R.string.error_generic))
                return@launch
            }
            val result = service.uploadFile(tempFile, currentFolderId)
            showLoading(false)
            tempFile.delete()
            if (result.isSuccess) {
                loadCurrentFolder()
            } else {
                showError(result.exceptionOrNull()?.message ?: getString(R.string.error_generic))
            }
        }
    }

    private fun queryFileName(uri: Uri): String? {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return it.getString(idx)
            }
        }
        return null
    }

    private fun confirmDelete(file: RemoteFile) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.btn_delete))
            .setMessage(file.name)
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                val cloudId = file.cloudFileId ?: file.path
                lifecycleScope.launch {
                    val result = service.deleteFile(cloudId)
                    if (result.isSuccess) loadCurrentFolder()
                    else showError(result.exceptionOrNull()?.message ?: getString(R.string.error_generic))
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showNewFolderDialog() {
        val input = android.widget.EditText(this)
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.btn_new_folder))
            .setView(input)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch {
                        val result = service.createFolder(name, currentFolderId)
                        if (result.isSuccess) loadCurrentFolder()
                        else showError(result.exceptionOrNull()?.message ?: getString(R.string.error_generic))
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        com.google.android.material.snackbar.Snackbar.make(binding.root, message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
    }

    /**
     * Bê hành vi thanh chọn nhiều từ CategoryFilesActivity (bộ nhớ trong) sang màn Cloud:
     * long-press 1 dòng để vào chế độ chọn -> thanh Tải lên/Tạo mới ẩn đi, thay bằng thanh
     * Sao chép/Di chuyển/Nén/Xóa. Bấm lại nút back hệ thống khi đang chọn sẽ thoát chế độ chọn
     * trước, không thoát màn luôn (qua onBackPressedDispatcher, xử lý trong handleBack()).
     */
    private fun setupSelectionBar() {
        binding.btnCopySelected.setOnClickListener { startCopyOrMove(isMove = false) }
        binding.btnMoveSelected.setOnClickListener { startCopyOrMove(isMove = true) }
        binding.btnCompressSelected.setOnClickListener { compressSelected() }
        binding.btnDeleteSelected.setOnClickListener { confirmDeleteSelected() }
    }

    private fun updateSelectionBar(selected: Set<String>) {
        val inSelectionMode = adapter.isSelectionMode
        binding.selectionBar.visibility = if (inSelectionMode) View.VISIBLE else View.GONE
        binding.btnUpload.visibility = if (inSelectionMode) View.GONE else View.VISIBLE
        binding.btnNewFolder.visibility = if (inSelectionMode) View.GONE else View.VISIBLE
        binding.toolbar.title = if (inSelectionMode && selected.isNotEmpty()) {
            getString(R.string.selected_count, selected.size)
        } else {
            // Thoát chế độ chọn: trả toolbar về đúng trạng thái trước đó — rỗng nếu đang ở thư
            // mục gốc (storage_header đã hiện tên provider rồi), có tên provider nếu đang ở
            // thư mục con. Không còn ghi đè cứng providerDisplayName() như trước (gây trùng
            // chữ với tv_storage_title khi thoát chọn ngay tại thư mục gốc).
            if (folderStack.isEmpty()) "" else providerDisplayName(provider)
        }
    }

    /** Đang chọn thì back trước hết thoát chế độ chọn — đúng hành vi quen thuộc của bộ nhớ trong. */
    private fun handleBackOrExitSelection(): Boolean {
        if (adapter.isSelectionMode) {
            adapter.exitSelectionMode()
            return true
        }
        return false
    }

    private var pendingSelectionCloudIds: List<Pair<String, Boolean>>? = null // (cloudFileId, isDirectory)
    private var pendingSelectionIsMove = false

    private val selectionFolderPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode != android.app.Activity.RESULT_OK || data == null) {
            pendingSelectionCloudIds = null
            return@registerForActivityResult
        }
        val destProvider = data.getStringExtra(FolderPickerActivity.EXTRA_RESULT_DEST_CLOUD_PROVIDER)
        val destFolderId = data.getStringExtra(FolderPickerActivity.EXTRA_RESULT_DEST_CLOUD_FOLDER_ID)
        val destPath = data.getStringExtra(FolderPickerActivity.EXTRA_RESULT_DEST_PATH)
        when {
            // Cùng provider hiện tại (vd đang ở Drive, chọn 1 thư mục khác trong chính Drive).
            destProvider == provider.name && destFolderId != null -> performCopyOrMoveWithinCloud(destFolderId)
            // Đích là bộ nhớ máy (Bộ nhớ trong/Thẻ nhớ SD) — trước đây rơi vào nhánh lỗi vì chưa
            // đọc EXTRA_RESULT_DEST_PATH dù FolderPickerActivity đã trả về đúng giá trị này.
            destPath != null -> performDownloadSelectedToDevice(destPath)
            // Đích là 1 provider cloud KHÁC (vd đang ở Drive, chọn thư mục bên Dropbox) — trước
            // đây cũng rơi vào nhánh lỗi vì performCopyOrMoveWithinCloud chỉ dùng "service" (luôn
            // là provider hiện tại của Activity này), không có đường nào gọi tới provider đích.
            destProvider != null && destFolderId != null -> {
                val destProviderEnum = try {
                    CloudProvider.valueOf(destProvider)
                } catch (e: IllegalArgumentException) {
                    null
                }
                if (destProviderEnum != null) {
                    performCopyOrMoveCrossCloud(destProviderEnum, destFolderId)
                } else {
                    showError(getString(R.string.error_generic))
                    pendingSelectionCloudIds = null
                }
            }
            else -> {
                showError(getString(R.string.error_generic))
                pendingSelectionCloudIds = null
            }
        }
    }

    private fun startCopyOrMove(isMove: Boolean) {
        val items = adapter.selectedItems()
        if (items.isEmpty()) return
        pendingSelectionCloudIds = items.map { (it.cloudFileId ?: it.path) to it.isDirectory }
        pendingSelectionIsMove = isMove
        val intent = Intent(this, FolderPickerActivity::class.java).apply {
            putExtra(FolderPickerActivity.EXTRA_IS_MOVE, isMove)
        }
        selectionFolderPickerLauncher.launch(intent)
    }

    /**
     * Tải các file cloud đã chọn về 1 thư mục trên bộ nhớ máy (Bộ nhớ trong/Thẻ nhớ SD). Dùng
     * chung service.downloadFile() sẵn có — khác performCopyOrMoveWithinCloud ở chỗ KHÔNG có
     * bước upload lại, chỉ tải về đúng tên gốc vào destPath. "Di chuyển" = tải xong rồi xoá bản
     * gốc trên cloud, giống quy ước isMove ở các luồng copy/move khác trong Activity này.
     * Thư mục con trong lựa chọn: bỏ qua (giống giới hạn hiện có ở performCopyOrMoveWithinCloud),
     * báo rõ số lượng bị bỏ qua thay vì âm thầm thiếu sót.
     *
     * TRƯỚC ĐÂY dùng showLoading(true) — 1 spinner mù che góc màn hình suốt từ lúc bắt đầu tới
     * khi TẤT CẢ file xong, không hiện đang xử lý file nào/còn bao nhiêu. Với nhiều file hoặc
     * file lớn (đặc biệt khi mạng chậm), người dùng thấy như app bị đơ/giật vì không có phản hồi
     * gì suốt thời gian dài. Đổi sang ProgressDialogHelper (đã dùng sẵn cho nén/giải nén) để hiện
     * rõ tên file đang tải + số thứ tự/tổng số, cùng cơ chế nhưng có phản hồi tức thời theo từng
     * bước thay vì im lặng tới cuối.
     */
    private fun performDownloadSelectedToDevice(destPath: String) {
        val items = pendingSelectionCloudIds ?: return
        val isMove = pendingSelectionIsMove
        pendingSelectionCloudIds = null
        val filesOnly = items.filter { !it.second }
        val skippedFolders = items.size - filesOnly.size

        val progress = com.learnsypro.app.filemanager.util.ProgressDialogHelper(
            this,
            if (isMove) R.string.progress_moving else R.string.progress_copying
        )
        lifecycleScope.launch {
            var successCount = 0
            withContext(Dispatchers.IO) {
                val destDir = File(destPath).apply { mkdirs() }
                filesOnly.forEachIndexed { index, (cloudId, _) ->
                    val realName = adapter.currentList.firstOrNull { (it.cloudFileId ?: it.path) == cloudId }?.name
                        ?: cloudId.substringAfterLast('/')
                    progress.setCurrentFile("$realName (${index + 1}/${filesOnly.size})")
                    val destFile = File(destDir, realName)
                    val dl = service.downloadFile(cloudId, destFile)
                    if (dl.isSuccess) {
                        successCount++
                        if (isMove) service.deleteFile(cloudId)
                    }
                    progress.update((index + 1).toLong(), filesOnly.size.toLong())
                }
            }
            progress.dismiss()
            adapter.exitSelectionMode()
            val allOk = successCount == filesOnly.size && skippedFolders == 0
            val msgRes = if (allOk) R.string.upload_success else R.string.upload_failed
            val suffix = if (skippedFolders > 0) " (${skippedFolders} thư mục con chưa được hỗ trợ, đã bỏ qua)" else ""
            showError(getString(msgRes) + suffix)
            if (isMove && successCount > 0) loadCurrentFolder()
        }
    }

    /**
     * Sao chép/di chuyển các file đã chọn sang 1 provider cloud KHÁC (vd Drive -> Dropbox). Cùng
     * kỹ thuật tải-về-tạm-rồi-tải-lên như performCopyOrMoveWithinCloud, chỉ khác: bước upload gọi
     * vào service của provider ĐÍCH (lấy qua CloudServiceFactory.get(), không phải "service" —
     * biến đó luôn là provider của chính CloudBrowserActivity này, tức provider NGUỒN).
     */
    private fun performCopyOrMoveCrossCloud(destProvider: CloudProvider, destFolderId: String) {
        val items = pendingSelectionCloudIds ?: return
        val isMove = pendingSelectionIsMove
        pendingSelectionCloudIds = null
        val filesOnly = items.filter { !it.second }
        val skippedFolders = items.size - filesOnly.size
        val destService = CloudServiceFactory.get(this, destProvider)

        // Cùng lý do đổi sang ProgressDialogHelper đã ghi ở performDownloadSelectedToDevice:
        // luồng này còn CHẬM HƠN (tải về tạm rồi upload lại, 2 lần round-trip mạng/file thay vì
        // 1) nên càng cần phản hồi rõ ràng đang xử lý gì thay vì 1 spinner mù kéo dài.
        val progress = com.learnsypro.app.filemanager.util.ProgressDialogHelper(
            this,
            if (isMove) R.string.progress_moving else R.string.progress_copying
        )
        lifecycleScope.launch {
            var successCount = 0
            withContext(Dispatchers.IO) {
                filesOnly.forEachIndexed { index, (cloudId, _) ->
                    val realName = adapter.currentList.firstOrNull { (it.cloudFileId ?: it.path) == cloudId }?.name ?: cloudId.substringAfterLast('/')
                    progress.setCurrentFile("$realName (${index + 1}/${filesOnly.size})")
                    val tempFile = File(cacheDir, "xfer_${System.currentTimeMillis()}_${cloudId.hashCode()}")
                    try {
                        val dl = service.downloadFile(cloudId, tempFile)
                        if (dl.isSuccess) {
                            val namedTemp = File(tempFile.parentFile, realName)
                            tempFile.renameTo(namedTemp)
                            val up = destService.uploadFile(namedTemp, destFolderId)
                            if (up.isSuccess) {
                                successCount++
                                if (isMove) service.deleteFile(cloudId)
                            }
                            namedTemp.delete()
                        }
                    } finally {
                        tempFile.delete()
                    }
                    progress.update((index + 1).toLong(), filesOnly.size.toLong())
                }
            }
            progress.dismiss()
            adapter.exitSelectionMode()
            val allOk = successCount == filesOnly.size && skippedFolders == 0
            val msgRes = if (allOk) R.string.upload_success else R.string.upload_failed
            val suffix = if (skippedFolders > 0) " (${skippedFolders} thư mục con chưa được hỗ trợ, đã bỏ qua)" else ""
            showError(getString(msgRes) + suffix)
            if (isMove && successCount > 0) loadCurrentFolder()
        }
    }

    /**
     * Sao chép/di chuyển NHIỀU file cùng lúc, CÙNG 1 provider cloud (Dropbox/Box/Drive không tự
     * có API "move" giữa 2 thư mục — cách khả thi duy nhất qua interface CloudFileService hiện
     * có là tải về cache tạm rồi upload lại vào thư mục đích, giống hệt cách CategoryFilesActivity
     * đã làm cho chiều local -> cloud). "Di chuyển" = sao chép xong rồi xóa bản gốc; thư mục con
     * không được hỗ trợ đệ quy ở bước này (khác performUploadToCloud phía local->cloud) — nếu có
     * thư mục trong lựa chọn, báo rõ và bỏ qua, chỉ xử lý các file lẻ, tránh làm nửa vời.
     */
    private fun performCopyOrMoveWithinCloud(destFolderId: String) {
        val items = pendingSelectionCloudIds ?: return
        val isMove = pendingSelectionIsMove
        pendingSelectionCloudIds = null
        val filesOnly = items.filter { !it.second }
        val skippedFolders = items.size - filesOnly.size

        val progress = com.learnsypro.app.filemanager.util.ProgressDialogHelper(
            this,
            if (isMove) R.string.progress_moving else R.string.progress_copying
        )
        lifecycleScope.launch {
            var successCount = 0
            withContext(Dispatchers.IO) {
                filesOnly.forEachIndexed { index, (cloudId, _) ->
                    val realName = adapter.currentList.firstOrNull { (it.cloudFileId ?: it.path) == cloudId }?.name ?: cloudId.substringAfterLast('/')
                    progress.setCurrentFile("$realName (${index + 1}/${filesOnly.size})")
                    val tempFile = File(cacheDir, "xfer_${System.currentTimeMillis()}_${cloudId.hashCode()}")
                    try {
                        val dl = service.downloadFile(cloudId, tempFile)
                        if (dl.isSuccess) {
                            val namedTemp = File(tempFile.parentFile, realName)
                            tempFile.renameTo(namedTemp)
                            val up = service.uploadFile(namedTemp, destFolderId)
                            if (up.isSuccess) {
                                successCount++
                                if (isMove) service.deleteFile(cloudId)
                            }
                            namedTemp.delete()
                        }
                    } finally {
                        tempFile.delete()
                    }
                    progress.update((index + 1).toLong(), filesOnly.size.toLong())
                }
            }
            progress.dismiss()
            adapter.exitSelectionMode()
            val allOk = successCount == filesOnly.size && skippedFolders == 0
            val msgRes = if (allOk) R.string.upload_success else R.string.upload_failed
            val suffix = if (skippedFolders > 0) " (${skippedFolders} thư mục con chưa được hỗ trợ, đã bỏ qua)" else ""
            showError(getString(msgRes) + suffix)
            if (isMove && successCount > 0) loadCurrentFolder()
        }
    }

    /**
     * Nén nhiều file đã chọn thành 1 file .zip rồi upload lên chính thư mục hiện tại. Cần tải hết
     * các file về cache trước (ArchiveUtils.zip chỉ nén được file cục bộ thật, không có API cloud
     * nào tự nén trực tiếp), nén xong upload lên rồi dọn cache — không hỗ trợ nén thư mục con
     * lồng nhau (giống giới hạn ở performCopyOrMoveWithinCloud).
     */
    private fun compressSelected() {
        val items = adapter.selectedItems().filter { !it.isDirectory }
        if (items.isEmpty()) {
            showError(getString(R.string.error_generic))
            return
        }
        showLoading(true)
        lifecycleScope.launch {
            val tempDir = File(cacheDir, "compress_${System.currentTimeMillis()}").apply { mkdirs() }
            val zipFile = File(cacheDir, "archive_${System.currentTimeMillis()}.zip")
            var uploadOk = false
            withContext(Dispatchers.IO) {
                try {
                    val localFiles = mutableListOf<File>()
                    items.forEach { file ->
                        val cloudId = file.cloudFileId ?: file.path
                        val local = File(tempDir, file.name)
                        if (service.downloadFile(cloudId, local).isSuccess) localFiles.add(local)
                    }
                    if (localFiles.isNotEmpty()) {
                        val zipResult = com.learnsypro.app.filemanager.util.ArchiveUtils.zip(localFiles, zipFile)
                        if (zipResult.isSuccess) {
                            uploadOk = service.uploadFile(zipFile, currentFolderId).isSuccess
                        }
                    }
                } finally {
                    tempDir.deleteRecursively()
                    zipFile.delete()
                }
            }
            showLoading(false)
            adapter.exitSelectionMode()
            showError(getString(if (uploadOk) R.string.compress_success else R.string.compress_failed))
            if (uploadOk) loadCurrentFolder()
        }
    }

    private fun confirmDeleteSelected() {
        val items = adapter.selectedItems()
        if (items.isEmpty()) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.btn_delete)
            .setMessage(getString(R.string.confirm_delete_cloud_count, items.size))
            .setPositiveButton(R.string.btn_delete) { _, _ -> deleteSelected(items) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteSelected(items: List<RemoteFile>) {
        showLoading(true)
        lifecycleScope.launch {
            var successCount = 0
            withContext(Dispatchers.IO) {
                items.forEach { file ->
                    val cloudId = file.cloudFileId ?: file.path
                    if (service.deleteFile(cloudId).isSuccess) successCount++
                }
            }
            showLoading(false)
            adapter.exitSelectionMode()
            val allOk = successCount == items.size
            showError(getString(if (allOk) R.string.delete_success else R.string.delete_failed))
            loadCurrentFolder()
        }
    }

    companion object {
        const val EXTRA_PROVIDER = "extra_cloud_provider"
    }
}

private enum class CloudSortMode { NAME, SIZE, DATE }
