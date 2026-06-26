package org.prebid.mobile.api.multiadloader

import android.content.Context
import android.view.View
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import org.prebid.mobile.AdSize
import org.prebid.mobile.LogUtil
import org.prebid.mobile.PrebidMobile
import org.prebid.mobile.api.data.AdFormat
import org.prebid.mobile.api.data.SdkType
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.multiadloader.listeners.MultiBannerViewListener
import org.prebid.mobile.api.rendering.BannerView
import org.prebid.mobile.api.rendering.listeners.BannerViewListener
import org.prebid.mobile.configuration.SdkConfigHolder
import org.prebid.mobile.logging.SdkAdStatus
import org.prebid.mobile.logging.SdkLogUtil

class MultiBannerLoader(
    private val context: Context,
    private val adSize: AdSize?,
    private val configId: String?,
    private val gamAdUnitId: String?,
    private val autoRefreshDelay: Int = 30
) {

    companion object {
        private val TAG = MultiBannerLoader::class.java.simpleName
    }

    private var selectedSDK: SdkType? = null
    private var listener: MultiBannerViewListener? = null
    private val priorityOrder: MutableList<SdkType> = SdkConfigHolder.priorityOrderSDK.toMutableList()

    private val adLoaders = mapOf(
        SdkType.GAM to GamAdLoader(),
        SdkType.PREBID to PrebidAdLoader()
    )

    fun setListener(listener: MultiBannerViewListener) {
        this.listener = listener
    }

    fun loadAd() {
        destroy()
        selectedSDK = null
        val order = priorityOrder.toList()
        order.forEach { sdk ->
            adLoaders[sdk]?.load()
        }
    }

    fun destroy() {
        adLoaders.values.forEach { it.destroy() }
    }

    private fun handleAdLoaded(sdk: SdkType, view: View) {
        if (priorityOrder.firstOrNull() == sdk && adLoaders[sdk] != null) {
            selectedSDK = sdk
            cancelOtherRequests(sdk)
            listener?.onAdLoaded(view, sdk)
        }
    }

    private fun handleAdFailed(view: BannerView?, error: String?, sdk: SdkType) {
        priorityOrder.remove(sdk)
        listener?.onAdFailed(view, error, sdk)
        LogUtil.debug(TAG, "Ad failed $sdk: $error")
    }

    private fun cancelOtherRequests(successfulSdk: SdkType) {
        adLoaders.keys
            .filter { it != successfulSdk }
            .forEach { sdk ->
                adLoaders[sdk]?.destroy()
            }
    }

    private inner class PrebidAdLoader : AdLoader {
        private var banner: BannerView? = null
        private var isAdLoaded = false

        override fun load() {
            if (!PrebidMobile.isSdkInitialized()) {
                handleAdFailed(null, "Prebid SDK is not initialized!", SdkType.PREBID)
                return
            }

            if (configId.isNullOrEmpty()) {
                handleAdFailed(null, "ConfigId is empty", SdkType.PREBID)
                return
            }

            isAdLoaded = false
            banner = BannerView(context, configId, adSize).apply {
                setBannerListener(object : BannerViewListener {
                    override fun onAdLoaded(bannerView: BannerView?) {
                        if (!isAdLoaded) {
                            isAdLoaded = true
                            bannerView?.let { handleAdLoaded(SdkType.PREBID, it) }
                        }
                    }

                    override fun onAdDisplayed(bannerView: BannerView?) {
                        if (selectedSDK != SdkType.PREBID) return
                        listener?.onAdDisplayed(bannerView, SdkType.PREBID)
                    }

                    override fun onAdFailed(bannerView: BannerView?, exception: AdException?) {
                        handleAdFailed(bannerView, exception?.message, SdkType.PREBID)
                    }

                    override fun onAdClicked(bannerView: BannerView?) {
                        listener?.onAdClicked(bannerView, SdkType.PREBID)
                    }

                    override fun onAdClosed(bannerView: BannerView?) {
                        listener?.onAdClosed(bannerView, SdkType.PREBID)
                    }
                })
                setAutoRefreshDelay(autoRefreshDelay)
                loadAd()
            }
        }

        override fun destroy() {
            banner?.destroy()
            banner = null
            isAdLoaded = false
            LogUtil.debug(TAG, "Ad destroyed: ${SdkType.PREBID}")
        }
    }

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
                        listener?.onAdClicked(null, SdkType.GAM)
                        SdkLogUtil.info("GAM Ad clicked", SdkAdStatus.CLICKED, AdFormat.BANNER, gamAdUnitId, SdkType.GAM)
                    }

                    override fun onAdClosed() {
                        listener?.onAdClosed(null, SdkType.GAM)
                        SdkLogUtil.info("GAM Ad closed", SdkAdStatus.CLOSED, AdFormat.BANNER, gamAdUnitId, SdkType.GAM)
                    }

                    override fun onAdImpression() {
                        listener?.onImpression(SdkType.GAM)
                        SdkLogUtil.info("GAM Ad impression", SdkAdStatus.IMPRESSION, AdFormat.BANNER, gamAdUnitId, SdkType.GAM)
                    }

                    override fun onAdOpened() {
                        listener?.onAdOpened(SdkType.GAM)
                        SdkLogUtil.info("GAM Ad opened", SdkAdStatus.OPENED, AdFormat.BANNER, gamAdUnitId, SdkType.GAM)
                    }
                }

                SdkLogUtil.info("GAM Ad requested", SdkAdStatus.REQUESTED, AdFormat.BANNER, gamAdUnitId, SdkType.GAM)
                val request = AdManagerAdRequest.Builder().build()
                loadAd(request)
            }
        }

        override fun destroy() {
            banner?.destroy()
            banner = null
            isAdLoaded = false
            LogUtil.debug(TAG, "Ad destroyed: ${SdkType.GAM}")
        }
    }

    private interface AdLoader {
        fun load()
        fun destroy()
    }

}
