package com.optic.tv.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.optic.tv.R
import com.optic.tv.models.PlaylistData

class PlaylistAdapter(private val inContext: Context) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val allData = mutableListOf<PlaylistData>()
    private var onItemClick: ((Int) -> Unit)? = null
    private var onItemLongClick: ((Int) -> Unit)? = null

    fun setOnItemClickListener(listener: (Int) -> Unit) {
        onItemClick = listener
    }

    fun setOnItemLongClickListener(listener: (Int) -> Unit) {
        onItemLongClick = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val v1 = inflater.inflate(R.layout.item_playlist, parent, false)
        return ViewHolder(v1)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val data = allData[position]
        val holder = holder as ViewHolder

        holder.tvTitle.text = data.title
        val count = data.channel.trim()
        if (count.isNotEmpty()) {
            holder.tvChannel.text = count
            holder.tvChannel.visibility = View.VISIBLE
        } else {
            holder.tvChannel.visibility = View.GONE
        }
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onItemClick?.invoke(pos)
        }
        holder.itemView.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onItemLongClick?.invoke(pos)
            true
        }
    }

    override fun getItemCount(): Int = allData.size

    fun addAll(semuaData: List<PlaylistData>) {
        allData.clear()
        allData.addAll(semuaData)
        notifyDataSetChanged()
    }

    fun clear() {
        allData.clear()
        notifyDataSetChanged()
    }

    fun isEmpty(): Boolean = itemCount == 0

    fun getItem(position: Int): PlaylistData = allData[position]

    fun refreshVirtualRowDisplays() {
        for (i in allData.indices) {
            if (allData[i].link.startsWith("__VIRTUAL_")) notifyItemChanged(i)
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.title)
        val tvChannel: TextView = itemView.findViewById(R.id.channel)
    }
}