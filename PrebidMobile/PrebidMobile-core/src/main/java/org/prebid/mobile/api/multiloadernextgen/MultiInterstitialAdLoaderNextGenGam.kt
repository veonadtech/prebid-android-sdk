package org.prebid.mobile.api.multiloadernextgen

import android.app.Activity
import android.content.Context
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import org.prebid.mobile.LogUtil
import org.prebid.mobile.api.data.AdFormat
import org.prebid.mobile.api.data.SdkType
import org.prebid.mobile.api.multiloadercommon.BaseMultiInterstitialAdLoader
import org.prebid.mobile.logging.SdkAdStatus
import org.prebid.mobile.logging.SdkLogUtil

class MultiInterstitialAdLoaderNextGenGam(
    context: Context,
    configId: String?,
    gamAdUnitId: String?,
    /**
     * Seam over the Next-Gen SDK static [InterstitialAd.load]. Production uses the real call;
     * unit tests inject a lambda to capture the request/callback (the Kotlin companion `load`
     * cannot be intercepted by Mockito's mockStatic).
     */
    private val gamLoad: (AdRequest, AdLoadCallback<InterstitialAd>) -> Unit =
        { request, callback -> InterstitialAd.load(request, callback) },
) : BaseMultiInterstitialAdLoader(context, configId, gamAdUnitId) {

    override fun createGamAdLoader(): AdLoader = GamAdLoader()

    private inner class GamAdLoader : AdLoader {
        private var interstitial: InterstitialAd? = null

        override fun load() {
            if (gamAdUnitId.isNullOrEmpty()) {
                handleAdFailed("GAM AdUnitId is empty", SdkType.GAM)
                return
            }

            try {
                val request = AdRequest.Builder(gamAdUnitId).build()
                gamLoad(request, object : AdLoadCallback<InterstitialAd> {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        super.onAdLoaded(ad)
                        interstitial = ad
                        ad.adEventCallback = object : InterstitialAdEventCallback {
                            override fun onAdShowedFullScreenContent() {
                                super.onAdShowedFullScreenContent()
                                notifyAdDisplayed(SdkType.GAM)
                            }

                            override fun onAdDismissedFullScreenContent() {
                                super.onAdDismissedFullScreenContent()
                                notifyAdClosed(SdkType.GAM)
                            }

                            override fun onAdClicked() {
                                super.onAdClicked()
                                notifyAdClicked(SdkType.GAM)
                            }

                            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                                super.onAdFailedToShowFullScreenContent(error)
                                notifyAdFailedToShow(error.message, SdkType.GAM)
                            }
                        }
                        handleAdLoaded(SdkType.GAM)
                        SdkLogUtil.info("GAM Ad loaded", SdkAdStatus.LOADED, AdFormat.INTERSTITIAL, gamAdUnitId, SdkType.GAM)
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        super.onAdFailedToLoad(loadAdError)
                        handleAdFailed(loadAdError.message, SdkType.GAM)
                        SdkLogUtil.info(
                            "GAM Ad failed to load: ${loadAdError.message}", SdkAdStatus.FAILED,
                            AdFormat.INTERSTITIAL, gamAdUnitId, SdkType.GAM
                        )
                    }
                })
            } catch (t: Throwable) {
                handleAdFailed(t.message, SdkType.GAM)
            }
        }

        override fun show() {
            interstitial?.show(context as Activity)
        }

        override fun destroy() {
            interstitial = null
            LogUtil.debug(logTag, "Ad destroyed: ${SdkType.GAM}")
        }
    }

}
