package com.learnsypro.app.ui.dashboard

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.learnsypro.app.ui.theme.NunitoFontFamily
import com.learnsypro.app.ui.toast.ToastController
import com.learnsypro.app.ui.toast.ToastType
import kotlinx.coroutines.launch

/**
 * ── AvatarUploader ──
 * Tương đương function AvatarUploader({student,dark,avatarUrl,loading,onUpload,onRemove})
 * trong avatar.jsx. Bấm vào avatar để mở gallery chọn ảnh (thay cho <input type="file">), sau
 * đó mở màn hình crop tương tác (uCrop — pan/zoom, khung tròn cố định tỉ lệ 1:1) TRƯỚC khi
 * upload, thay vì center-crop cứng tự động như trước. Overlay spinner khi đang tải, badge
 * camera góc dưới phải, nút "Đổi ảnh"/"Xóa", feedback hiển thị qua ToastController dùng chung
 * toàn app (thay vì message cục bộ).
 *
 * Thêm ảnh bìa (cover photo): khu vực nền hình chữ nhật PHÍA SAU avatar, độc lập hoàn toàn với
 * avatar (bucket Supabase riêng "covers", xem CoverPhotoRepository) — bấm vào khu vực bìa (ngoài
 * vòng tròn avatar) để đổi, cũng qua crop tương tác (uCrop, khung chữ nhật tỉ lệ 16:9, KHÔNG
 * tròn) trước khi upload.
 *
 * @param displayName tên hiển thị (student.display_name || student.username)
 * @param onUpload callback trả về AvatarUploadResult tương tự bản web {ok,size,msg}
 * @param onRemove callback xóa avatar
 * @param coverUrl URL ảnh bìa hiện tại, null nếu chưa có
 * @param coverLoading true khi đang upload ảnh bìa
 * @param onCoverUpload callback nhận URI ảnh bìa ĐÃ CROP SẴN (không phải ảnh gốc — crop xảy ra
 *   trong chính Composable này qua uCrop trước khi gọi callback)
 * @param onCoverRemove callback xóa ảnh bìa
 */
