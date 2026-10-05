package com.martin.showfavicon

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log

/**
 * Watches the connectivity of the device.
 *
 * A site that cannot be reached is what grays an icon out, and the usual reason is
 * that the device is not on the right network — a phone that joins the Wi-Fi, or an
 * address that only exists inside the LAN. WorkManager's own network constraint
 * covers a device that had *no* network at all, but it cannot see a change from one
 * network to another, which is exactly the case here. This callback can: whenever
 * the default network changes, one refresh is queued right away.
 *
 * It lives as long as the process does. When the app is not running, the periodic
 * work is what eventually catches up.
 */
class ShowFaviconApp : Application() {

    override fun onCreate() {
        super.onCreate()

        val manager = getSystemService(ConnectivityManager::class.java) ?: return
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "default network became available: $network")
                FaviconWorker.refreshNow(this@ShowFaviconApp)
            }
        })
    }

    private companion object {
        const val TAG = "ShowFaviconNet"
    }
}
