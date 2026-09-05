package com.learnsypro.app.filemanager.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.learnsypro.app.R
import com.learnsypro.app.databinding.ItemRemoteFileBinding
import com.learnsypro.app.filemanager.dlna.RemoteMediaServer

/**
 * Danh sách máy chủ MediaServer (NAS, điện thoại khác...) tìm thấy trong LAN — bấm vào để bắt
 * đầu duyệt. Dùng chung item_remote_file.xml với danh sách thư mục/cloud để đồng bộ giao diện
 * toàn bộ màn hình "Kết nối" với các màn duyệt file khác trong app.
 */
class RemoteServerAdapter(
    private val onClick: (RemoteMediaServer) -> Unit
) : RecyclerView.Adapter<RemoteServerAdapter.VH>() {

    private val servers = mutableListOf<RemoteMediaServer>()

    fun submitList(newServers: List<RemoteMediaServer>) {
        servers.clear()
        servers.addAll(newServers)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemRemoteFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val server = servers[position]
        val binding = holder.binding
        binding.tvFileName.text = server.friendlyName
        binding.tvFileMeta.text = server.location
        binding.ivIcon.setImageResource(R.drawable.ic_network_storage)
        binding.ivSelectedCheck.visibility = View.GONE
        binding.btnMore.visibility = View.GONE
        binding.root.setOnClickListener { onClick(server) }
        binding.root.setOnLongClickListener { false }
    }

    override fun getItemCount(): Int = servers.size

    class VH(val binding: ItemRemoteFileBinding) : RecyclerView.ViewHolder(binding.root)
}
