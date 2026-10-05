package com.martin.showfavicon

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.widget.RemoteViews

/**
 * The home screen widget: one favicon per configured site, tap to open.
 *
 * The layout carries a fixed number of slots because RemoteViews cannot add
 * views at runtime, so sites beyond [MAX_SLOTS] are simply not shown.
 */
class FaviconWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        widgetIds.forEach { render(context, manager, it) }
    }

    override fun onEnabled(context: Context) {
        // First widget placed: make sure the hourly refresh is running.
        FaviconWorker.schedule(context)
    }

    companion object {
        /** Slots declared in `layout/widget_sites.xml`. */
        private const val MAX_SLOTS = 4

        /** Big enough for one home screen cell on a high density display. */
        private const val ICON_SIZE_PX = 96

        private val SLOT_IDS = intArrayOf(R.id.slot_0, R.id.slot_1, R.id.slot_2, R.id.slot_3)

        /** Redraws every placed widget. Called after a fetch run. */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, FaviconWidgetProvider::class.java))
            ids.forEach { render(context, manager, it) }
        }

        private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val sites = SiteStore(context).sites()
            val store = FaviconStore(context)
            val views = RemoteViews(context.packageName, R.layout.widget_sites)

            views.setViewVisibility(
                R.id.empty_hint,
                if (sites.isEmpty()) View.VISIBLE else View.GONE,
            )

            SLOT_IDS.forEachIndexed { index, slotId ->
                val site = sites.getOrNull(index)
                if (site == null) {
                    views.setViewVisibility(slotId, View.GONE)
                    return@forEachIndexed
                }

                views.setViewVisibility(slotId, View.VISIBLE)
                views.setIcon(slotId, iconFor(store, site))
                views.setOnClickPendingIntent(slotId, openSite(context, index, site))
            }

            manager.updateAppWidget(widgetId, views)
        }

        /** Cached icon of a site, grayed out when its last fetch failed. */
        private fun iconFor(store: FaviconStore, site: String): Bitmap? {
            val host = Urls.host(site)
            val cached = store.read(host) ?: return null
            val sized = FaviconStore.scaled(cached, ICON_SIZE_PX)
            return if (store.hasFailed(host)) FaviconStore.grayed(sized) else sized
        }

        private fun openSite(context: Context, index: Int, site: String): PendingIntent {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(site))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // One request code per slot; the payload is refreshed on every draw.
            return PendingIntent.getActivity(
                context,
                index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}

/**
 * A slot shows either a bitmap or, while nothing is cached, the placeholder
 * resource: `RemoteViews` needs a different call for each.
 */
private fun RemoteViews.setIcon(viewId: Int, icon: Bitmap?) {
    if (icon == null) {
        setImageViewResource(viewId, R.drawable.ic_site_placeholder)
    } else {
        setImageViewBitmap(viewId, icon)
    }
}
