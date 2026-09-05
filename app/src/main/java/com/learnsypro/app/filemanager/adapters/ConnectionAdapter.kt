package com.learnsypro.app.filemanager.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.learnsypro.app.databinding.ItemConnectionBinding
import com.learnsypro.app.filemanager.model.FtpConnectionProfile

class ConnectionAdapter(
    private val onClick: (FtpConnectionProfile) -> Unit,
    private val onEdit: (FtpConnectionProfile) -> Unit,
    private val onDelete: (FtpConnectionProfile) -> Unit
) : RecyclerView.Adapter<ConnectionAdapter.VH>() {

    private val items = mutableListOf<FtpConnectionProfile>()

    fun submit(newItems: List<FtpConnectionProfile>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    inner class VH(val binding: ItemConnectionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemConnectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val conn = items[position]
        holder.binding.tvConnName.text = conn.name
        holder.binding.tvConnHost.text = "${conn.host}:${conn.port}"
        holder.binding.root.setOnClickListener { onClick(conn) }
        holder.binding.btnEditConnection.setOnClickListener { onEdit(conn) }
        holder.binding.btnDeleteConnection.setOnClickListener { onDelete(conn) }
    }

    override fun getItemCount(): Int = items.size
}
