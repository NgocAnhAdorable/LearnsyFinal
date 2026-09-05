package com.learnsypro.app.filemanager

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.learnsypro.app.R
import com.learnsypro.app.filemanager.client.RemoteClient
import com.learnsypro.app.databinding.ActivityFtpConnectionBinding
import com.learnsypro.app.filemanager.model.ConnectionType
import com.learnsypro.app.filemanager.model.FtpConnectionProfile
import com.learnsypro.app.filemanager.util.ActivityTransitions
import com.learnsypro.app.filemanager.util.SecurePrefs
import kotlinx.coroutines.launch

class FtpConnectionActivity : LearnsyFileManagerActivity() {

    private lateinit var binding: ActivityFtpConnectionBinding
    private lateinit var prefs: SecurePrefs
    // ID của kết nối đang SỬA — null nếu đây là màn "Thêm kết nối mới". Khi khác
    // null, lưu phải cập nhật ĐÚNG bản ghi có id này (kể cả khi người dùng đổi
    // host/username lúc sửa), không được tạo thêm bản ghi mới hay dựa vào so khớp
    // host+username+type+smbShareName như luồng tạo mới — so khớp theo field dễ tạo
    // bản ghi trùng nếu người dùng sửa xong đổi cả host lẫn username cùng lúc.
    private var editingConnectionId: String? = null

    private val qrScanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            applyQrResult(result.data)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFtpConnectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = SecurePrefs.getInstance(this)

        binding.toolbar.setNavigationOnClickListener {
            finish()
            ActivityTransitions.backward(this)
        }

        binding.chipGroupType.setOnCheckedStateChangeListener { _, _ -> updateFieldsForType() }
        updateFieldsForType()

        binding.btnScanLan.setOnClickListener { scanLan() }
        binding.btnScanQr.setOnClickListener {
            qrScanLauncher.launch(Intent(this, QrScannerActivity::class.java))
        }
        binding.btnConnect.setOnClickListener { attemptConnect() }
        binding.btnSaveOnly.setOnClickListener { saveWithoutTesting() }

