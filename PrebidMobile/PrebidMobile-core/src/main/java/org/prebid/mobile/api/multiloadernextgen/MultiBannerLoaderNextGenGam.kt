package org.prebid.mobile.api.multiloadernextgen

import android.content.Context
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import org.prebid.mobile.AdSize
import org.prebid.mobile.LogUtil
import org.prebid.mobile.api.data.AdFormat
import org.prebid.mobile.api.data.SdkType
import org.prebid.mobile.api.multiloadercommon.BaseMultiBannerLoader
import org.prebid.mobile.logging.SdkAdStatus
import org.prebid.mobile.logging.SdkLogUtil
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize as NextSize

class MultiBannerLoaderNextGenGam(
    context: Context,
    adSize: AdSize?,
    configId: String?,
    gamAdUnitId: String?,
    autoRefreshDelay: Int = 30
) : BaseMultiBannerLoader(context, adSize, configId, gamAdUnitId, autoRefreshDelay) {

    override fun createGamAdLoader(): AdLoader = GamAdLoader()

    private inner class GamAdLoader : AdLoader {
        private var adView: AdView? = null
        private var isAdLoaded = false

        override fun load() {
            if (gamAdUnitId.isNullOrEmpty()) {
                handleAdFailed(null, "GAM AdUnitId is empty", SdkType.GAM)
                return
            }

            val size = adSize ?: run {
                handleAdFailed(null, "GAM adSize is null", SdkType.GAM)
                return
            }

            isAdLoaded = false
            val view = AdView(context)
            adView = view
            try {
                val request = BannerAdRequest.Builder(
                    gamAdUnitId, listOf(NextSize(size.width, size.height))
                ).build()

                view.loadAd(request, object : AdLoadCallback<BannerAd> {
                    override fun onAdLoaded(ad: BannerAd) {
                        super.onAdLoaded(ad)
                        ad.adEventCallback = object : BannerAdEventCallback {
                            override fun onAdClicked() {
                                super.onAdClicked()
                                notifyAdClicked(null, SdkType.GAM)
                                SdkLogUtil.info("GAM Ad clicked", SdkAdStatus.CLICKED, AdFormat.BANNER, gamAdUnitId, SdkType.GAM)
                            }

                            override fun onAdImpression() {
                                super.onAdImpression()
                                notifyImpression(SdkType.GAM)
                                SdkLogUtil.info("GAM Ad impression", SdkAdStatus.IMPRESSION, AdFormat.BANNER, gamAdUnitId, SdkType.GAM)
                            }

                            override fun onAdDismissedFullScreenContent() {
                                super.onAdDismissedFullScreenContent()
                                notifyAdClosed(null, SdkType.GAM)
                                SdkLogUtil.info("GAM Ad closed", SdkAdStatus.CLOSED, AdFormat.BANNER, gamAdUnitId, SdkType.GAM)
                            }
                        }
                        if (!isAdLoaded) {
                            isAdLoaded = true
                            handleAdLoaded(SdkType.GAM, view)
                            SdkLogUtil.info("GAM Ad loaded", SdkAdStatus.LOADED, AdFormat.BANNER, gamAdUnitId, SdkType.GAM)
                        }
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        super.onAdFailedToLoad(adError)
                        handleAdFailed(null, adError.message, SdkType.GAM)
                        SdkLogUtil.info(
                            "GAM Ad failed to load: ${adError.message}",
                            SdkAdStatus.FAILED, AdFormat.BANNER, gamAdUnitId, SdkType.GAM
                        )
                    }
                })
            } catch (t: Throwable) {
                handleAdFailed(null, t.message, SdkType.GAM)
            }
        }

        override fun destroy() {
            adView?.destroy()
            adView = null
            isAdLoaded = false
            LogUtil.debug(logTag, "Ad destroyed: ${SdkType.GAM}")
        }
    }

}
