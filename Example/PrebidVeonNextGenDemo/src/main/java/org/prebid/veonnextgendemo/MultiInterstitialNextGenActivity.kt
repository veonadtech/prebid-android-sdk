package org.prebid.veonnextgendemo

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yandex.mobile.ads.common.ImpressionData
import org.prebid.mobile.api.data.SdkType
import org.prebid.mobile.api.multiloadernextgen.MultiInterstitialAdLoaderNextGenGam
import org.prebid.mobile.api.multiloadernextgen.listeners.MultiInterstitialAdListenerNextGen

class MultiInterstitialNextGenActivity : AppCompatActivity() {

    companion object {
        const val CONFIG_ID = "prebid-demo-banner-320-50"
        const val GAM_AD_UNIT_ID = "/21808260008/prebid_demo_app_original_api_banner_300x250_order"
        const val YANDEX_AD_UNIT_ID = "demo-interstitial-yandex"
    }

    private var interstitialLoader: MultiInterstitialAdLoaderNextGenGam? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ad)
        onCreateInterstitial()
    }

    private fun onCreateInterstitial() {
        val loader = MultiInterstitialAdLoaderNextGenGam(
            context = this,
            configId = CONFIG_ID,
            gamAdUnitId = GAM_AD_UNIT_ID,
            yandexAdUnitId = YANDEX_AD_UNIT_ID,
        )
        interstitialLoader = loader

        loader.setListener(object : MultiInterstitialAdListenerNextGen {
            override fun onAdLoaded(sdk: SdkType) {
                Log.d("Interstitial", "Ad loaded from: ${sdk.name}")
                showToast("Interstitial loaded from: ${sdk.name}")
                loader.showAd()
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
            override fun onImpression(impressionData: ImpressionData?, sdk: SdkType) = showToast("Interstitial impression (${sdk.name})")
        })

        loader.loadAd()
    }

    override fun onDestroy() {
        interstitialLoader?.destroy()
        super.onDestroy()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
