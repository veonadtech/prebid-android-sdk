package org.prebid.mobile.api.rendering

import android.content.Context
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
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.rendering.listeners.BannerViewListener
import org.prebid.mobile.api.rendering.listeners.MultiAdLoaderListener


class MultiAdLoader(

    private val context: Context,
    private val configId: String,
    private val adSize: AdSize,
    private val gamAdUnitId: String? = null,
    private val yandexAdUnitId: String? = null,
    private val priorityOrder: List<AdPlatformSDK>? = null,
    private val autoRefreshDelay: Int = 0

) {

    private var currentProviderIndex = 0
    private var bannerView: BannerView? = null
    private var gamAdView: AdManagerAdView? = null
    private var yandexAdView: BannerAdView? = null
    private var listener: MultiAdLoaderListener? = null

    enum class AdPlatformSDK {
        PREBID, GAM, YANDEX
    }

    fun setListener(listener: MultiAdLoaderListener) {
        this.listener = listener
    }

    fun loadAd() {
        priorityOrder?.let {
            if (currentProviderIndex >= priorityOrder.size) {
                listener?.onAdFailed(null, "All ad providers failed to load ad", null)
                return
            }

            destroy()
            when (priorityOrder[currentProviderIndex]) {
                AdPlatformSDK.PREBID -> loadPrebidAd(adSize)
                AdPlatformSDK.GAM -> loadGamAd(adSize)
                AdPlatformSDK.YANDEX -> loadYandexAd(adSize)
            }
        }
    }

    private fun loadPrebidAd(adSize: AdSize?) {
        bannerView = BannerView(context, configId, adSize).apply {
            setBannerListener(object : BannerViewListener {
                override fun onAdLoaded(bannerView: BannerView?) {
                    listener?.onAdLoaded(this@apply, AdPlatformSDK.PREBID)
                }

                override fun onAdDisplayed(bannerView: BannerView?) {
                    listener?.onAdDisplayed(bannerView, AdPlatformSDK.PREBID)
                }

                override fun onAdFailed(bannerView: BannerView?, exception: AdException?) {
                    moveToNextProvider()
                    listener?.onAdFailed(bannerView, exception?.message, AdPlatformSDK.PREBID)
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

    private fun loadGamAd(adSize: AdSize?) {
        if (gamAdUnitId.isNullOrEmpty()) {
            moveToNextProvider()
            return
        }

        gamAdView = AdManagerAdView(context).apply {
            try {
                adUnitId = gamAdUnitId

                adSize?.let { size ->
                    val bannerAdSize = com.google.android.gms.ads.AdSize(size.width, size.height)
                    setAdSizes(bannerAdSize)

                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            listener?.onAdLoaded(this@apply, AdPlatformSDK.GAM)
                        }

                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            moveToNextProvider()
                            listener?.onAdFailed(null, adError.message, AdPlatformSDK.GAM)
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
                } ?: run {
                    moveToNextProvider()
                }
            } catch (e: Exception) {
                moveToNextProvider()
            }
        }
    }

    private fun loadYandexAd(adSize: AdSize?) {
        if (yandexAdUnitId.isNullOrEmpty()) {
            moveToNextProvider()
            return
        }

        try {
            yandexAdView = BannerAdView(context).apply {
                setAdUnitId(yandexAdUnitId)
                adSize?.let { size ->
                    setAdSize(BannerAdSize.inlineSize(context, size.width, size.height))

                    setBannerAdEventListener(object : BannerAdEventListener {
                        override fun onAdLoaded() {
                            listener?.onAdLoaded(this@apply, AdPlatformSDK.YANDEX)
                        }

                        override fun onAdFailedToLoad(error: AdRequestError) {
                            moveToNextProvider()
                            listener?.onAdFailed(null, error.description, AdPlatformSDK.YANDEX)
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
                } ?: run {
                    moveToNextProvider()
                }
            }
        } catch (e: Exception) {
            moveToNextProvider()
        }

    }

    private fun moveToNextProvider() {
        currentProviderIndex++
        loadAd()
    }

    fun destroy() {
        bannerView?.destroy()
        gamAdView?.destroy()
        yandexAdView?.destroy()
    }

}
