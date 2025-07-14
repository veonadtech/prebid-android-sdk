package org.prebid.mobile.api.multiadloader

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
    private val priorityOrder: List<AdPlatformSDK> = listOf(AdPlatformSDK.YANDEX, AdPlatformSDK.GAM, AdPlatformSDK.PREBID)
) {
    private val failedSDKs = mutableSetOf<AdPlatformSDK>()
    private var currentProviderIndex = 0
    private var prebidBanner: BannerView? = null
    private var gamBanner: AdManagerAdView? = null
    private var yandexBanner: BannerAdView? = null
    private var listener: MultiBannerViewListener? = null

    fun setListener(listener: MultiBannerViewListener) {
        this.listener = listener
    }

    fun loadAd() {
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

            when (currentSDK) {
                AdPlatformSDK.PREBID -> prebidBanner?.let { banner ->
                    listener?.onAdLoaded(banner, currentSDK)
                    cancelOtherRequests(currentSDK)
                    return
                }
                AdPlatformSDK.GAM -> gamBanner?.let { banner ->
                    listener?.onAdLoaded(banner, currentSDK)
                    cancelOtherRequests(currentSDK)
                    return
                }
                AdPlatformSDK.YANDEX -> yandexBanner?.let { banner ->
                    listener?.onAdLoaded(banner, currentSDK)
                    cancelOtherRequests(currentSDK)
                    return
                }
            }

            if (failedSDKs.contains(currentSDK)) {
                currentProviderIndex++
            } else {
                return
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

        prebidBanner = BannerView(context, configId, adSize).apply {
            setBannerListener(object : BannerViewListener {
                override fun onAdLoaded(bannerView: BannerView?) {
                    checkPriorityAndNotify()
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

        gamBanner = AdManagerAdView(context).apply {
            adUnitId = gamAdUnitId
            setAdSizes(com.google.android.gms.ads.AdSize(adSize.width, adSize.height))
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    checkPriorityAndNotify()
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

        yandexBanner = BannerAdView(context).apply {
            setAdUnitId(yandexAdUnitId)
            setAdSize(BannerAdSize.inlineSize(context, adSize.width, adSize.height))

            setBannerAdEventListener(object : BannerAdEventListener {
                override fun onAdLoaded() {
                    checkPriorityAndNotify()
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
                    AdPlatformSDK.PREBID -> {
                        prebidBanner?.destroy()
                        prebidBanner = null
                    }
                    AdPlatformSDK.GAM -> {
                        gamBanner?.destroy()
                        gamBanner = null
                    }
                    AdPlatformSDK.YANDEX -> {
                        yandexBanner?.destroy()
                        yandexBanner = null
                    }
                }
            }
        }
    }

    fun destroy() {
        prebidBanner?.destroy()
        gamBanner?.destroy()
        yandexBanner?.destroy()
        prebidBanner = null
        gamBanner = null
        yandexBanner = null
        failedSDKs.clear()
    }
}
