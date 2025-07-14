package org.prebid.mobile.api.multiadloader

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd
import com.google.android.gms.ads.admanager.AdManagerInterstitialAdLoadCallback
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.common.AdRequestConfiguration
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.interstitial.InterstitialAd
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader
import org.prebid.mobile.api.data.AdPlatformSDK
import org.prebid.mobile.api.data.AdUnitFormat
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.multiadloader.listeners.MultiInterstitialAdListener
import org.prebid.mobile.api.rendering.InterstitialAdUnit
import org.prebid.mobile.api.rendering.listeners.InterstitialAdUnitListener
import java.util.EnumSet

class MultiInterstitialAdLoader(
    private val context: Context,
    private val configId: String?,
    private val gamAdUnitId: String?,
    private val yandexAdUnitId: String?,
    private val priorityOrder: List<AdPlatformSDK> = listOf(AdPlatformSDK.YANDEX, AdPlatformSDK.GAM, AdPlatformSDK.PREBID)
) {
    private val failedSDKs = mutableSetOf<AdPlatformSDK>()
    private var currentProviderIndex = 0
    private var prebidInterstitial: InterstitialAdUnit? = null
    private var gamInterstitial: AdManagerInterstitialAd? = null
    private var yandexInterstitial: InterstitialAd? = null
    private var listener: MultiInterstitialAdListener? = null

    fun setListener(listener: MultiInterstitialAdListener) {
        this.listener = listener
    }

    fun loadAd() {
        failedSDKs.clear()
        currentProviderIndex = 0
        destroy()

        priorityOrder.forEach { sdk ->
            when (sdk) {
                AdPlatformSDK.PREBID -> loadPrebidInterstitial()
                AdPlatformSDK.GAM -> loadGamInterstitial()
                AdPlatformSDK.YANDEX -> loadYandexInterstitial()
            }
        }
    }

    private fun checkPriorityAndNotify() {
        while (currentProviderIndex < priorityOrder.size) {
            val currentSDK = priorityOrder[currentProviderIndex]

            when (currentSDK) {
                AdPlatformSDK.PREBID -> if (prebidInterstitial != null) {
                    cancelOtherRequests(currentSDK)
                    return
                }
                AdPlatformSDK.GAM -> if (gamInterstitial != null) {
                    cancelOtherRequests(currentSDK)
                    return
                }
                AdPlatformSDK.YANDEX -> if (yandexInterstitial != null) {
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
            listener?.onAdFailed("All prioritized ad providers failed to load ad", priorityOrder.last())
        }
    }

    private fun loadPrebidInterstitial() {
        if (configId.isNullOrEmpty()) {
            handleAdFailed("ConfigId is empty", AdPlatformSDK.PREBID)
            return
        }

        prebidInterstitial = InterstitialAdUnit(context, configId, EnumSet.of(AdUnitFormat.BANNER)).apply {
            setInterstitialAdUnitListener(object : InterstitialAdUnitListener {
                override fun onAdLoaded(unit: InterstitialAdUnit?) {
                    listener?.onAdLoaded(AdPlatformSDK.PREBID)
                    checkPriorityAndNotify()
                }

                override fun onAdDisplayed(unit: InterstitialAdUnit?) {
                    listener?.onAdDisplayed(AdPlatformSDK.PREBID)
                }

                override fun onAdFailed(unit: InterstitialAdUnit?, e: AdException?) {
                    handleAdFailed(e?.message ?: "Unknown error", AdPlatformSDK.PREBID)
                }

                override fun onAdClicked(unit: InterstitialAdUnit?) {
                    listener?.onAdClicked(AdPlatformSDK.PREBID)
                }

                override fun onAdClosed(unit: InterstitialAdUnit?) {
                    listener?.onAdClosed(AdPlatformSDK.PREBID)
                }
            })
            loadAd()
        }
    }

    private fun loadGamInterstitial() {
        if (gamAdUnitId.isNullOrEmpty()) {
            handleAdFailed("GAM AdUnitId is empty", AdPlatformSDK.GAM)
            return
        }

        val request = AdManagerAdRequest.Builder().build()
        AdManagerInterstitialAd.load(context, gamAdUnitId, request, object : AdManagerInterstitialAdLoadCallback() {
            override fun onAdLoaded(interstitialAd: AdManagerInterstitialAd) {
                gamInterstitial = interstitialAd
                listener?.onAdLoaded(AdPlatformSDK.GAM)
                checkPriorityAndNotify()
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                handleAdFailed(loadAdError.message, AdPlatformSDK.GAM)
            }
        })
    }

    private fun loadYandexInterstitial() {
        if (yandexAdUnitId.isNullOrEmpty()) {
            handleAdFailed("Yandex AdUnitId is empty", AdPlatformSDK.YANDEX)
            return
        }

        InterstitialAdLoader(context).apply {
            setAdLoadListener(object : InterstitialAdLoadListener {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    listener?.onAdLoaded(AdPlatformSDK.YANDEX)
                    yandexInterstitial = interstitialAd.apply {
                        setAdEventListener(object : InterstitialAdEventListener {
                            override fun onAdShown() {
                                listener?.onAdDisplayed(AdPlatformSDK.YANDEX)
                            }

                            override fun onAdFailedToShow(adError: AdError) {
                                listener?.onAdFailedToShow(adError.description, AdPlatformSDK.YANDEX)
                            }

                            override fun onAdDismissed() {
                                listener?.onAdClosed(AdPlatformSDK.YANDEX)
                            }

                            override fun onAdClicked() {
                                listener?.onAdClicked(AdPlatformSDK.YANDEX)
                            }

                            override fun onAdImpression(impressionData: ImpressionData?) {
                                listener?.onImpression(impressionData, AdPlatformSDK.YANDEX)
                            }
                        })
                    }
                    checkPriorityAndNotify()
                }

                override fun onAdFailedToLoad(error: AdRequestError) {
                    handleAdFailed(error.description, AdPlatformSDK.YANDEX)
                }
            })
            loadAd(AdRequestConfiguration.Builder(yandexAdUnitId).build())
        }
    }

    private fun handleAdFailed(error: String?, sdk: AdPlatformSDK) {
        failedSDKs.add(sdk)
        listener?.onAdFailed(error, sdk)
        checkPriorityAndNotify()
    }

    private fun cancelOtherRequests(successfulSdk: AdPlatformSDK) {
        priorityOrder.forEach { sdk ->
            if (sdk != successfulSdk) {
                when (sdk) {
                    AdPlatformSDK.PREBID -> {
                        prebidInterstitial?.destroy()
                        prebidInterstitial = null
                    }
                    AdPlatformSDK.GAM -> gamInterstitial = null
                    AdPlatformSDK.YANDEX -> yandexInterstitial = null
                }
            }
        }
    }

    fun showAd() {
        when {
            prebidInterstitial != null -> prebidInterstitial?.show()
            gamInterstitial != null -> gamInterstitial?.show(context as Activity)
            yandexInterstitial != null -> yandexInterstitial?.show(context as Activity)
            else -> listener?.onAdFailedToShow("No loaded interstitial ad to show", null)
        }
    }

    fun destroy() {
        prebidInterstitial?.destroy()
        prebidInterstitial = null
        gamInterstitial = null
        yandexInterstitial = null
        failedSDKs.clear()
    }

}
