package com.learnsypro.app.filemanager.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import coil.size.Scale
import com.learnsypro.app.R
import com.learnsypro.app.databinding.ItemRemoteFileBinding
import com.learnsypro.app.filemanager.dlna.RemoteDidlItem

/**
 * Danh sách thư mục/file trong 1 máy chủ DLNA từ xa đang duyệt — bấm thư mục để vào sâu hơn,
 * bấm file để phát/xem. Dùng CHUNG layout dòng (item_remote_file.xml) với CloudBrowserActivity/
 * FileBrowserActivity để đồng bộ giao diện: icon 48dp bên trái, tên + dòng mô tả loại nội dung.
 *
 * Đồng bộ với Cloud/Bộ nhớ trong: có menu "..." (Tải về máy/Chia sẻ/Chi tiết — KHÔNG có Đổi
 * tên/Xóa vì máy chủ DLNA là CỦA NGƯỜI KHÁC, app chỉ có quyền đọc qua ContentDirectory:Browse,
 * không có action ghi nào trong chuẩn UPnP để sửa đổi dữ liệu của họ).
 */
class RemoteDidlAdapter(
    private val onClick: (RemoteDidlItem) -> Unit,
    private val onMoreClick: (RemoteDidlItem, View) -> Unit
) : RecyclerView.Adapter<RemoteDidlAdapter.VH>() {

    private val entries = mutableListOf<RemoteDidlItem>()

    fun submitList(newEntries: List<RemoteDidlItem>) {
        entries.clear()
        entries.addAll(newEntries)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemRemoteFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = entries[position]
        val binding = holder.binding

        binding.tvFileName.text = entry.title

        val isImage = entry.mimeType?.startsWith("image") == true
        val isVideo = entry.mimeType?.startsWith("video") == true
        val fallbackIcon = when {
            entry.isContainer -> R.drawable.ic_folder
            isVideo -> R.drawable.ic_cat_video
            entry.mimeType?.startsWith("audio") == true -> R.drawable.ic_cat_audio
            isImage -> R.drawable.ic_cat_photo
            else -> R.drawable.ic_cat_doc
        }

        // Ảnh/video: load thumbnail thật qua HTTP thẳng từ resUrl — giống thumbnail thật đã có ở
        // Bộ nhớ trong (Coil), khác Cloud ở chỗ URL DLNA mở trực tiếp không cần access token vì
        // ContentDirectory HTTP-GET của UPnP vốn không yêu cầu xác thực.
        if (!entry.isContainer && (isImage || isVideo) && !entry.resUrl.isNullOrBlank()) {
            binding.ivIcon.load(entry.resUrl) {
                scale(Scale.FILL)
                placeholder(fallbackIcon)
                error(fallbackIcon)
            }
        } else {
            binding.ivIcon.dispose()
            binding.ivIcon.setImageResource(fallbackIcon)
        }

        // Dòng mô tả phụ: thư mục hiện trống (không có số mục từ DIDL), file hiện loại MIME rút gọn.
        binding.tvFileMeta.text = when {
            entry.isContainer -> ""
            !entry.mimeType.isNullOrBlank() -> entry.mimeType.substringBefore(";")
            else -> ""
        }

        // Duyệt DLNA chỉ đọc — không có đa chọn, nhưng CÓ menu "..." (tải về/chia sẻ/chi tiết).
        binding.ivSelectedCheck.visibility = View.GONE
        binding.btnMore.visibility = if (entry.isContainer) View.GONE else View.VISIBLE
        binding.btnMore.setOnClickListener { onMoreClick(entry, it) }

        binding.root.setOnClickListener { onClick(entry) }
        binding.root.setOnLongClickListener { false }
    }

    override fun getItemCount(): Int = entries.size

    class VH(val binding: ItemRemoteFileBinding) : RecyclerView.ViewHolder(binding.root)
}
