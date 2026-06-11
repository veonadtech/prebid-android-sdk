package org.prebid.mobile.api.multiadloader

import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import org.prebid.mobile.AdSize
import org.prebid.mobile.LogUtil
import org.prebid.mobile.api.data.AdFormat
import org.prebid.mobile.api.data.SdkType
import org.prebid.mobile.api.multiloadercommon.BaseMultiBannerLoader
import org.prebid.mobile.logging.SdkAdStatus
import org.prebid.mobile.logging.SdkLogUtil

class MultiBannerLoaderLegacyGam(
    context: Context,
    adSize: AdSize?,
    configId: String?,
    gamAdUnitId: String?,
    autoRefreshDelay: Int = 30
) : BaseMultiBannerLoader(context, adSize, configId, gamAdUnitId, autoRefreshDelay) {

    override fun createGamAdLoader(): AdLoader = GamAdLoader()

    private inner class GamAdLoader : AdLoader {
        private var banner: AdManagerAdView? = null
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
            banner = AdManagerAdView(context).apply {
                adUnitId = gamAdUnitId
                setAdSizes(com.google.android.gms.ads.AdSize(size.width, size.height))
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        if (!isAdLoaded) {
                            isAdLoaded = true
                            handleAdLoaded(SdkType.GAM, this@apply)
                            SdkLogUtil.info("GAM Ad loaded", SdkAdStatus.LOADED, AdFormat.BANNER, gamAdUnitId, SdkType.GAM)
                        }
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        handleAdFailed(null, adError.message, SdkType.GAM)
                        SdkLogUtil.info(
                            "GAM Ad failed to load: ${adError.message}",
                            SdkAdStatus.FAILED, AdFormat.BANNER, gamAdUnitId, SdkType.GAM
                        )
                    }

                    override fun onAdClicked() {
                        notifyAdClicked(null, SdkType.GAM)
                        SdkLogUtil.info("GAM Ad clicked", SdkAdStatus.CLICKED, AdFormat.BANNER, gamAdUnitId, SdkType.GAM)
                    }

                    override fun onAdClosed() {
                        notifyAdClosed(null, SdkType.GAM)
                        SdkLogUtil.info("GAM Ad closed", SdkAdStatus.CLOSED, AdFormat.BANNER, gamAdUnitId, SdkType.GAM)
                    }

                    override fun onAdImpression() {
                        notifyImpression(SdkType.GAM)
                        SdkLogUtil.info("GAM Ad impression", SdkAdStatus.IMPRESSION, AdFormat.BANNER, gamAdUnitId, SdkType.GAM)
                    }

                    override fun onAdOpened() {
                        notifyAdOpened(SdkType.GAM)
                        SdkLogUtil.info("GAM Ad opened", SdkAdStatus.OPENED, AdFormat.BANNER, gamAdUnitId, SdkType.GAM)
                    }
                }

                val request = AdManagerAdRequest.Builder().build()
                loadAd(request)
            }
        }

        override fun destroy() {
            banner?.destroy()
            banner = null
            isAdLoaded = false
            LogUtil.debug(logTag, "Ad destroyed: ${SdkType.GAM}")
        }
    }

}
