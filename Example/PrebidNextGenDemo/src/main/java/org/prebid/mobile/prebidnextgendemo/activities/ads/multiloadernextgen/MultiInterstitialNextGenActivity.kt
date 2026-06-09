package org.prebid.mobile.prebidnextgendemo.activities.ads.multiloadernextgen

import android.os.Bundle
import android.util.Log
import com.yandex.mobile.ads.common.ImpressionData
import org.prebid.mobile.api.data.SdkType
import org.prebid.mobile.api.multiloadernextgen.MultiInterstitialAdLoaderNextGen
import org.prebid.mobile.api.multiloadernextgen.listeners.MultiInterstitialAdListenerNextGen
import org.prebid.mobile.prebidnextgendemo.activities.BaseAdActivity

class MultiInterstitialNextGenActivity : BaseAdActivity() {

    companion object {
        const val CONFIG_ID = "prebid-demo-display-interstitial-320-480"
        const val GAM_AD_UNIT_ID = "/21808260008/prebid-demo-app-original-api-display-interstitial"
        const val YANDEX_AD_UNIT_ID = "demo-interstitial-yandex"
    }

    private var interstitialLoader: MultiInterstitialAdLoaderNextGen? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createAd()
    }

    private fun createAd() {
        val loader = MultiInterstitialAdLoaderNextGen(
            context = this,
            configId = CONFIG_ID,
            gamAdUnitId = GAM_AD_UNIT_ID,
            yandexAdUnitId = YANDEX_AD_UNIT_ID,
        )
        interstitialLoader = loader

        loader.setListener(object : MultiInterstitialAdListenerNextGen {
            override fun onAdLoaded(sdk: SdkType) {
                Log.d(TAG, "Interstitial loaded from: ${sdk.name}")
                events.loaded(true)
                loader.showAd()
            }

            override fun onAdFailed(error: String?, sdk: SdkType?) {
                Log.e(TAG, "Interstitial failed (${sdk?.name}): $error")
                events.failed(true)
            }

            override fun onAdDisplayed(sdk: SdkType) = events.displayed(true)
            override fun onAdFailedToShow(error: String?, sdk: SdkType?) = events.failed(true)
            override fun onAdClicked(sdk: SdkType) = events.clicked(true)
            override fun onAdClosed(sdk: SdkType) = events.closed(true)
            override fun onImpression(impressionData: ImpressionData?, sdk: SdkType) {}
        })

        loader.loadAd()
    }

    override fun onDestroy() {
        super.onDestroy()
        interstitialLoader?.destroy()
    }

}
