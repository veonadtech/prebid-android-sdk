package org.prebid.nextgendemo

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yandex.mobile.ads.common.ImpressionData
import org.prebid.mobile.AdSize
import org.prebid.mobile.api.data.SdkType
import org.prebid.mobile.api.multiloadernextgen.MultiBannerLoaderNextGen
import org.prebid.mobile.api.multiloadernextgen.listeners.MultiBannerViewListenerNextGen
import org.prebid.mobile.api.rendering.BannerView

class MultiBannerNextGenActivity : AppCompatActivity() {

    companion object {
        const val CONFIG_ID = "prebid-demo-banner-320-50"
        const val GAM_AD_UNIT_ID = "/21808260008/prebid_demo_app_original_api_banner_300x250_order"
        const val YANDEX_AD_UNIT_ID = "demo-banner-yandex"
        const val WIDTH = 320
        const val HEIGHT = 50
    }

    private lateinit var adWrapper: ViewGroup
    private var adLoader: MultiBannerLoaderNextGen? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ad)
        adWrapper = findViewById(R.id.adWrapper)
        createAd()
    }

    private fun createAd() {
        val loader = MultiBannerLoaderNextGen(
            context = this,
            adSize = AdSize(WIDTH, HEIGHT),
            configId = CONFIG_ID,
            gamAdUnitId = GAM_AD_UNIT_ID,
            yandexAdUnitId = YANDEX_AD_UNIT_ID,
        )
        adLoader = loader

        loader.setListener(object : MultiBannerViewListenerNextGen {
            override fun onAdLoaded(adView: View, sdk: SdkType) {
                adWrapper.removeAllViews()
                adWrapper.addView(adView)
                Log.d("onAdLoaded", "Ad loaded from: ${sdk.name}")
                showToast("Ad loaded from: ${sdk.name}")
            }

            override fun onAdFailed(bannerView: BannerView?, error: String?, sdk: SdkType?) {
                val errorMsg = error ?: "Unknown error"
                val sdkName = sdk?.name ?: "unknown SDK"
                showToast("Ad failed ($sdkName): $errorMsg")
            }

            override fun onAdClicked(bannerView: BannerView?, sdk: SdkType) = showToast("Ad clicked (${sdk.name})")
            override fun onLeftApplication(sdk: SdkType) = showToast("Left app (${sdk.name})")
            override fun onReturnedToApplication(sdk: SdkType) = showToast("Returned to app (${sdk.name})")
            override fun onImpression(impressionData: ImpressionData?, sdk: SdkType) = showToast("Impression tracked (${sdk.name})")
            override fun onAdClosed(bannerView: BannerView?, sdk: SdkType) = showToast("Ad closed (${sdk.name})")
            override fun onAdDisplayed(bannerView: BannerView?, sdk: SdkType) = showToast("Ad displayed (${sdk.name})")
            override fun onAdOpened(sdk: SdkType) = showToast("Ad opened (${sdk.name})")
        })

        loader.loadAd()
    }

    override fun onDestroy() {
        adLoader?.destroy()
        super.onDestroy()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
