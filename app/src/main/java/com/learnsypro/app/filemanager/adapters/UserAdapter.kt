package com.learnsypro.app.filemanager.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.learnsypro.app.R
import com.learnsypro.app.databinding.ItemUserBinding
import com.learnsypro.app.filemanager.model.FtpUser

class UserAdapter(
    private val onEdit: (FtpUser) -> Unit,
    private val onDelete: (FtpUser) -> Unit
) : RecyclerView.Adapter<UserAdapter.VH>() {

    private val items = mutableListOf<FtpUser>()

    fun submit(newItems: List<FtpUser>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    inner class VH(val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val user = items[position]
        holder.binding.tvUsername.text = user.username
        holder.binding.tvPermission.text = holder.itemView.context.getString(
            if (user.writePermission) R.string.permission_readwrite else R.string.permission_readonly
        )
        holder.binding.btnEditUser.setOnClickListener { onEdit(user) }
        holder.binding.btnDeleteUser.setOnClickListener { onDelete(user) }
    }

    override fun getItemCount(): Int = items.size
}
