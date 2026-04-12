package org.prebid.mobile.api.multiadloader

import android.content.Context
import android.view.View
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import com.yandex.mobile.ads.banner.BannerAdEventListener
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import org.prebid.mobile.AdSize
import org.prebid.mobile.LogUtil
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
    private val yandexAdUnitId: String?,
    private val autoRefreshDelay: Int = 30
) {

    companion object {
        private val TAG = MultiBannerLoader::class.java.simpleName
    }

    enum class SdkState {
        NOT_STARTED,
        LOADING,
        LOADED,
        FAILED
    }

    private var selectedSDK: SdkType? = null
    private var listener: MultiBannerViewListener? = null
    private val priorityOrder: MutableList<SdkType> = SdkConfigHolder.priorityOrderSDK.toMutableList()
    private val sdkStates = mutableMapOf<SdkType, SdkState>()

    private val adLoaders = mapOf(
        SdkType.GAM to GamAdLoader(),
        SdkType.YANDEX to YandexAdLoader(),
        SdkType.PREBID to PrebidAdLoader()
    )

    init {
        SdkType.entries.forEach { sdk ->
            sdkStates[sdk] = SdkState.NOT_STARTED
        }
    }

    fun setListener(listener: MultiBannerViewListener) {
        this.listener = listener
    }

    fun loadAd() {
        destroy()
        selectedSDK = null

        sdkStates.keys.forEach { sdk ->
            sdkStates[sdk] = SdkState.LOADING
        }

        priorityOrder.forEach { sdk ->
            adLoaders[sdk]?.load()
        }
    }

    fun destroy() {
        adLoaders.values.forEach { it.destroy() }
        sdkStates.keys.forEach { sdk ->
            sdkStates[sdk] = SdkState.NOT_STARTED
        }
    }

    private fun handleAdLoaded(sdk: SdkType) {
        sdkStates[sdk] = SdkState.LOADED
        trySelectAd()
    }

    private fun handleAdFailed(error: String?, sdk: SdkType) {
        LogUtil.debug(TAG, "Ad failed $sdk: $error")

        sdkStates[sdk] = SdkState.FAILED
        priorityOrder.remove(sdk)

        listener?.onAdFailed(error, sdk)

        trySelectAd()
    }

    private fun trySelectAd() {
        if (selectedSDK != null) return

        val firstSdk = priorityOrder.firstOrNull() ?: return

        if (sdkStates[firstSdk] == SdkState.LOADED) {
            selectedSDK = firstSdk
            cancelOtherRequests(firstSdk)

            getViewForSdk(firstSdk)?.let { view ->
                listener?.onAdLoaded(view, firstSdk)
            }
            return
        }
    }

    private fun getViewForSdk(sdk: SdkType): View? {
        return when (sdk) {
            SdkType.GAM -> adLoaders[sdk]?.let { (it as GamAdLoader).banner }
            SdkType.YANDEX -> adLoaders[sdk]?.let { (it as YandexAdLoader).banner }
            SdkType.PREBID -> adLoaders[sdk]?.let { (it as PrebidAdLoader).banner }
        }
    }

    private fun cancelOtherRequests(successfulSdk: SdkType) {
        adLoaders.keys
            .filter { it != successfulSdk }
            .forEach { sdk ->
                adLoaders[sdk]?.destroy()
                sdkStates[sdk] = SdkState.NOT_STARTED
            }
    }

    private inner class PrebidAdLoader : AdLoader {
        var banner: BannerView? = null
        private var isAdLoaded = false

        override fun load() {
            if (configId.isNullOrEmpty()) {
                handleAdFailed("ConfigId is empty", SdkType.PREBID)
                return
            }

            isAdLoaded = false
            banner = BannerView(context, configId, adSize).apply {
                setBannerListener(object : BannerViewListener {
                    override fun onAdLoaded(bannerView: BannerView?) {
                        if (!isAdLoaded) {
                            isAdLoaded = true
                            bannerView?.let { handleAdLoaded(SdkType.PREBID) }
                        }
                    }

                    override fun onAdDisplayed(bannerView: BannerView?) {
                        if (selectedSDK != SdkType.PREBID) return
                        listener?.onAdDisplayed(bannerView, SdkType.PREBID)
                    }

                    override fun onAdFailed(bannerView: BannerView?, exception: AdException?) {
                        handleAdFailed(exception?.message, SdkType.PREBID)
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
        var banner: AdManagerAdView? = null
        private var isAdLoaded = false

        override fun load() {
            if (gamAdUnitId.isNullOrEmpty()) {
                handleAdFailed("GAM AdUnitId is empty", SdkType.GAM)
                return
            }

            val size = adSize ?: run {
                handleAdFailed("GAM adSize is null", SdkType.GAM)
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
                            handleAdLoaded(SdkType.GAM)
                            SdkLogUtil.info("GAM Ad loaded", SdkAdStatus.LOADED, AdFormat.BANNER, gamAdUnitId, SdkType.GAM)
                        }
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        SdkLogUtil.info(
                            "GAM Ad failed to load: ${adError.message}",
                            SdkAdStatus.FAILED, AdFormat.BANNER, gamAdUnitId, SdkType.GAM
                        )
                        handleAdFailed(adError.message, SdkType.GAM)
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
                        listener?.onImpression(null, SdkType.GAM)
                        SdkLogUtil.info("GAM Ad impression", SdkAdStatus.IMPRESSION, AdFormat.BANNER, gamAdUnitId, SdkType.GAM)
                    }

                    override fun onAdOpened() {
                        listener?.onAdOpened(SdkType.GAM)
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
            LogUtil.debug(TAG, "Ad destroyed: ${SdkType.GAM}")
        }
    }

    private inner class YandexAdLoader : AdLoader {
        var banner: BannerAdView? = null
        private var isAdLoaded = false

        override fun load() {
            if (yandexAdUnitId.isNullOrEmpty()) {
                handleAdFailed("Yandex AdUnitId is empty", SdkType.YANDEX)
                return
            }

            val size = adSize ?: run {
                handleAdFailed("Yandex adSize is null", SdkType.YANDEX)
                return
            }

            isAdLoaded = false
            banner = BannerAdView(context).apply {
                setAdUnitId(yandexAdUnitId)
                setAdSize(BannerAdSize.inlineSize(context, size.width, size.height))

                setBannerAdEventListener(object : BannerAdEventListener {
                    override fun onAdLoaded() {
                        if (!isAdLoaded) {
                            isAdLoaded = true
                            SdkLogUtil.info("Yandex Ad loaded", SdkAdStatus.LOADED, AdFormat.BANNER, yandexAdUnitId, SdkType.YANDEX)
                            handleAdLoaded(SdkType.YANDEX)
                        }
                    }

                    override fun onAdFailedToLoad(error: AdRequestError) {
                        SdkLogUtil.info(
                            "Yandex Ad failed to load: ${error.description}", SdkAdStatus.FAILED,
                            AdFormat.BANNER, yandexAdUnitId, SdkType.YANDEX
                        )
                        handleAdFailed(error.description, SdkType.YANDEX)
                    }

                    override fun onAdClicked() {
                        listener?.onAdClicked(null, SdkType.YANDEX)
                        SdkLogUtil.info(
                            "Yandex Ad clicked", SdkAdStatus.CLICKED,
                            AdFormat.BANNER, yandexAdUnitId, SdkType.YANDEX
                        )
                    }

                    override fun onLeftApplication() {
                        listener?.onLeftApplication(SdkType.YANDEX)
                        SdkLogUtil.info(
                            "Yandex Ad left application", SdkAdStatus.LEFTAPPLICATION,
                            AdFormat.BANNER, yandexAdUnitId, SdkType.YANDEX
                        )
                    }

                    override fun onReturnedToApplication() {
                        listener?.onReturnedToApplication(SdkType.YANDEX)
                        SdkLogUtil.info(
                            "Yandex Ad returned to application", SdkAdStatus.RETURNEDTOAPPLICATION,
                            AdFormat.BANNER, yandexAdUnitId, SdkType.YANDEX
                        )
                    }

                    override fun onImpression(impressionData: ImpressionData?) {
                        listener?.onImpression(impressionData, SdkType.YANDEX)
                        SdkLogUtil.info(
                            "Yandex Ad impression", SdkAdStatus.IMPRESSION,
                            AdFormat.BANNER, yandexAdUnitId, SdkType.YANDEX
                        )
                    }
                })

                loadAd(AdRequest.Builder().build())
            }
        }

        override fun destroy() {
            banner?.destroy()
            banner = null
            isAdLoaded = false
            LogUtil.debug(TAG, "Ad destroyed: ${SdkType.YANDEX}")
        }
    }

    private interface AdLoader {
        fun load()
        fun destroy()
    }

}