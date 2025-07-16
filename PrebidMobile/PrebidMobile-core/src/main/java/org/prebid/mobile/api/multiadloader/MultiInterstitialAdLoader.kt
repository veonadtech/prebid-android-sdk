package org.prebid.mobile.api.multiadloader

import android.app.Activity
import android.content.Context
import android.util.Log
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
    private val priorityOrder: MutableList<AdPlatformSDK> = mutableListOf(
        AdPlatformSDK.YANDEX,
        AdPlatformSDK.GAM,
        AdPlatformSDK.PREBID
    )
) {

    companion object {
        private val TAG = MultiInterstitialAdLoader::class.java.simpleName
    }

    private var selectedSDK: AdPlatformSDK? = null
    private var loadedSDK: MutableList<AdPlatformSDK> = mutableListOf()
    private var listener: MultiInterstitialAdListener? = null
    private var currentLoaderIndex = 0
    private var isPrebidLoadStarted = false

    private val adLoaders = mapOf(
        AdPlatformSDK.PREBID to PrebidAdLoader(),
        AdPlatformSDK.GAM to GamAdLoader(),
        AdPlatformSDK.YANDEX to YandexAdLoader()
    )

    fun setListener(listener: MultiInterstitialAdListener) {
        this.listener = listener
    }

    fun loadAd() {
        destroy()
        selectedSDK = null

        val order = priorityOrder.toList()
        order.forEach { sdk ->
            if (sdk != AdPlatformSDK.PREBID) {
                adLoaders[sdk]?.load()
            }
        }

        if (priorityOrder.firstOrNull() == AdPlatformSDK.PREBID) {
            adLoaders[AdPlatformSDK.PREBID]?.load()
        }
    }

    fun showAd() {
        selectedSDK?.let { sdk ->
            adLoaders[sdk]?.show()
        } ?: run {
            listener?.onAdFailedToShow("No loaded interstitial ad to show", null)
        }
    }

    fun destroy() {
        adLoaders.values.forEach { it.destroy() }
    }

    private fun handleAdLoaded(sdk: AdPlatformSDK) {
        Log.d(TAG, "Handle Ad loaded: $sdk")
        currentLoaderIndex++
        loadedSDK.add(sdk)
        selectSdkIfFirstPriority(sdk)
    }

    private fun handleAdFailed(error: String?, sdk: AdPlatformSDK) {
        Log.d(TAG, "Ad failed $sdk: $error")
        priorityOrder.remove(sdk)
        selectSdkIfFirstPriority(sdk)
        listener?.onAdFailed(error, sdk)
    }

    private fun selectSdkIfFirstPriority(sdk: AdPlatformSDK) {
        if (isFirstPriorityAndLoaded()) {
            selectedSDK = sdk
            cancelOtherRequests(sdk)
            listener?.onAdLoaded(sdk)
        } else {
            maybeLoadPrebid(sdk)
        }
    }

    private fun isFirstPriorityAndLoaded(): Boolean {
        return loadedSDK.contains(priorityOrder.firstOrNull())
    }

    private fun cancelOtherRequests(successfulSdk: AdPlatformSDK) {
        adLoaders
            .filterKeys { it != successfulSdk }
            .forEach { (_, loader) ->
                loader.destroy()
            }
    }

    private fun maybeLoadPrebid(sdk: AdPlatformSDK) {
        if (sdk == AdPlatformSDK.PREBID) return
        Log.d(TAG, "May be Prebid Load")
        if (priorityOrder.getOrNull(currentLoaderIndex) == AdPlatformSDK.PREBID && !isPrebidLoadStarted) {
            adLoaders[AdPlatformSDK.PREBID]?.load()
        }
    }

    private inner class PrebidAdLoader : AdLoader {
        private var interstitial: InterstitialAdUnit? = null

        override fun load() {
            Log.d(TAG, "Prebid Ad loading")
            isPrebidLoadStarted = true
            if (configId.isNullOrEmpty()) {
                handleAdFailed("ConfigId is empty", AdPlatformSDK.PREBID)
                return
            }

            interstitial = InterstitialAdUnit(context, configId, EnumSet.of(AdUnitFormat.BANNER)).apply {
                setInterstitialAdUnitListener(object : InterstitialAdUnitListener {
                    override fun onAdLoaded(unit: InterstitialAdUnit?) = handleAdLoaded(AdPlatformSDK.PREBID)
                    override fun onAdDisplayed(unit: InterstitialAdUnit?) = listener?.onAdDisplayed(AdPlatformSDK.PREBID) ?: Unit
                    override fun onAdFailed(unit: InterstitialAdUnit?, e: AdException?) =
                        handleAdFailed(e?.message ?: "Unknown error", AdPlatformSDK.PREBID)
                    override fun onAdClicked(unit: InterstitialAdUnit?) = listener?.onAdClicked(AdPlatformSDK.PREBID) ?: Unit
                    override fun onAdClosed(unit: InterstitialAdUnit?) = listener?.onAdClosed(AdPlatformSDK.PREBID) ?: Unit
                })
                loadAd()
            }
        }

        override fun show() {
            interstitial?.show()
        }

        override fun destroy() {
            interstitial?.destroy()
            interstitial = null
            Log.d(TAG, "Ad destroyed: ${AdPlatformSDK.PREBID}")
        }
    }

    private inner class GamAdLoader : AdLoader {
        private var interstitial: AdManagerInterstitialAd? = null

        override fun load() {
            Log.d(TAG, "GAM Ad loading")
            if (gamAdUnitId.isNullOrEmpty()) {
                handleAdFailed("GAM AdUnitId is empty", AdPlatformSDK.GAM)
                return
            }

            val request = AdManagerAdRequest.Builder().build()
            AdManagerInterstitialAd.load(context, gamAdUnitId, request, object : AdManagerInterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: AdManagerInterstitialAd) {
                    interstitial = interstitialAd
                    handleAdLoaded(AdPlatformSDK.GAM)
                }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        handleAdFailed(loadAdError.message, AdPlatformSDK.GAM)
                    }
                })
            }

        override fun show() {
            interstitial?.show(context as Activity)
        }

        override fun destroy() {
            interstitial = null
            Log.d(TAG, "Ad destroyed: ${AdPlatformSDK.GAM}")
        }
    }

    private inner class YandexAdLoader : AdLoader {
        private var interstitial: InterstitialAd? = null

        override fun load() {
            Log.d(TAG, "Yandex Ad loading")
            if (yandexAdUnitId.isNullOrEmpty()) {
                handleAdFailed("Yandex AdUnitId is empty", AdPlatformSDK.YANDEX)
                return
            }

            InterstitialAdLoader(context).apply {
                setAdLoadListener(object : InterstitialAdLoadListener {
                    override fun onAdLoaded(interstitialAd: InterstitialAd) {
                        interstitial = interstitialAd.apply {
                            setAdEventListener(object : InterstitialAdEventListener {
                                override fun onAdShown() = listener?.onAdDisplayed(AdPlatformSDK.YANDEX) ?: Unit
                                override fun onAdFailedToShow(adError: AdError) =
                                    listener?.onAdFailedToShow(adError.description, AdPlatformSDK.YANDEX) ?: Unit
                                override fun onAdDismissed() = listener?.onAdClosed(AdPlatformSDK.YANDEX) ?: Unit
                                override fun onAdClicked() = listener?.onAdClicked(AdPlatformSDK.YANDEX) ?: Unit
                                override fun onAdImpression(impressionData: ImpressionData?) =
                                    listener?.onImpression(impressionData, AdPlatformSDK.YANDEX) ?: Unit
                            })
                        }
                        handleAdLoaded(AdPlatformSDK.YANDEX)
                    }

                    override fun onAdFailedToLoad(error: AdRequestError) {
                        handleAdFailed(error.description, AdPlatformSDK.YANDEX)
                    }
                })
                loadAd(AdRequestConfiguration.Builder(yandexAdUnitId).build())
            }
        }

        override fun show() {
            interstitial?.show(context as Activity)
        }

        override fun destroy() {
            interstitial = null
            Log.d(TAG, "Ad destroyed: ${AdPlatformSDK.YANDEX}")
        }
    }

    private interface AdLoader {
        fun load()
        fun show()
        fun destroy()
    }

}
