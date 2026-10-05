package com.martin.showfavicon

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * The configured sites, one row per site with a remove button.
 *
 * The whole list is replaced on every change: it holds a handful of entries, so
 * DiffUtil would be more code than it saves.
 */
class SiteAdapter(private val onRemove: (String) -> Unit) :
    RecyclerView.Adapter<SiteAdapter.SiteViewHolder>() {

    private var items: List<String> = emptyList()

    fun submit(sites: List<String>) {
        items = sites
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SiteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.row_site, parent, false)
        return SiteViewHolder(view, onRemove)
    }

    override fun onBindViewHolder(holder: SiteViewHolder, position: Int) =
        holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    class SiteViewHolder(view: View, private val onRemove: (String) -> Unit) :
        RecyclerView.ViewHolder(view) {

        private val urlLabel: TextView = view.findViewById(R.id.site_url)
        private val removeButton: ImageButton = view.findViewById(R.id.remove_button)

        fun bind(site: String) {
            // The host is the part that tells two sites apart in this list.
            urlLabel.text = Urls.displayName(site).ifEmpty { site }
            removeButton.setOnClickListener { onRemove(site) }
        }
    }
}
