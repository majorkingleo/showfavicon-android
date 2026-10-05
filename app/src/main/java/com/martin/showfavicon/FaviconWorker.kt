package com.martin.showfavicon

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Refreshes every configured site and then redraws the widget.
 *
 * WorkManager instead of a service: the work has to survive reboots and the app
 * has no reason to keep a process alive between two fetches. There is no
 * network constraint on purpose — an unreachable site is a result we want to
 * record (the widget grays the icon out), not a reason to postpone the work.
 */
class FaviconWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val sites = SiteStore(context).sites()
        if (sites.isEmpty()) return Result.success()

        val store = FaviconStore(context)
        val fetcher = FaviconFetcher()

        withContext(Dispatchers.IO) {
            for (site in sites) {
                val host = Urls.host(site)
                if (host.isEmpty()) continue

                val bytes = fetcher.fetch(site)
                if (bytes != null && store.write(host, bytes)) {
                    store.markOk(host)
                } else {
                    store.markFailed(host)
                }
            }
            store.prune(sites.map { Urls.host(it) })
        }

        FaviconWidgetProvider.refreshAll(context)
        return Result.success()
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "showfavicon-hourly"

        /** Starts the hourly refresh. Existing schedules are left alone. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<FaviconWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Fetches once, for example right after the user edited the list. */
        fun refreshNow(context: Context) {
            WorkManager.getInstance(context)
                .enqueue(OneTimeWorkRequestBuilder<FaviconWorker>().build())
        }
    }
}
