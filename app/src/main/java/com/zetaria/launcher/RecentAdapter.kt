package com.zetaria.launcher

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class RecentItem(val app: AppInfo, val lastUsed: Long)

class RecentAdapter(
    private val onClick: (AppInfo) -> Unit,
    private val onLongClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<RecentAdapter.VH>() {

    var items: List<RecentItem> = emptyList()
        private set

    @SuppressLint("NotifyDataSetChanged")
    var accent: Int = 0xFF00FFC8.toInt()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.icon)
        val label: TextView = view.findViewById(R.id.label)
        val ago: TextView = view.findViewById(R.id.ago)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<RecentItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_recent, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.label.text = item.app.label.lowercase()
        holder.ago.text = ago(item.lastUsed)
        holder.icon.setImageDrawable(item.app.freshIcon(holder.itemView.resources))
        holder.itemView.background = Ui.frame(holder.itemView.context, accent)
        holder.itemView.setOnClickListener { onClick(item.app) }
        holder.itemView.setOnLongClickListener {
            onLongClick(item.app)
            true
        }
    }

    private fun ago(ms: Long): String {
        val mins = (System.currentTimeMillis() - ms) / 60000L
        return when {
            mins < 1 -> "ahora"
            mins < 60 -> "hace $mins min"
            mins < 1440 -> "hace ${mins / 60} h"
            else -> "hace ${mins / 1440} d"
        }
    }
}
