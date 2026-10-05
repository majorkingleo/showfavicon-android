package com.martin.showfavicon

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * The configured sites, one row per site with a remove button.
 *
 * A `ListAdapter`, so an edit animates and rebinds only the rows that actually
 * changed instead of invalidating the whole list.
 */
class SiteAdapter(private val onRemove: (String) -> Unit) :
    ListAdapter<String, SiteAdapter.SiteViewHolder>(SITES_DIFFER) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SiteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.row_site, parent, false)
        return SiteViewHolder(view, onRemove)
    }

    override fun onBindViewHolder(holder: SiteViewHolder, position: Int) =
        holder.bind(getItem(position))

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

/**
 * Site URLs are unique inside the list, so the URL itself is the identity and
 * the content: either both rows are the same or one of them is a different site.
 */
private val SITES_DIFFER = object : DiffUtil.ItemCallback<String>() {
    override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
    override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
}
