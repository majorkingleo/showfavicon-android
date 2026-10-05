package com.martin.showfavicon

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * The settings screen: the list of monitored sites.
 *
 * No drag and drop by design. Adding goes through the text field, tapping a row
 * loads that site back into the field for a correction, and the row's button
 * removes it. Every change is saved as it happens, so leaving the screen needs no
 * confirmation. The widget always shows the first four sites, which is why this is
 * not a widget configuration activity.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var sites: SiteStore
    private lateinit var adapter: SiteAdapter
    private lateinit var urlInput: EditText
    private lateinit var addButton: Button
    private lateinit var emptyHint: TextView

    /** URL of the site the field is editing, or null while a new site is typed. */
    private var editing: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applyWindowInsets(findViewById(R.id.root))

        // The toolbar is the support action bar, which is what makes the menu
        // below appear. The title is set here because the activity carries no
        // label of its own.
        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
        supportActionBar?.title = getString(R.string.sites_title)

        sites = SiteStore(this)
        urlInput = findViewById(R.id.url_input)
        emptyHint = findViewById(R.id.empty_view)

        adapter = SiteAdapter(
            onClick = { url -> editOrToggle(url) },
            onRemove = { url -> remove(url) },
        )
        findViewById<RecyclerView>(R.id.site_list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        addButton = findViewById(R.id.add_button)
        addButton.setOnClickListener { submitInput() }
        findViewById<Button>(R.id.refresh_button).setOnClickListener { fetchNow() }
        // The keyboard's own Done key commits the field and then gets out of the
        // way; without this the keyboard stayed up with nothing to close it.
        urlInput.setOnEditorActionListener { _, _, _ ->
            submitInput()
            hideKeyboard(urlInput)
            true
        }
        // A focusable EditText swallows its OnClickListener in the focus change,
        // and after the keyboard was closed the IME can still believe it is
        // shown, so no new show request is made. Hence the touch listener below.
        requestKeyboardOnTap(urlInput)

        // Idempotent, and the only place that guarantees the hourly work exists
        // even when the user never places the widget.
        FaviconWorker.schedule(this)
        showSites()
    }

    /**
     * Edge-to-edge is enforced from targetSdk 35 on, so the content view spans the
     * whole screen, status and navigation bars included. The toolbar would sit
     * under the status bar and the last list row under the navigation bar
     * without this. The base padding is read once so repeated inset passes do
     * not add up.
     */
    private fun applyWindowInsets(root: View) {
        val basePaddingTop = root.paddingTop
        val basePaddingBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            // The keyboard overlays the window as well; whichever inset is taller wins.
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            view.updatePadding(
                top = basePaddingTop + bars.top,
                bottom = basePaddingBottom + maxOf(bars.bottom, ime),
            )
            // Not consumed: children may still want to know about the insets.
            insets
        }
    }

    /**
     * Raises the keyboard whenever [field] is tapped, not only when it gains
     * focus: after the keyboard was closed the IME can still believe it is
     * shown, and then the framework sends no new show request.
     */
    @SuppressLint("ClickableViewAccessibility") // performClick() is called on ACTION_UP
    private fun requestKeyboardOnTap(field: EditText) {
        field.setOnTouchListener { view, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                showKeyboard(view)
                view.performClick()
            }
            // Not consumed, so the cursor still lands where the tap was.
            false
        }
    }

    /** Raises the soft keyboard for [view], whether or not the IME thinks it is up. */
    private fun showKeyboard(view: View) {
        view.requestFocus()
        val manager = getSystemService(InputMethodManager::class.java) ?: return
        // Flags are 0: SHOW_IMPLICIT is deprecated and had no effect anyway.
        manager.showSoftInput(view, 0)
    }

    /** Takes the keyboard down again. */
    private fun hideKeyboard(view: View) {
        val manager = getSystemService(InputMethodManager::class.java) ?: return
        manager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_done -> {
            finishConfiguration()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    /**
     * Leaves the screen and takes the keyboard down with it. Every change to the
     * list is saved as it happens, so there is nothing to confirm here.
     */
    private fun finishConfiguration() {
        hideKeyboard(urlInput)
        finish()
    }

    /**
     * Adds a new site, or saves the one the field is editing. One field and one
     * button, because a site is a URL and nothing else.
     */
    private fun submitInput() {
        val target = editing
        val result = if (target == null) {
            sites.add(urlInput.text.toString())
        } else {
            sites.update(target, urlInput.text.toString())
        }

        when (result) {
            SiteStore.AddResult.ADDED -> {
                clearInput()
                showSites()
                fetchNow()
            }

            SiteStore.AddResult.UPDATED -> {
                leaveEditMode()
                showSites()
                // The old host may be unconfigured now, the new one needs an icon.
                FaviconStore(this).prune(sites.sites().map { Urls.host(it) })
                FaviconWidgetProvider.refreshAll(this)
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

    /** Loads [site] into the field for a correction, or leaves that mode again. */
    private fun editOrToggle(site: String) {
        if (editing == site) {
            leaveEditMode()
            return
        }

        editing = site
        urlInput.setText(site)
        urlInput.setSelection(site.length)
        urlInput.error = null
        addButton.text = getString(R.string.save_site)
        adapter.setEditing(site)
        showKeyboard(urlInput)
    }

    /** Gives the field back to adding new sites. */
    private fun leaveEditMode() {
        editing = null
        clearInput()
        addButton.text = getString(R.string.add_site)
        adapter.setEditing(null)
    }

    private fun clearInput() {
        urlInput.setText("")
        urlInput.error = null
    }

    private fun remove(url: String) {
        if (editing == url) leaveEditMode()

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
        adapter.submitList(list)
        emptyHint.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }
}
