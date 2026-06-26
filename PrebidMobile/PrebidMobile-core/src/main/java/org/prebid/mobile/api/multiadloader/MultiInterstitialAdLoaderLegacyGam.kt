package org.prebid.mobile.api.multiadloader

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd
import com.google.android.gms.ads.admanager.AdManagerInterstitialAdLoadCallback
import org.prebid.mobile.LogUtil
import org.prebid.mobile.api.data.AdFormat
import org.prebid.mobile.api.data.SdkType
import org.prebid.mobile.api.multiloadercommon.BaseMultiInterstitialAdLoader
import org.prebid.mobile.logging.SdkAdStatus
import org.prebid.mobile.logging.SdkLogUtil

class MultiInterstitialAdLoaderLegacyGam(
    context: Context,
    configId: String?,
    gamAdUnitId: String?,
) : BaseMultiInterstitialAdLoader(context, configId, gamAdUnitId) {

    override fun createGamAdLoader(): AdLoader = GamAdLoader()

    private inner class GamAdLoader : AdLoader {
        private var interstitial: AdManagerInterstitialAd? = null

        override fun load() {
            if (gamAdUnitId.isNullOrEmpty()) {
                handleAdFailed("GAM AdUnitId is empty", SdkType.GAM)
                return
            }

            SdkLogUtil.info("GAM Ad requested", SdkAdStatus.REQUESTED, AdFormat.INTERSTITIAL, gamAdUnitId, SdkType.GAM)
            val request = AdManagerAdRequest.Builder().build()
            AdManagerInterstitialAd.load(context, gamAdUnitId, request, object : AdManagerInterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: AdManagerInterstitialAd) {
                    interstitial = interstitialAd
                    handleAdLoaded(SdkType.GAM)
                    SdkLogUtil.info("GAM Ad loaded", SdkAdStatus.LOADED, AdFormat.INTERSTITIAL, gamAdUnitId, SdkType.GAM)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    handleAdFailed(loadAdError.message, SdkType.GAM)
                    SdkLogUtil.info(
                        "GAM Ad failed to load: ${loadAdError.message}", SdkAdStatus.FAILED,
                        AdFormat.INTERSTITIAL, gamAdUnitId, SdkType.GAM
                    )
                }
            })
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
