package org.prebid.veondemo.activities.ads.multi

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import org.prebid.mobile.api.data.SdkType
import org.prebid.mobile.api.multiadloader.MultiInterstitialAdLoader
import org.prebid.mobile.api.multiloadercommon.MultiInterstitialAdListener
import org.prebid.veondemo.activities.BaseAdActivity

class MultiInterstitialAdActivity : BaseAdActivity() {

    companion object {
        const val CONFIG_ID = "prebid-demo-banner-320-50"
        const val AD_UNIT_ID = "/21808260008/prebid_demo_app_original_api_banner_300x250_order"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onCreateInterstitial()
    }

    private fun onCreateInterstitial() {
        val interstitialLoader = MultiInterstitialAdLoader(
            context = this,
            configId = CONFIG_ID,
            gamAdUnitId = AD_UNIT_ID
        )

        interstitialLoader.setListener(object : MultiInterstitialAdListener {
            override fun onAdLoaded(sdk: SdkType) {
                Log.d("Interstitial", "Ad loaded from: ${sdk.name}")
                showToast("Interstitial loaded from: ${sdk.name}")
                interstitialLoader.showAd()
            }

            override fun onAdFailed(error: String?, sdk: SdkType?) {
                val errorMsg = error ?: "Unknown error"
                val sdkName = sdk?.name ?: "unknown SDK"
                showToast("Interstitial failed ($sdkName): $errorMsg")
            }

            override fun onAdDisplayed(sdk: SdkType) = showToast("Interstitial displayed (${sdk.name})")
            override fun onAdFailedToShow(error: String?, sdk: SdkType?) = showToast("Interstitial failed to show (${sdk?.name}): $error")
            override fun onAdClicked(sdk: SdkType) = showToast("Interstitial clicked (${sdk.name})")
            override fun onAdClosed(sdk: SdkType) = showToast("Interstitial closed (${sdk.name})")
        })

        interstitialLoader.loadAd()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}