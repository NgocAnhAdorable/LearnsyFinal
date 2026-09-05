package com.learnsypro.app.ui.dashboard

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.yalantis.ucrop.UCrop
import java.io.File

/**
 * ── CropLauncher ──
 * Bọc uCrop thành 1 ActivityResultContract dùng trong Compose — nhận URI ảnh gốc người dùng vừa
 * chọn từ gallery, mở màn hình uCrop (pan/zoom tương tác, KHÔNG còn center-crop cứng tự động
 * như trước), trả về URI ảnh đã crop (file tạm trong cacheDir) hoặc null nếu người dùng hủy.
 *
 * [aspectX]/[aspectY]: tỉ lệ khung crop cố định — 1:1 cho avatar (hình vuông/tròn), 16:9 hoặc
 * tỉ lệ khác cho ảnh bìa. [circleShape]: bật khung crop hình tròn (chỉ có ý nghĩa với avatar,
 * ảnh bìa luôn dùng khung chữ nhật).
 */
class CropContract(
    private val aspectX: Float,
    private val aspectY: Float,
    private val circleShape: Boolean,
    private val maxSizePx: Int
) : ActivityResultContract<Uri, Uri?>() {

    override fun createIntent(context: Context, input: Uri): android.content.Intent {
        // File output tạm trong cacheDir — dùng FileProvider có sẵn của app
        // (${applicationId}.fileprovider, đã khai báo cho MediaViewerActivity từ trước) để cấp
        // quyền ghi cho UCropActivity mà không cần authority riêng.
        val destFile = File(context.cacheDir, "crop_${System.currentTimeMillis()}.jpg")
        val destUri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", destFile
        )

        val options = UCrop.Options().apply {
            setCircleDimmedLayer(circleShape)
            setShowCropFrame(!circleShape)
            setShowCropGrid(!circleShape)
            setToolbarTitle(if (circleShape) "Cắt ảnh đại diện" else "Cắt ảnh bìa")
            setCompressionQuality(90)
            setFreeStyleCropEnabled(false) // tỉ lệ cố định — không cho kéo méo khung
        }

        return UCrop.of(input, destUri)
            .withAspectRatio(aspectX, aspectY)
            .withMaxResultSize(maxSizePx, (maxSizePx * aspectY / aspectX).toInt())
            .withOptions(options)
            .getIntent(context)
    }

    override fun parseResult(resultCode: Int, intent: android.content.Intent?): Uri? {
        if (resultCode != android.app.Activity.RESULT_OK || intent == null) return null
        return UCrop.getOutput(intent)
    }
}

/**
 * Composable helper — trả về hàm launch(sourceUri) để mở uCrop, và callback [onCropped] nhận
 * kết quả (null nếu người dùng hủy crop, KHÔNG gọi callback trong trường hợp đó — nơi gọi không
 * cần tự check null nếu chỉ quan tâm trường hợp thành công).
 */
@Composable
fun rememberCropLauncher(
    aspectX: Float,
    aspectY: Float,
    circleShape: Boolean,
    maxSizePx: Int = 1024,
    onCropped: (Uri) -> Unit
): (Uri) -> Unit {
    val contract = remember(aspectX, aspectY, circleShape, maxSizePx) {
        CropContract(aspectX, aspectY, circleShape, maxSizePx)
    }
    val launcher = rememberLauncherForActivityResult(contract) { result ->
        if (result != null) onCropped(result)
    }
    return { uri -> launcher.launch(uri) }
}
