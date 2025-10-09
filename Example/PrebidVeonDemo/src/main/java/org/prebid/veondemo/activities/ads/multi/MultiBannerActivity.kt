package org.prebid.veondemo.activities.ads.multi

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import org.prebid.mobile.AdSize
import org.prebid.mobile.api.data.SdkType
import org.prebid.mobile.api.multiadloader.MultiBannerLoader
import org.prebid.mobile.api.multiadloader.listeners.MultiBannerViewListener
import org.prebid.mobile.api.rendering.BannerView
import org.prebid.veondemo.activities.BaseAdActivity

class MultiBannerActivity : BaseAdActivity() {

    companion object Companion {
        const val CONFIG_ID = "prebid-demo-banner-320-50"
        const val AD_UNIT_ID = "/21808260008/prebid_demo_app_original_api_banner_300x250_order"
        const val WIDTH = 320
        const val HEIGHT = 50
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createAd()
    }

    private fun createAd() {
        val adLoader = MultiBannerLoader(
            context = this,
            adSize = AdSize(WIDTH, HEIGHT),
            configId = CONFIG_ID,
            gamAdUnitId = AD_UNIT_ID
        )

        adLoader.setListener(object : MultiBannerViewListener {
            override fun onAdLoaded(adView: View, sdk: SdkType) {
                adWrapperView.addView(adView)
                Log.d("onAdLoaded", "Ad loaded from: ${sdk.name}")
                showToast("Ad loaded from: ${sdk.name}")
            }

            override fun onAdFailed(bannerView: BannerView?, error: String?, sdk: SdkType?) {
                val errorMsg = error ?: "Unknown error"
                val sdkName = sdk?.name ?: "unknown SDK"
                showToast("Ad failed ($sdkName): $errorMsg")
            }

            override fun onAdClicked(bannerView: BannerView?, sdk: SdkType) = showToast("Ad clicked (${sdk.name})")
            override fun onImpression(sdk: SdkType) = showToast("Impression tracked (${sdk.name})")
            override fun onAdClosed(bannerView: BannerView?, sdk: SdkType) = showToast("Ad closed (${sdk.name})")
            override fun onAdDisplayed(bannerView: BannerView?, sdk: SdkType) = showToast("Ad displayed (${sdk.name})")
            override fun onAdOpened(sdk: SdkType) = showToast("Ad opened (${sdk.name})")
        })

        adLoader.loadAd()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

}