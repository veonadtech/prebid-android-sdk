package org.prebid.mobile.api.rendering

import android.content.Context
import android.util.Log
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
import org.prebid.mobile.api.rendering.listeners.BannerViewListener
import org.prebid.mobile.api.rendering.listeners.MultiAdLoaderListener

class MultiAdLoader(

    private val context: Context,
    private val adSize: AdSize?,
    private val configId: String?,
    private val gamAdUnitId: String?,
    private val yandexAdUnitId: String?,
    private val autoRefreshDelay: Int = 0,
    private val priorityOrder: List<AdPlatformSDK> = listOf(AdPlatformSDK.YANDEX, AdPlatformSDK.GAM, AdPlatformSDK.PREBID)
) {

    private val loadedAds = mutableMapOf<AdPlatformSDK, View>()
    private val failedSDKs = mutableSetOf<AdPlatformSDK>()
    private var currentProviderIndex = 0
    private var bannerView: BannerView? = null
    private var gamAdView: AdManagerAdView? = null
    private var yandexAdView: BannerAdView? = null
    private var listener: MultiAdLoaderListener? = null

    fun setListener(listener: MultiAdLoaderListener) {
        this.listener = listener
    }

    fun loadAd() {
        loadedAds.clear()
        failedSDKs.clear()
        currentProviderIndex = 0
        destroy()

        priorityOrder.forEach { sdk ->
            when (sdk) {
                AdPlatformSDK.PREBID -> loadPrebidAd(adSize)
                AdPlatformSDK.GAM -> loadGamAd(adSize)
                AdPlatformSDK.YANDEX -> loadYandexAd(adSize)
            }
        }
    }

    private fun checkPriorityAndNotify() {
        while (currentProviderIndex < priorityOrder.size) {
            val currentSDK = priorityOrder[currentProviderIndex]

            loadedAds[currentSDK]?.let { view ->
                listener?.onAdLoaded(view, currentSDK)
                cancelOtherRequests(currentSDK)
                return
            } ?: run {
                if (failedSDKs.contains(currentSDK)) {
                    currentProviderIndex++
                } else {
                    return
                }
            }
        }

        if (failedSDKs.size == priorityOrder.size) {
            listener?.onAdFailed(null, "All prioritized ad providers failed to load ad", priorityOrder.last())
        }
    }

    private fun loadPrebidAd(adSize: AdSize?) {
        if (configId.isNullOrEmpty()) {
            handleAdFailed(null, "ConfigId is empty", AdPlatformSDK.PREBID)
            return
        }

        bannerView = BannerView(context, configId, adSize).apply {
            setBannerListener(object : BannerViewListener {
                override fun onAdLoaded(bannerView: BannerView?) {
                    if (loadedAds[AdPlatformSDK.PREBID] == null) {
                        loadedAds[AdPlatformSDK.PREBID] = this@apply
                        checkPriorityAndNotify()
                    }
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

    private fun loadGamAd(adSize: AdSize?) {
        if (gamAdUnitId.isNullOrEmpty()) {
            handleAdFailed(null, "GAM AdUnitId is empty", AdPlatformSDK.GAM)
            return
        }

        if (adSize == null) {
            handleAdFailed(null, "GAM adSize is null", AdPlatformSDK.GAM)
            return
        }

        gamAdView = AdManagerAdView(context).apply {
            adUnitId = gamAdUnitId
            setAdSizes(com.google.android.gms.ads.AdSize(adSize.width, adSize.height))
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    if (loadedAds[AdPlatformSDK.GAM] == null) {
                        loadedAds[AdPlatformSDK.GAM] = this@apply
                        checkPriorityAndNotify()
                    }
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

    private fun loadYandexAd(adSize: AdSize?) {
        if (yandexAdUnitId.isNullOrEmpty()) {
            handleAdFailed(null, "Yandex AdUnitId is empty", AdPlatformSDK.YANDEX)
            return
        }

        if (adSize == null) {
            handleAdFailed(null, "Yandex adSize is null", AdPlatformSDK.YANDEX)
            return
        }

        yandexAdView = BannerAdView(context).apply {
            setAdUnitId(yandexAdUnitId)
            setAdSize(BannerAdSize.inlineSize(context, adSize.width, adSize.height))

            setBannerAdEventListener(object : BannerAdEventListener {
                override fun onAdLoaded() {
                    if (loadedAds[AdPlatformSDK.YANDEX] == null) {
                        loadedAds[AdPlatformSDK.YANDEX] = this@apply
                        checkPriorityAndNotify()
                    }
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

    private fun handleAdFailed(view: BannerView?, error: String?, sdk: AdPlatformSDK) {
        failedSDKs.add(sdk)
        listener?.onAdFailed(view, error, sdk)
        checkPriorityAndNotify()
    }

    private fun cancelOtherRequests(successfulSdk: AdPlatformSDK) {
        priorityOrder.forEach { sdk ->
            if (sdk != successfulSdk) {
                when (sdk) {
                    AdPlatformSDK.PREBID -> bannerView?.destroy()
                    AdPlatformSDK.GAM -> gamAdView?.destroy()
                    AdPlatformSDK.YANDEX -> yandexAdView?.destroy()
                }
            }
        }
    }

    fun destroy() {
        bannerView?.destroy()
        gamAdView?.destroy()
        yandexAdView?.destroy()
        loadedAds.clear()
        failedSDKs.clear()
    }
    
}
