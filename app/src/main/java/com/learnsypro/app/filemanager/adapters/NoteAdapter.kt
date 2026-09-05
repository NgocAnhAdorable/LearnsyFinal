package com.learnsypro.app.filemanager.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.learnsypro.app.databinding.ItemNoteBinding
import com.learnsypro.app.filemanager.notes.NoteFileStore

class NoteAdapter(
    private val onClick: (NoteFileStore.NoteSummary) -> Unit,
    private val onLongClick: (NoteFileStore.NoteSummary) -> Unit
) : ListAdapter<NoteFileStore.NoteSummary, NoteAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val note = getItem(position)
        holder.binding.tvNoteTitle.text = note.title
        holder.binding.tvNotePreview.text = note.previewText
        holder.binding.tvNoteDate.text = NoteFileStore.formattedDate(note.lastModified)
        holder.binding.root.setOnClickListener { onClick(note) }
        holder.binding.root.setOnLongClickListener { onLongClick(note); true }
    }

    class VH(val binding: ItemNoteBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<NoteFileStore.NoteSummary>() {
            override fun areItemsTheSame(oldItem: NoteFileStore.NoteSummary, newItem: NoteFileStore.NoteSummary) =
                oldItem.file.path == newItem.file.path
            override fun areContentsTheSame(oldItem: NoteFileStore.NoteSummary, newItem: NoteFileStore.NoteSummary) =
                oldItem == newItem
        }
    }
}