@Composable
fun AvatarUploader(
    displayName: String,
    dark: Boolean,
    avatarUrl: String?,
    loading: Boolean,
    onUpload: suspend (Uri) -> Pair<Boolean, String?>,
    onRemove: suspend () -> Unit,
    coverUrl: String? = null,
    coverLoading: Boolean = false,
    onCoverUpload: suspend (Uri) -> Pair<Boolean, String?> = { _ -> false to null },
    onCoverRemove: suspend () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var preview by remember { mutableStateOf<Uri?>(null) }
    var removing by remember { mutableStateOf(false) }
    var coverPreview by remember { mutableStateOf<Uri?>(null) }
    var coverRemoving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val busy = loading || removing
    val coverBusy = coverLoading || coverRemoving
    val displayUrl = preview?.toString() ?: avatarUrl
    val displayCoverUrl = coverPreview?.toString() ?: coverUrl

    // Crop avatar: khung TRÒN, tỉ lệ 1:1 — ảnh gốc từ gallery đi qua đây TRƯỚC khi tới onUpload.
    val cropAvatar = rememberCropLauncher(
        aspectX = 1f, aspectY = 1f, circleShape = true, maxSizePx = 512
    ) { croppedUri ->
        preview = croppedUri
        scope.launch {
            val (ok, resultMsg) = onUpload(croppedUri)
            preview = null
            val text = if (ok) {
                "Cập nhật thành công!" + (resultMsg?.let { " ($it)" } ?: "")
            } else {
                resultMsg ?: "Thất bại, thử lại nhé!"
            }
            ToastController.show(text, if (ok) ToastType.SUCCESS else ToastType.ERROR, scope = scope)
        }
    }

    // Crop ảnh bìa: khung CHỮ NHẬT 16:9, không tròn.
    val cropCover = rememberCropLauncher(
        aspectX = 16f, aspectY = 9f, circleShape = false, maxSizePx = 1280
    ) { croppedUri ->
        coverPreview = croppedUri
        scope.launch {
            val (ok, resultMsg) = onCoverUpload(croppedUri)
            coverPreview = null
            val text = if (ok) {
                "Đã cập nhật ảnh bìa!" + (resultMsg?.let { " ($it)" } ?: "")
            } else {
                resultMsg ?: "Thất bại, thử lại nhé!"
            }
            ToastController.show(text, if (ok) ToastType.SUCCESS else ToastType.ERROR, scope = scope)
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        cropAvatar(uri) // mở uCrop thay vì upload thẳng — kết quả crop mới upload
    }

    val pickCoverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        cropCover(uri)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Cover photo (nền phía sau avatar) ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(
                    if (displayCoverUrl.isNullOrBlank()) {
                        Brush.linearGradient(
                            if (dark) listOf(Color(0xFF3B1568), Color(0xFF1E0845))
                            else listOf(Color(0xFFFCE7F3), Color(0xFFE9D5FF))
                        )
                    } else {
                        Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                    }
                )
                .clickable(enabled = !coverBusy) { pickCoverLauncher.launch("image/*") }
        ) {
            if (!displayCoverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = displayCoverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Nút đổi ảnh bìa — góc trên phải, luôn hiện để rõ khu vực này bấm được.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color(0x8C000000), CircleShape)
                    .padding(6.dp)
            ) {
                if (coverBusy) {
                    val rotation by rememberInfiniteTransition(label = "coverSpin").animateFloat(
                        initialValue = 0f, targetValue = 360f,
                        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
                        label = "coverSpinRotation"
                    )
                    Box(modifier = Modifier.graphicsLayer { rotationZ = rotation }) {
                        DashboardIcon(name = "sparkle", size = 14.dp, color = Color.White)
                    }
                } else {
                    DashboardIcon(name = "shuffle", size = 14.dp, color = Color.White)
                }
            }
            if (!coverUrl.isNullOrBlank() && !coverBusy) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color(0x8C000000), CircleShape)
                        .padding(6.dp)
                        .clickable {
                            coverRemoving = true
                            scope.launch {
                                onCoverRemove()
                                coverRemoving = false
                                ToastController.show("Đã xóa ảnh bìa!", ToastType.SUCCESS, scope = scope)
                            }
                        }
                ) {
                    DashboardIcon(name = "close", size = 14.dp, color = Color.White)
                }
            }
        }

        Column(
            modifier = Modifier
                .padding(vertical = 16.dp)
                .offset(y = (-42).dp), // kéo avatar đè lên nửa dưới ảnh bìa, kiểu profile card chuẩn
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        // ── Avatar circle (tap to change) ──
        Box(
            modifier = Modifier
                .size(84.dp)
                .border(3.dp, if (dark) Color(0xFF1E0845) else Color.White, CircleShape)
                .clickable(enabled = !busy) { pickImageLauncher.launch("image/*") },
            contentAlignment = Alignment.Center
        ) {
            LetterAvatar(
                name = displayName,
                size = 84.dp,
                dark = dark,
                animate = true,
                avatarUrl = displayUrl
            )

            // Overlay — spinner khi busy, icon edit khi rảnh (bản Android luôn hiện nhẹ, không cần hover)
            if (busy) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .background(Color(0x61000000), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val rotation by rememberInfiniteTransition(label = "spin").animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
                        label = "spinRotation"
                    )
                    Box(modifier = Modifier.graphicsLayer { rotationZ = rotation }) {
                        DashboardIcon(name = "sparkle", size = 22.dp, color = Color.White)
                    }
                }
            }

            // Camera badge — góc dưới phải
            if (!busy) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(26.dp)
                        .background(AccentGradient, CircleShape)
                        .border(2.dp, if (dark) Color(0xFF1E0845) else Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    DashboardIcon(name = "sparkle", size = 13.dp, color = Color.White)
                }
            }
        }

        // ── Action buttons ──
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { pickImageLauncher.launch("image/*") },
                enabled = !busy,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF472B6)),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                DashboardIcon(name = "shuffle", size = 13.dp, color = Color.White)
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (loading) "Đang tải..." else "Đổi ảnh",
                    fontFamily = NunitoFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp,
                    color = Color.White
                )
            }

            if (!avatarUrl.isNullOrBlank() && !busy) {
                OutlinedButton(
                    onClick = {
                        removing = true
                        scope.launch {
                            onRemove()
                            removing = false
                            ToastController.show("Đã xóa ảnh đại diện!", ToastType.SUCCESS, scope = scope)
                        }
                    },
                    shape = RoundedCornerShape(50),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0x66EF4444)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Xóa",
                        fontFamily = NunitoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            if (removing) {
                Text(
                    text = "Đang xóa…",
                    fontFamily = NunitoFontFamily,
                    fontSize = 11.sp,
                    color = Color(0xFFEF4444)
                )
            }
        }
        }
    }
}
