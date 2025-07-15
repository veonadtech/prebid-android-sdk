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
import org.prebid.mobile.api.data.AdPlatformSDK
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.multiadloader.listeners.MultiBannerViewListener
import org.prebid.mobile.api.rendering.BannerView
import org.prebid.mobile.api.rendering.listeners.BannerViewListener

class MultiBannerLoader(
    private val context: Context,
    private val adSize: AdSize?,
    private val configId: String?,
    private val gamAdUnitId: String?,
    private val yandexAdUnitId: String?,
    private val autoRefreshDelay: Int = 0,
    private val priorityOrder: MutableList<AdPlatformSDK> = mutableListOf(
        AdPlatformSDK.YANDEX,
        AdPlatformSDK.GAM,
        AdPlatformSDK.PREBID
    )
) {
    private var selectedSDK: AdPlatformSDK? = null
    private var listener: MultiBannerViewListener? = null

    private val adLoaders = mapOf(
        AdPlatformSDK.GAM to GamAdLoader(),
        AdPlatformSDK.YANDEX to YandexAdLoader(),
        AdPlatformSDK.PREBID to PrebidAdLoader()
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

    private fun handleAdLoaded(sdk: AdPlatformSDK, view: View) {
        if (priorityOrder.firstOrNull() == sdk && adLoaders[sdk] != null) {
            selectedSDK = sdk
            cancelOtherRequests(sdk)
            listener?.onAdLoaded(view, sdk)
        }
    }

    private fun handleAdFailed(view: BannerView?, error: String?, sdk: AdPlatformSDK) {
        priorityOrder.remove(sdk)
        listener?.onAdFailed(view, error, sdk)
    }

    private fun cancelOtherRequests(successfulSdk: AdPlatformSDK) {
        priorityOrder.filter { it != successfulSdk }.forEach { sdk ->
            adLoaders[sdk]?.destroy()
        }
    }

    private inner class PrebidAdLoader : AdLoader {
        private var banner: BannerView? = null

        override fun load() {
            if (configId.isNullOrEmpty()) {
                handleAdFailed(null, "ConfigId is empty", AdPlatformSDK.PREBID)
                return
            }

            banner = BannerView(context, configId, adSize).apply {
                setBannerListener(object : BannerViewListener {
                    override fun onAdLoaded(bannerView: BannerView?) {
                        bannerView?.let { handleAdLoaded(AdPlatformSDK.PREBID, it) }
                    }

                    override fun onAdDisplayed(bannerView: BannerView?) {
                        listener?.onAdDisplayed(bannerView, AdPlatformSDK.PREBID)
                    }

                    override fun onAdFailed(bannerView: BannerView?, exception: AdException?) {
                        handleAdFailed(bannerView, exception?.message, AdPlatformSDK.PREBID)
                    }

                    override fun onAdClicked(bannerView: BannerView?) {
                        listener?.onAdClicked(bannerView, AdPlatformSDK.PREBID)
                    }

                    override fun onAdUrlClicked(url: String?) {
                        listener?.onAdUrlClicked(url, AdPlatformSDK.PREBID)
                    }

                    override fun onAdClosed(bannerView: BannerView?) {
                        listener?.onAdClosed(bannerView, AdPlatformSDK.PREBID)
                    }
                })
                setAutoRefreshDelay(autoRefreshDelay)
                loadAd()
            }
        }

        override fun destroy() {
            banner?.destroy()
            banner = null
        }
    }

    private inner class GamAdLoader : AdLoader {
        private var banner: AdManagerAdView? = null

        override fun load() {
            if (gamAdUnitId.isNullOrEmpty()) {
                handleAdFailed(null, "GAM AdUnitId is empty", AdPlatformSDK.GAM)
                return
            }

            val size = adSize ?: run {
                handleAdFailed(null, "GAM adSize is null", AdPlatformSDK.GAM)
                return
            }

            banner = AdManagerAdView(context).apply {
                adUnitId = gamAdUnitId
                setAdSizes(com.google.android.gms.ads.AdSize(size.width, size.height))
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        handleAdLoaded(AdPlatformSDK.GAM, this@apply)
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        handleAdFailed(null, adError.message, AdPlatformSDK.GAM)
                    }

                    override fun onAdClicked() {
                        listener?.onAdClicked(null, AdPlatformSDK.GAM)
                    }

                    override fun onAdClosed() {
                        listener?.onAdClosed(null, AdPlatformSDK.GAM)
                    }

                    override fun onAdImpression() {
                        listener?.onImpression(null, AdPlatformSDK.GAM)
                    }

                    override fun onAdOpened() {
                        listener?.onAdOpened(AdPlatformSDK.GAM)
                    }
                }

                val request = AdManagerAdRequest.Builder().build()
                loadAd(request)
            }
        }

        override fun destroy() {
            banner?.destroy()
            banner = null
        }
    }

    private inner class YandexAdLoader : AdLoader {
        private var banner: BannerAdView? = null

        override fun load() {
            if (yandexAdUnitId.isNullOrEmpty()) {
                handleAdFailed(null, "Yandex AdUnitId is empty", AdPlatformSDK.YANDEX)
                return
            }

            val size = adSize ?: run {
                handleAdFailed(null, "Yandex adSize is null", AdPlatformSDK.YANDEX)
                return
            }

            banner = BannerAdView(context).apply {
                setAdUnitId(yandexAdUnitId)
                setAdSize(BannerAdSize.inlineSize(context, size.width, size.height))

                setBannerAdEventListener(object : BannerAdEventListener {
                    override fun onAdLoaded() {
                        handleAdLoaded(AdPlatformSDK.YANDEX, this@apply)
                    }

                    override fun onAdFailedToLoad(error: AdRequestError) {
                        handleAdFailed(null, error.description, AdPlatformSDK.YANDEX)
                    }

                    override fun onAdClicked() {
                        listener?.onAdClicked(null, AdPlatformSDK.YANDEX)
                    }

                    override fun onLeftApplication() {
                        listener?.onLeftApplication(AdPlatformSDK.YANDEX)
                    }

                    override fun onReturnedToApplication() {
                        listener?.onReturnedToApplication(AdPlatformSDK.YANDEX)
                    }

                    override fun onImpression(impressionData: ImpressionData?) {
                        listener?.onImpression(impressionData, AdPlatformSDK.YANDEX)
                    }
                })

                loadAd(AdRequest.Builder().build())
            }
        }

        override fun destroy() {
            banner?.destroy()
            banner = null
        }
    }

    private interface AdLoader {
        fun load()
        fun destroy()
    }
}
