package org.prebid.nextgendemo

import android.app.Application
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import org.prebid.mobile.PrebidMobile
import org.prebid.mobile.TargetingParams
import org.prebid.mobile.api.data.InitializationStatus

class NextGenApp : Application() {

    companion object {
        private const val TAG = "NextGenApp"
    }

    override fun onCreate() {
        super.onCreate()
        initPrebidSdk()
        initNextGenSdk()
        TargetingParams.setSubjectToGDPR(true)
    }

    private fun initPrebidSdk() {
        PrebidMobile.setPrebidServerAccountId("com.arena.banglalinkmela.app")
        PrebidMobile.setCustomStatusEndpoint("https://prebid.veonadx.com/status")
        PrebidMobile.setTimeoutMillis(3000)
        PrebidMobile.setShareGeoLocation(true)
        PrebidMobile.setLogLevel(PrebidMobile.LogLevel.DEBUG)
        PrebidMobile.useExternalBrowser = true

        PrebidMobile.initializeSdk(
            applicationContext,
            "https://prebid.veonadx.com/openrtb2/auction",
            "https://dcdn.veonadx.com/sdk/com.arena.banglalinkmela.app/config.json"
        ) { status ->
            if (status == InitializationStatus.SUCCEEDED) {
                Log.d(TAG, "Prebid SDK initialized successfully!")
            } else {
                Log.e(TAG, "Prebid SDK initialization error: $status\n${status.description}")
            }
        }
    }

    /** Initializes the Next-Gen GMA SDK used by the multiloadernextgen GAM racer. */
    private fun initNextGenSdk() {
        Thread {
            MobileAds.initialize(
                this,
                InitializationConfig.Builder("ca-app-pub-1875909575462531~6255590079").build()
            ) {
                Log.d(TAG, "Next-Gen GMA SDK initialized")
            }
        }.start()
    }
}
