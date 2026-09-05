package com.learnsypro.app.admin.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.learnsypro.app.admin.data.AdminAvatarRepository
import com.learnsypro.app.admin.ui.ToastCenter
import kotlinx.coroutines.launch

// Tương đương avatarInputRef + doUploadAvatar() trong student-manager.jsx.
class AvatarUiState {
    var urls by mutableStateOf<Map<String, String>>(emptyMap())
        internal set
    var uploadingId by mutableStateOf<String?>(null)
        internal set

    // Gọi khi tải xong signed URL cho 1 học sinh (vd. lúc load danh sách) —
    // merge vào map thay vì ghi đè, để không mất url của học sinh khác.
    fun putUrl(studentId: String, url: String) {
        urls = urls + (studentId to url)
    }
}

@Composable
fun rememberAvatarUiState(): Pair<AvatarUiState, (String) -> Unit> {
    val context = LocalContext.current
    val repo = remember { AdminAvatarRepository(context) }
    val state = remember { AvatarUiState() }
    val scope = rememberCoroutineScope()
    var pendingTargetId by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val targetId = pendingTargetId
        pendingTargetId = null
        if (uri == null || targetId == null) return@rememberLauncherForActivityResult
        scope.launch {
            state.uploadingId = targetId
            try {
                val resized = repo.resizeSquare(uri)
                val err = repo.validate(resized)
                if (err != null) {
                    ToastCenter.show(err, "⚠️", Color(0xFFF59E0B))
                    state.uploadingId = null
                    return@launch
                }
                repo.upload(targetId, resized)
                val signedUrl = repo.getSignedUrl(targetId)
                if (signedUrl != null) {
                    state.urls = state.urls + (targetId to signedUrl)
                }
                ToastCenter.show("Đã cập nhật ảnh đại diện!", "✅", Color(0xFF10B981))
            } catch (e: Exception) {
                ToastCenter.show("Lỗi upload ảnh!", "❌", Color(0xFFEF4444))
            } finally {
                state.uploadingId = null
            }
        }
    }

    val requestPick: (String) -> Unit = { studentId ->
        pendingTargetId = studentId
        launcher.launch("image/*")
    }

    return state to requestPick
}

// Tương đương loadAvatar() cho từng học sinh — gọi khi list load xong
suspend fun loadAvatarUrl(repo: AdminAvatarRepository, studentId: String): String? =
    repo.getSignedUrl(studentId)
