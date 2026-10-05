package com.martin.showfavicon

import android.content.Context
import androidx.core.content.edit

/**
 * The ordered list of monitored sites.
 *
 * Stored as one newline separated preference value: a URL cannot contain a line
 * break, so no JSON dependency is needed and the user's order is preserved.
 */
class SiteStore(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Result of [add] and [update], so the caller can show the right message. */
    enum class AddResult { ADDED, UPDATED, EMPTY, INVALID, DUPLICATE }

    /** Configured sites in user order. */
    fun sites(): List<String> = preferences.getString(KEY_SITES, null)
        .orEmpty()
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toList()

    /** Appends a site unless it is empty, malformed or already tracked. */
    fun add(rawUrl: String): AddResult {
        val url = Urls.normalize(rawUrl)
        if (rawUrl.isBlank()) return AddResult.EMPTY
        if (!Urls.isValid(url)) return AddResult.INVALID

        val existing = sites()
        val host = Urls.host(url)
        if (existing.any { Urls.host(it) == host }) return AddResult.DUPLICATE

        save(existing + url)
        return AddResult.ADDED
    }

    /** Removes the site with this URL's host. */
    fun remove(url: String) {
        val host = Urls.host(url)
        save(sites().filterNot { Urls.host(it) == host })
    }

    /**
     * Corrects [previous] into [rawUrl] and keeps its position in the list, so a
     * typo does not mean removing a site and adding it again at the end.
     */
    fun update(previous: String, rawUrl: String): AddResult {
        if (rawUrl.isBlank()) return AddResult.EMPTY

        val url = Urls.normalize(rawUrl)
        if (!Urls.isValid(url)) return AddResult.INVALID

        val existing = sites()
        // The row is gone by now (removed in the meantime), so this is an add.
        if (previous !in existing) return add(rawUrl)

        val host = Urls.host(url)
        val taken = existing.any { it != previous && Urls.host(it) == host }
        if (taken) return AddResult.DUPLICATE

        save(existing.map { if (it == previous) url else it })
        return AddResult.UPDATED
    }

    private fun save(sites: List<String>) {
        preferences.edit { putString(KEY_SITES, sites.joinToString("\n")) }
    }

    private companion object {
        const val PREFS_NAME = "sites"
        const val KEY_SITES = "list"
    }
}