        val editId = intent.getStringExtra(EXTRA_EDIT_CONNECTION_ID)
        if (editId != null) {
            val existing = prefs.loadConnections().firstOrNull { it.id == editId }
            if (existing != null) {
                editingConnectionId = editId
                populateFormForEdit(existing)
                binding.toolbar.title = getString(R.string.title_edit_connection)
                // Ở chế độ sửa, người dùng có thể chỉ muốn sửa lại thông tin sai (ví
                // dụ IP/port) mà chưa chắc kết nối thử ngay lúc đó sẽ thành công (ví
                // dụ đang đổi từ Passive sang Active Mode để né lỗi router) — cho phép
                // lưu thẳng không bắt buộc phải kết nối thành công trước.
                binding.btnSaveOnly.visibility = View.VISIBLE
            }
        }
    }

    /** Điền sẵn toàn bộ field từ 1 kết nối đã lưu — dùng khi mở màn ở chế độ Sửa. */
    private fun populateFormForEdit(conn: FtpConnectionProfile) {
        val chipId = when (conn.type) {
            ConnectionType.FTP -> binding.chipFtp.id
            ConnectionType.SFTP -> binding.chipSftp.id
            ConnectionType.SMB -> binding.chipSmb.id
        }
        binding.chipGroupType.check(chipId)
        binding.etHost.setText(conn.host)
        binding.etPort.setText(conn.port.toString())
        binding.etUsername.setText(conn.username)
        binding.etPassword.setText(conn.password)
        binding.etSmbShare.setText(conn.smbShareName)
        binding.etSmbDomain.setText(conn.smbDomain)
        binding.switchActiveMode.isChecked = !conn.passiveMode
        updateFieldsForType()
    }

    /** Điền form kết nối từ kết quả trả về của QrScannerActivity. */
    private fun applyQrResult(data: Intent?) {
        data ?: return
        val host = data.getStringExtra(QrScannerActivity.EXTRA_HOST) ?: return
        val port = data.getIntExtra(QrScannerActivity.EXTRA_PORT, 21)
        val username = data.getStringExtra(QrScannerActivity.EXTRA_USERNAME).orEmpty()
        val password = data.getStringExtra(QrScannerActivity.EXTRA_PASSWORD).orEmpty()
        val typeName = data.getStringExtra(QrScannerActivity.EXTRA_TYPE) ?: ConnectionType.FTP.name
        val smbShare = data.getStringExtra(QrScannerActivity.EXTRA_SMB_SHARE).orEmpty()

        val chipId = when (ConnectionType.valueOf(typeName)) {
            ConnectionType.FTP -> binding.chipFtp.id
            ConnectionType.SFTP -> binding.chipSftp.id
            ConnectionType.SMB -> binding.chipSmb.id
        }
        binding.chipGroupType.check(chipId)
        binding.etHost.setText(host)
        binding.etPort.setText(port.toString())
        binding.etUsername.setText(username)
        binding.etPassword.setText(password)
        if (smbShare.isNotBlank()) {
            binding.etSmbShare.setText(smbShare)
        }
    }

    private fun selectedType(): ConnectionType = when (binding.chipGroupType.checkedChipId) {
        binding.chipSftp.id -> ConnectionType.SFTP
        binding.chipSmb.id -> ConnectionType.SMB
        else -> ConnectionType.FTP
    }

    /** Ẩn/hiện field riêng theo giao thức, và cập nhật cổng mặc định gợi ý khi đổi loại. */
    private fun updateFieldsForType() {
        val type = selectedType()
        binding.tilSmbShare.visibility = if (type == ConnectionType.SMB) View.VISIBLE else View.GONE
        binding.tilSmbDomain.visibility = if (type == ConnectionType.SMB) View.VISIBLE else View.GONE
        // Active/Passive Mode chỉ có ý nghĩa với FTP thường (không áp dụng SFTP — chạy
        // trên 1 kết nối SSH duy nhất, không có khái niệm data channel riêng; cũng không
        // áp dụng SMB — giao thức hoàn toàn khác).
        val activeModeRowVisibility = if (type == ConnectionType.FTP) View.VISIBLE else View.GONE
        binding.rowFtpActiveMode.visibility = activeModeRowVisibility
        binding.tvFtpActiveModeDesc.visibility = activeModeRowVisibility

        val currentPort = binding.etPort.text?.toString()?.toIntOrNull()
        val defaultPortsInUse = setOf(21, 22, 445)
        if (currentPort == null || currentPort in defaultPortsInUse) {
            binding.etPort.setText(
                when (type) {
                    ConnectionType.FTP -> "21"
                    ConnectionType.SFTP -> "22"
                    ConnectionType.SMB -> "445"
                }
            )
        }
    }

    /** Dò các host đang mở cổng FTP/SFTP/SMB trong mạng LAN hiện tại, để chọn nhanh thay vì gõ tay IP. */
    private fun scanLan() {
        binding.progress.visibility = View.VISIBLE
        binding.btnScanLan.isEnabled = false
        val type = selectedType()
        lifecycleScope.launch {
            val found = com.learnsypro.app.filemanager.util.LanScanner.scan(this@FtpConnectionActivity, type)
            binding.progress.visibility = View.GONE
            binding.btnScanLan.isEnabled = true
            if (found.isEmpty()) {
                com.google.android.material.snackbar.Snackbar.make(binding.root, getString(R.string.no_lan_devices_found), com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show()
                return@launch
            }
            val labels = found.map { "${it.ip}:${it.port}" }.toTypedArray()
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this@FtpConnectionActivity)
                .setTitle(getString(R.string.title_lan_scan))
                .setItems(labels) { _, which ->
                    binding.etHost.setText(found[which].ip)
                    binding.etPort.setText(found[which].port.toString())
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    /** Đọc form hiện tại thành 1 FtpConnectionProfile, validate field bắt buộc.
     *  Trả về null nếu thiếu field (đã tự hiển thị lỗi lên đúng ô input). */
    private fun buildProfileFromForm(): FtpConnectionProfile? {
        val type = selectedType()
        val host = binding.etHost.text?.toString()?.trim().orEmpty()
        val port = binding.etPort.text?.toString()?.toIntOrNull() ?: 21
        val username = binding.etUsername.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()
        val smbShare = binding.etSmbShare.text?.toString()?.trim().orEmpty()
        val smbDomain = binding.etSmbDomain.text?.toString()?.trim().orEmpty()

        if (host.isEmpty()) {
            binding.etHost.error = getString(R.string.hint_host)
            return null
        }
        if (type == ConnectionType.SMB && smbShare.isEmpty()) {
            binding.etSmbShare.error = getString(R.string.hint_smb_share)
            return null
        }

        // Khi sửa 1 kết nối đã lưu, giữ nguyên ID cũ để ghi đè ĐÚNG bản ghi đó —
        // không tạo id ngẫu nhiên mới (FtpConnectionProfile() mặc định tự sinh id
        // mới nếu không truyền vào).
        val idToUse = editingConnectionId
        return FtpConnectionProfile(
            id = idToUse ?: java.util.UUID.randomUUID().toString(),
            name = if (type == ConnectionType.SMB) "$host/$smbShare" else host,
            host = host,
            port = port,
            username = username,
            password = password,
            type = type,
            // Switch hiển thị "Chế độ chủ động (Active Mode)" — khi BẬT nghĩa là dùng
            // Active Mode, nên passiveMode (mặc định true) phải là PHỦ ĐỊNH của switch.
            passiveMode = !binding.switchActiveMode.isChecked,
            smbShareName = smbShare,
            smbDomain = smbDomain
        )
    }

    /** Lưu profile vào danh sách kết nối đã lưu — ghi đè đúng bản ghi cũ nếu đang
     *  sửa (theo id), hoặc theo host+username+type+smbShareName nếu là tạo mới
     *  (tránh trùng khi người dùng kết nối thành công nhiều lần với cùng thông tin). */
    private fun persistProfile(profile: FtpConnectionProfile) {
        val existing = prefs.loadConnections()
        if (editingConnectionId != null) {
            existing.removeAll { it.id == editingConnectionId }
        } else {
            existing.removeAll { it.host == profile.host && it.username == profile.username && it.type == profile.type && it.smbShareName == profile.smbShareName }
        }
        existing.add(profile)
        prefs.saveConnections(existing)
    }

    /** Lưu thông tin chỉnh sửa mà KHÔNG bắt buộc kết nối thử phải thành công trước —
     *  chỉ hiện ở chế độ Sửa, cho phép sửa lại thông tin sai (host/port/Active Mode...)
     *  rồi lưu ngay, thử kết nối lại sau ở màn danh sách kết nối. */
    private fun saveWithoutTesting() {
        val profile = buildProfileFromForm() ?: return
        persistProfile(profile)
        finish()
        ActivityTransitions.backward(this)
    }

    private fun attemptConnect() {
        val profile = buildProfileFromForm() ?: return

        binding.progress.visibility = View.VISIBLE
        binding.btnConnect.isEnabled = false

        lifecycleScope.launch {
            val client = RemoteClient.forProfile(profile)
            val result = client.connect(profile)
            binding.progress.visibility = View.GONE
            binding.btnConnect.isEnabled = true

            if (result.isSuccess) {
                client.disconnect()
                persistProfile(profile)

                val intent = Intent(this@FtpConnectionActivity, FileBrowserActivity::class.java)
                intent.putExtra(FileBrowserActivity.EXTRA_CONNECTION_ID, profile.id)
                startActivity(intent)
                ActivityTransitions.forward(this@FtpConnectionActivity)
                finish()
            } else {
                binding.etHost.error = result.exceptionOrNull()?.message ?: getString(R.string.connect_failed)
            }
        }
    }

    companion object {
        const val EXTRA_EDIT_CONNECTION_ID = "extra_edit_connection_id"
    }
}
