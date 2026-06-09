package org.prebid.mobile.prebidnextgendemo.activities.ads.multiloadernextgen

import android.os.Bundle
import android.util.Log
import android.view.View
import com.yandex.mobile.ads.common.ImpressionData
import org.prebid.mobile.AdSize
import org.prebid.mobile.api.data.SdkType
import org.prebid.mobile.api.multiloadernextgen.MultiBannerLoaderNextGen
import org.prebid.mobile.api.multiloadernextgen.listeners.MultiBannerViewListenerNextGen
import org.prebid.mobile.api.rendering.BannerView
import org.prebid.mobile.prebidnextgendemo.activities.BaseAdActivity

class MultiBannerNextGenActivity : BaseAdActivity() {

    companion object {
        const val CONFIG_ID = "prebid-demo-banner-320-50"
        const val GAM_AD_UNIT_ID = "/21808260008/prebid_oxb_320x50_banner"
        const val YANDEX_AD_UNIT_ID = "demo-banner-yandex"
        const val WIDTH = 320
        const val HEIGHT = 50
    }

    private var adLoader: MultiBannerLoaderNextGen? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createAd()
    }

    private fun createAd() {
        val loader = MultiBannerLoaderNextGen(
            context = this,
            adSize = AdSize(WIDTH, HEIGHT),
            configId = CONFIG_ID,
            gamAdUnitId = GAM_AD_UNIT_ID,
            yandexAdUnitId = YANDEX_AD_UNIT_ID,
            autoRefreshDelay = refreshTimeSeconds,
        )
        adLoader = loader

        loader.setListener(object : MultiBannerViewListenerNextGen {
            override fun onAdLoaded(adView: View, sdk: SdkType) {
                Log.d(TAG, "Ad loaded from: ${sdk.name}")
                adWrapperView.removeAllViews()
                adWrapperView.addView(adView)
                events.loaded(true)
            }

            override fun onAdFailed(bannerView: BannerView?, error: String?, sdk: SdkType?) {
                Log.e(TAG, "Ad failed (${sdk?.name}): $error")
                events.failed(true)
            }

            override fun onAdClicked(bannerView: BannerView?, sdk: SdkType) = events.clicked(true)
            override fun onLeftApplication(sdk: SdkType) {}
            override fun onReturnedToApplication(sdk: SdkType) {}
            override fun onImpression(impressionData: ImpressionData?, sdk: SdkType) {}
            override fun onAdClosed(bannerView: BannerView?, sdk: SdkType) = events.closed(true)
            override fun onAdDisplayed(bannerView: BannerView?, sdk: SdkType) = events.displayed(true)
            override fun onAdOpened(sdk: SdkType) {}
        })

        loader.loadAd()
    }

    override fun onDestroy() {
        super.onDestroy()
        adLoader?.destroy()
    }

}
