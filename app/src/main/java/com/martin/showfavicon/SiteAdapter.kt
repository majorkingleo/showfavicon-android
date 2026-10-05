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
 * The configured sites, one row per site: tapping a row hands it to [onClick] so
 * it can be edited, the button removes it.
 *
 * A `ListAdapter`, so an edit animates and rebinds only the rows that actually
 * changed instead of invalidating the whole list.
 */
class SiteAdapter(
    private val onClick: (String) -> Unit,
    private val onRemove: (String) -> Unit,
) : ListAdapter<String, SiteAdapter.SiteViewHolder>(SITES_DIFFER) {

    /** URL of the row that is currently being edited, or null. */
    private var editing: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SiteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.row_site, parent, false)
        return SiteViewHolder(view, onClick, onRemove)
    }

    override fun onBindViewHolder(holder: SiteViewHolder, position: Int) {
        val site = getItem(position)
        holder.bind(site, editing = site == editing)
    }

    /**
     * Marks the row of [site] as the one being edited, or clears the mark with
     * null. The two affected rows are rebound one by one, because `ListAdapter`
     * compares site URLs and would not notice this field changing.
     */
    fun setEditing(site: String?) {
        if (editing == site) return

        val previous = currentList.indexOf(editing)
        val next = currentList.indexOf(site)
        editing = site
        if (previous >= 0) notifyItemChanged(previous)
        if (next >= 0) notifyItemChanged(next)
    }

    class SiteViewHolder(
        view: View,
        private val onClick: (String) -> Unit,
        private val onRemove: (String) -> Unit,
    ) : RecyclerView.ViewHolder(view) {

        private val urlLabel: TextView = view.findViewById(R.id.site_url)
        private val removeButton: ImageButton = view.findViewById(R.id.remove_button)

        fun bind(site: String, editing: Boolean) {
            // The host is the part that tells two sites apart in this list.
            val label = Urls.displayName(site).ifEmpty { site }
            urlLabel.text = label
            // A screen reader has to learn that the row itself does something.
            itemView.contentDescription = itemView.context.getString(R.string.edit_site, label)
            // The highlighted background marks the row the field belongs to.
            itemView.isActivated = editing

            itemView.setOnClickListener { onClick(site) }
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
