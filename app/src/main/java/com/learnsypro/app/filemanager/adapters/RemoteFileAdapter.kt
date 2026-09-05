package com.learnsypro.app.filemanager.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import coil.size.Scale
import com.learnsypro.app.R
import com.learnsypro.app.filemanager.model.RemoteFile
import com.learnsypro.app.databinding.ItemRemoteFileBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.DecimalFormat

/**
 * Dùng ListAdapter + DiffUtil thay vì notifyDataSetChanged(): mỗi lần đổi thư mục chỉ
 * rebind đúng những dòng thực sự thay đổi, thay vì vẽ lại toàn bộ danh sách — đỡ giật/lag
 * trên chip tầm trung (MediaTek Helio, Exynos tầm trung) khi thư mục có nhiều file.
 *
 * Đa chọn (selection mode): bê hành vi tương đương CategoryFilesActivity (bộ nhớ trong) sang
 * đây — long-press 1 dòng để vào chế độ chọn, sau đó tap thường lên các dòng khác để chọn thêm/
 * bớt thay vì mở file. Trạng thái chọn lưu trong adapter (selectedPaths) để không mất khi
 * RecyclerView tái sử dụng ViewHolder.
 */
class RemoteFileAdapter(
    private val onItemClick: (RemoteFile) -> Unit,
    private val onMoreClick: (RemoteFile, android.view.View) -> Unit,
    /** true = file thường (không chỉ thư mục) cũng bấm mở/tải được (CloudBrowserActivity).
     *  false = chỉ thư mục bấm được, file chỉ hiện để xem (FolderPickerActivity chọn đích). */
    private val filesClickable: Boolean = true,
    /** Bật đa chọn (chỉ CloudBrowserActivity cần — FolderPickerActivity không dùng). */
    private val selectionEnabled: Boolean = false,
    private val onSelectionChanged: ((Set<String>) -> Unit)? = null,
    /** Scope để launch coroutine tải thumbnail — null (FolderPickerActivity) = không hiện thumbnail thật, chỉ icon. */
    private val scope: CoroutineScope? = null,
    /** Trả về (url, headers kèm access token) để tải thumbnail thật qua Coil — null = provider chưa đăng nhập/không phải ảnh. */
    private val getThumbnailRequest: (suspend (RemoteFile) -> Pair<String, Map<String, String>>?)? = null
) : ListAdapter<RemoteFile, RemoteFileAdapter.VH>(DIFF) {

    init {
        setHasStableIds(true)
    }

    var isSelectionMode: Boolean = false
        private set
    private val selectedPaths = mutableSetOf<String>()

    fun submit(newItems: List<RemoteFile>) {
        submitList(newItems.toList())
    }

    fun selectedItems(): List<RemoteFile> = currentList.filter { selectedPaths.contains(it.path) }

    fun enterSelectionModeEmpty() {
        if (!selectionEnabled) return
        isSelectionMode = true
        selectedPaths.clear()
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedPaths)
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedPaths.clear()
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedPaths)
    }

    fun selectAll() {
        selectedPaths.clear()
        selectedPaths.addAll(currentList.map { it.path })
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedPaths)
    }

    private fun toggleSelection(file: RemoteFile) {
        if (selectedPaths.contains(file.path)) selectedPaths.remove(file.path) else selectedPaths.add(file.path)
        // Không còn mục nào được chọn -> tự thoát chế độ chọn, giống hành vi quen thuộc của
        // CategoryFilesActivity khi bỏ chọn hết.
        if (selectedPaths.isEmpty()) {
            isSelectionMode = false
        }
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedPaths)
    }

    inner class VH(val binding: ItemRemoteFileBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemId(position: Int): Long = getItem(position).path.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemRemoteFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val file = getItem(position)
        holder.binding.tvFileName.text = file.name

        // Hủy request thumbnail cũ (nếu có, dòng vừa được tái sử dụng từ 1 dòng ảnh/video khác)
        // TRƯỚC KHI set icon fallback/launch request mới — dispose() hủy ĐỒNG BỘ, tránh race
        // condition kinh điển của RecyclerView: request cũ hoàn tất SAU khi dòng đã đổi sang
        // hiện file khác, ghi đè nhầm ảnh vào đúng lúc dòng đang hiện tên file mới.
        holder.binding.ivIcon.dispose()

        val fallbackIcon = if (file.isDirectory) com.learnsypro.app.filemanager.util.FolderIcons.iconFor(file.name) else R.drawable.ic_file
        holder.binding.ivIcon.setImageResource(fallbackIcon)

        // Ảnh/video: thử tải thumbnail thật (giống thumbnail thật đã có sẵn ở Bộ nhớ trong,
        // dùng Coil). Khác local (đọc thẳng từ đĩa), Cloud cần async xin (url, headers) kèm
        // access token trước, nên phải launch coroutine riêng thay vì gọi đồng bộ trong bind.
        if (!file.isDirectory && scope != null && getThumbnailRequest != null &&
            com.learnsypro.app.filemanager.util.FileTypeUtils.isImageOrVideoName(file.name)
        ) {
            scope.launch {
                val request = getThumbnailRequest.invoke(file) ?: return@launch
                // holder có thể đã bị tái chế cho file khác trong lúc coroutine trên đang chờ
                // mạng trả token — kiểm tra lại đúng file trước khi gọi .load(), nếu không sẽ
                // vẫn xảy ra hiện ảnh nhầm dù đã có bước dispose() ở trên (dispose chỉ hủy được
                // request CŨ đã launch, không ngăn được request MỚI này set nhầm ảnh).
                if (holder.bindingAdapterPosition == RecyclerView.NO_POSITION) return@launch
                if (getItem(holder.bindingAdapterPosition).path != file.path) return@launch
                val (url, headers) = request
                holder.binding.ivIcon.load(url) {
                    scale(Scale.FILL)
                    placeholder(fallbackIcon)
                    error(fallbackIcon)
                    headers(okhttp3.Headers.Builder().apply {
                        headers.forEach { (k, v) -> add(k, v) }
                    }.build())
                }
            }
        }
        holder.binding.tvFileMeta.text = if (file.isDirectory) "" else formatSize(file.size)

        val isChecked = selectedPaths.contains(file.path)
        val showSelectionUi = selectionEnabled && isSelectionMode
        holder.binding.ivSelectedCheck.visibility = if (showSelectionUi) android.view.View.VISIBLE else android.view.View.GONE
        holder.binding.ivSelectedCheck.setImageResource(
            if (isChecked) R.drawable.ic_check_circle else R.drawable.ic_check_circle_outline
        )
        holder.binding.btnMore.visibility = if (showSelectionUi) android.view.View.GONE else android.view.View.VISIBLE
        holder.binding.root.setBackgroundColor(
            if (isChecked) holder.itemView.context.getColor(R.color.pastel_blue_50) else android.graphics.Color.TRANSPARENT
        )

        // Ở CloudBrowserActivity (xem/tải file thật), file phải bấm mở được bình thường.
        // Ở FolderPickerActivity (chọn thư mục đích), file chỉ để xem, không bấm mở được —
        // điều khiển qua filesClickable để dùng chung 1 adapter cho cả 2 màn.
        val clickable = file.isDirectory || filesClickable
        holder.binding.root.alpha = if (clickable || isSelectionMode) 1f else 0.55f
        holder.binding.root.setOnClickListener {
            if (showSelectionUi) {
                toggleSelection(file)
            } else if (clickable) {
                onItemClick(file)
            }
        }
        holder.binding.root.setOnLongClickListener {
            if (selectionEnabled && !isSelectionMode) {
                isSelectionMode = true
                selectedPaths.add(file.path)
                notifyDataSetChanged()
                onSelectionChanged?.invoke(selectedPaths)
                true
            } else false
        }
        holder.binding.btnMore.setOnClickListener { onMoreClick(file, it) }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val safeGroup = digitGroups.coerceIn(0, units.size - 1)
        return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, safeGroup.toDouble())) + " " + units[safeGroup]
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<RemoteFile>() {
            override fun areItemsTheSame(oldItem: RemoteFile, newItem: RemoteFile) = oldItem.path == newItem.path
            override fun areContentsTheSame(oldItem: RemoteFile, newItem: RemoteFile) = oldItem == newItem
        }
    }
}
