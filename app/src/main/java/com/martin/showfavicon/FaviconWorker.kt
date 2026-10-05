package com.martin.showfavicon

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
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
 * has no reason to keep a process alive between two fetches.
 *
 * Two things make a gray icon come back by itself. The work only runs when there is
 * a network, and a run that leaves a site failing asks for a retry — which
 * WorkManager holds back until the network is there again. That is what turns "the
 * phone just joined the Wi-Fi" into a fresh attempt without anybody pressing
 * anything.
 */
class FaviconWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val sites = SiteStore(context).sites()
        if (sites.isEmpty()) return Result.success()

        val store = FaviconStore(context)
        val fetcher = FaviconFetcher()
        var failed = 0

        withContext(Dispatchers.IO) {
            for (site in sites) {
                val host = Urls.host(site)
                if (host.isEmpty()) continue

                val bytes = fetcher.fetch(site)
                if (bytes != null && store.write(host, bytes)) {
                    store.markOk(host)
                } else {
                    store.markFailed(host)
                    failed++
                }
            }
            store.prune(sites.map { Urls.host(it) })
        }

        FaviconWidgetProvider.refreshAll(context)

        // A site may simply be unreachable right now, and then the retry comes when
        // the network does. The cap keeps a permanently dead site from polling on.
        return if (failed > 0 && runAttemptCount < MAX_ATTEMPTS) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "showfavicon-hourly"

        /** Wait before the first retry; it grows linearly from there. */
        private const val BACKOFF_SECONDS = 30L

        /** Retries per run, so a site that stays broken does not poll forever. */
        private const val MAX_ATTEMPTS = 6

        /** The one thing every fetch needs. */
        private fun networkConstraint(): Constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /**
         * Starts the hourly refresh. An existing schedule is *updated* rather than
         * left alone: KEEP would keep the constraints of an older install, and that
         * is the kind of change nobody notices for a week.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<FaviconWorker>(1, TimeUnit.HOURS)
                .setConstraints(networkConstraint())
                .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        /**
         * Fetches once: after the user edited the list, or when the device changed
         * networks. Without a network it waits instead of failing straight away.
         */
        fun refreshNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<FaviconWorker>()
                .setConstraints(networkConstraint())
                .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
