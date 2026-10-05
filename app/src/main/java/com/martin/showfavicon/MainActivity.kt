package com.martin.showfavicon

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * The settings screen: the list of monitored sites.
 *
 * No drag and drop by design — adding from a text field and removing from the
 * list covers the use case. The same screen is meant to become the widget's
 * configuration activity once per instance configuration is implemented.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var sites: SiteStore
    private lateinit var adapter: SiteAdapter
    private lateinit var urlInput: EditText
    private lateinit var emptyHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setTitle(R.string.sites_title)

        sites = SiteStore(this)
        urlInput = findViewById(R.id.url_input)
        emptyHint = findViewById(R.id.empty_view)

        adapter = SiteAdapter { url -> remove(url) }
        findViewById<RecyclerView>(R.id.site_list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        findViewById<Button>(R.id.add_button).setOnClickListener { addCurrentInput() }
        findViewById<Button>(R.id.refresh_button).setOnClickListener { fetchNow() }
        urlInput.setOnEditorActionListener { _, _, _ ->
            addCurrentInput()
            true
        }

        // Idempotent, and the only place that guarantees the hourly work exists
        // even when the user never places the widget.
        FaviconWorker.schedule(this)
        showSites()
    }

    private fun addCurrentInput() {
        when (sites.add(urlInput.text.toString())) {
            SiteStore.AddResult.ADDED -> {
                urlInput.setText("")
                urlInput.error = null
                showSites()
                fetchNow()
            }

            SiteStore.AddResult.EMPTY ->
                urlInput.error = getString(R.string.error_empty_url)

            SiteStore.AddResult.INVALID ->
                urlInput.error = getString(R.string.error_invalid_url)

            SiteStore.AddResult.DUPLICATE ->
                urlInput.error = getString(R.string.error_duplicate_url)
        }
    }

    private fun remove(url: String) {
        sites.remove(url)
        FaviconStore(this).prune(sites.sites().map { Urls.host(it) })
        showSites()
        FaviconWidgetProvider.refreshAll(this)
    }

    private fun fetchNow() {
        FaviconWorker.refreshNow(this)
        Toast.makeText(this, R.string.fetch_started, Toast.LENGTH_SHORT).show()
    }

    private fun showSites() {
        val list = sites.sites()
        adapter.submit(list)
        emptyHint.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }
}
