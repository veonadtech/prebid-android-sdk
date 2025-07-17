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
import org.prebid.mobile.api.data.AdFormat
import org.prebid.mobile.api.data.AdUnitFormat
import org.prebid.mobile.api.data.SdkType
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.multiadloader.listeners.MultiInterstitialAdListener
import org.prebid.mobile.api.rendering.InterstitialAdUnit
import org.prebid.mobile.api.rendering.listeners.InterstitialAdUnitListener
import org.prebid.mobile.logging.SdkAdStatus
import org.prebid.mobile.logging.SdkLogUtil
import java.util.EnumSet

class MultiInterstitialAdLoader(
    private val context: Context,
    private val configId: String?,
    private val gamAdUnitId: String?,
    private val yandexAdUnitId: String?,
    private val priorityOrder: MutableList<SdkType> = mutableListOf(
        SdkType.YANDEX,
        SdkType.GAM,
        SdkType.PREBID
    )
) {

    companion object {
        private val TAG = MultiInterstitialAdLoader::class.java.simpleName
    }

    private var selectedSDK: SdkType? = null
    private var loadedSDK: MutableList<SdkType> = mutableListOf()
    private var listener: MultiInterstitialAdListener? = null
    private var currentLoaderIndex = 0
    private var isPrebidLoadStarted = false

    private val adLoaders = mapOf(
        SdkType.PREBID to PrebidAdLoader(),
        SdkType.GAM to GamAdLoader(),
        SdkType.YANDEX to YandexAdLoader()
    )

    fun setListener(listener: MultiInterstitialAdListener) {
        this.listener = listener
    }

    fun loadAd() {
        destroy()
        selectedSDK = null

        val order = priorityOrder.toList()
        order.forEach { sdk ->
            if (sdk != SdkType.PREBID) {
                adLoaders[sdk]?.load()
            }
        }

        if (priorityOrder.firstOrNull() == SdkType.PREBID) {
            adLoaders[SdkType.PREBID]?.load()
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

    private fun handleAdLoaded(sdk: SdkType) {
        currentLoaderIndex++
        loadedSDK.add(sdk)
        selectSdkIfFirstPriority(sdk)
    }

    private fun handleAdFailed(error: String?, sdk: SdkType) {
        Log.d(TAG, "Ad failed $sdk: $error")
        priorityOrder.remove(sdk)
        selectSdkIfFirstPriority(sdk)
        listener?.onAdFailed(error, sdk)
    }

    private fun selectSdkIfFirstPriority(sdk: SdkType) {
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

    private fun cancelOtherRequests(successfulSdk: SdkType) {
        adLoaders.keys
            .filter { it != successfulSdk }
            .forEach { sdk ->
                adLoaders[sdk]?.destroy()
            }
    }

    private fun maybeLoadPrebid(sdk: SdkType) {
        if (sdk == SdkType.PREBID) return
        if (priorityOrder.getOrNull(currentLoaderIndex) == SdkType.PREBID && !isPrebidLoadStarted) {
            adLoaders[SdkType.PREBID]?.load()
        }
    }

    private inner class PrebidAdLoader : AdLoader {
        private var interstitial: InterstitialAdUnit? = null

        override fun load() {
            isPrebidLoadStarted = true
            if (configId.isNullOrEmpty()) {
                handleAdFailed("ConfigId is empty", SdkType.PREBID)
                return
            }

            interstitial = InterstitialAdUnit(context, configId, EnumSet.of(AdUnitFormat.BANNER)).apply {
                setInterstitialAdUnitListener(object : InterstitialAdUnitListener {
                    override fun onAdLoaded(unit: InterstitialAdUnit?) = handleAdLoaded(SdkType.PREBID)
                    override fun onAdDisplayed(unit: InterstitialAdUnit?) = listener?.onAdDisplayed(SdkType.PREBID) ?: Unit
                    override fun onAdFailed(unit: InterstitialAdUnit?, e: AdException?) =
                        handleAdFailed(e?.message ?: "Unknown error", SdkType.PREBID)

                    override fun onAdClicked(unit: InterstitialAdUnit?) = listener?.onAdClicked(SdkType.PREBID) ?: Unit
                    override fun onAdClosed(unit: InterstitialAdUnit?) = listener?.onAdClosed(SdkType.PREBID) ?: Unit
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
            Log.d(TAG, "Ad destroyed: ${SdkType.PREBID}")
        }
    }

    private inner class GamAdLoader : AdLoader {
        private var interstitial: AdManagerInterstitialAd? = null

        override fun load() {
            if (gamAdUnitId.isNullOrEmpty()) {
                handleAdFailed("GAM AdUnitId is empty", SdkType.GAM)
                return
            }

            val request = AdManagerAdRequest.Builder().build()
            AdManagerInterstitialAd.load(context, gamAdUnitId, request, object : AdManagerInterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: AdManagerInterstitialAd) {
                    interstitial = interstitialAd
                    handleAdLoaded(SdkType.GAM)
                    SdkLogUtil.info("GAM Ad loaded", SdkAdStatus.LOADED, AdFormat.INTERSTITIAL, gamAdUnitId, SdkType.GAM)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    handleAdFailed(loadAdError.message, SdkType.GAM)
                    SdkLogUtil.info(
                        "GAM Ad failed to load: ${loadAdError.message}", SdkAdStatus.FAILED,
                        AdFormat.INTERSTITIAL, gamAdUnitId, SdkType.GAM
                    )
                }
            })
        }

        override fun show() {
            interstitial?.show(context as Activity)
        }

        override fun destroy() {
            interstitial = null
            Log.d(TAG, "Ad destroyed: ${SdkType.GAM}")
        }
    }

    private inner class YandexAdLoader : AdLoader {
        private var interstitial: InterstitialAd? = null

        override fun load() {
            if (yandexAdUnitId.isNullOrEmpty()) {
                handleAdFailed("Yandex AdUnitId is empty", SdkType.YANDEX)
                return
            }

            InterstitialAdLoader(context).apply {
                setAdLoadListener(object : InterstitialAdLoadListener {
                    override fun onAdLoaded(interstitialAd: InterstitialAd) {
                        SdkLogUtil.info("Yandex Ad loaded", SdkAdStatus.LOADED, AdFormat.INTERSTITIAL, yandexAdUnitId, SdkType.YANDEX)
                        interstitial = interstitialAd.apply {
                            setAdEventListener(object : InterstitialAdEventListener {
                                override fun onAdShown() {
                                    listener?.onAdDisplayed(SdkType.YANDEX) ?: Unit
                                    SdkLogUtil.info(
                                        "Yandex Ad shown", SdkAdStatus.DISPLAYED,
                                        AdFormat.INTERSTITIAL, yandexAdUnitId, SdkType.YANDEX
                                    )
                                }

                                override fun onAdFailedToShow(adError: AdError) {
                                    listener?.onAdFailedToShow(adError.description, SdkType.YANDEX) ?: Unit
                                    SdkLogUtil.info(
                                        "Yandex Ad failed to show: ${adError.description}", SdkAdStatus.FAILED,
                                        AdFormat.INTERSTITIAL, yandexAdUnitId, SdkType.YANDEX
                                    )
                                }

                                override fun onAdDismissed() {
                                    listener?.onAdClosed(SdkType.YANDEX) ?: Unit
                                    SdkLogUtil.info(
                                        "Yandex Ad closed", SdkAdStatus.CLOSED,
                                        AdFormat.INTERSTITIAL, yandexAdUnitId, SdkType.YANDEX
                                    )
                                }

                                override fun onAdClicked() {
                                    listener?.onAdClicked(SdkType.YANDEX) ?: Unit
                                    SdkLogUtil.info(
                                        "Yandex Ad clicked", SdkAdStatus.CLICKED,
                                        AdFormat.INTERSTITIAL, yandexAdUnitId, SdkType.YANDEX
                                    )
                                }

                                override fun onAdImpression(impressionData: ImpressionData?) {
                                    listener?.onImpression(impressionData, SdkType.YANDEX) ?: Unit
                                    SdkLogUtil.info(
                                        "Yandex Ad impression", SdkAdStatus.IMPRESSION,
                                        AdFormat.INTERSTITIAL, yandexAdUnitId, SdkType.YANDEX
                                    )
                                }
                            })
                        }
                        handleAdLoaded(SdkType.YANDEX)
                    }

                    override fun onAdFailedToLoad(error: AdRequestError) {
                        handleAdFailed(error.description, SdkType.YANDEX)
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
            Log.d(TAG, "Ad destroyed: ${SdkType.YANDEX}")
        }
    }

    private interface AdLoader {
        fun load()
        fun show()
        fun destroy()
    }

}
