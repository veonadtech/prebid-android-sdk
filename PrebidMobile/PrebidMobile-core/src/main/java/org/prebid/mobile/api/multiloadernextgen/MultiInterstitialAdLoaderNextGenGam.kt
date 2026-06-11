package org.prebid.mobile.api.multiloadernextgen

import android.app.Activity
import android.content.Context
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.common.AdRequestConfiguration
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader
import org.prebid.mobile.LogUtil
import org.prebid.mobile.PrebidMobile
import org.prebid.mobile.api.data.AdFormat
import org.prebid.mobile.api.data.AdUnitFormat
import org.prebid.mobile.api.data.SdkType
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.rendering.InterstitialAdUnit
import org.prebid.mobile.api.rendering.listeners.InterstitialAdUnitListener
import org.prebid.mobile.configuration.SdkConfigHolder
import org.prebid.mobile.api.multiloadernextgen.listeners.MultiInterstitialAdListenerNextGen
import org.prebid.mobile.logging.SdkAdStatus
import org.prebid.mobile.logging.SdkLogUtil
import java.util.EnumSet
import com.yandex.mobile.ads.interstitial.InterstitialAd as YandexInterstitialAd

class MultiInterstitialAdLoaderNextGenGam(
    private val context: Context,
    private val configId: String?,
    private val gamAdUnitId: String?,
    private val yandexAdUnitId: String?,
    /**
     * Seam over the Next-Gen SDK static [InterstitialAd.load]. Production uses the real call;
     * unit tests inject a lambda to capture the request/callback (the Kotlin companion `load`
     * cannot be intercepted by Mockito's mockStatic).
     */
    private val gamLoad: (AdRequest, AdLoadCallback<InterstitialAd>) -> Unit =
        { request, callback -> InterstitialAd.load(request, callback) },
) {

    companion object {
        private val TAG = MultiInterstitialAdLoaderNextGenGam::class.java.simpleName
    }

    enum class SdkState {
        NOT_STARTED,
        LOADING,
        LOADED,
        FAILED
    }

    private var selectedSDK: SdkType? = null
    private var listener: MultiInterstitialAdListenerNextGen? = null
    private val sdkStates = mutableMapOf<SdkType, SdkState>()
    private val priorityOrder = SdkConfigHolder.priorityOrderSDK.toMutableList()

    private val adLoaders = mapOf(
        SdkType.PREBID to PrebidAdLoader(),
        SdkType.GAM to GamAdLoader(),
        SdkType.YANDEX to YandexAdLoader()
    )

    init {
        SdkType.values().forEach { sdk ->
            sdkStates[sdk] = SdkState.NOT_STARTED
        }
    }

    fun setListener(listener: MultiInterstitialAdListenerNextGen) {
        this.listener = listener
    }

    fun loadAd() {
        destroy()
        selectedSDK = null

        sdkStates.keys.forEach { sdk ->
            sdkStates[sdk] = SdkState.NOT_STARTED
        }

        val order = priorityOrder.toList()
        order.forEach { sdk ->
            if (sdk != SdkType.PREBID) {
                setSdkState(sdk, SdkState.LOADING)
                adLoaders[sdk]?.load()
            }
        }

        if (priorityOrder.firstOrNull() == SdkType.PREBID) {
            setSdkState(SdkType.PREBID, SdkState.LOADING)
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
        sdkStates.keys.forEach { sdk ->
            sdkStates[sdk] = SdkState.NOT_STARTED
        }
    }

    private fun setSdkState(sdk: SdkType, state: SdkState) {
        sdkStates[sdk] = state
    }

    private fun getSdkState(sdk: SdkType): SdkState {
        return sdkStates[sdk] ?: SdkState.NOT_STARTED
    }

    private fun handleAdLoaded(sdk: SdkType) {
        setSdkState(sdk, SdkState.LOADED)
        selectSdkIfFirstPriority(sdk)
    }

    private fun handleAdFailed(error: String?, sdk: SdkType) {
        LogUtil.debug(TAG, "Ad failed $sdk: $error")
        setSdkState(sdk, SdkState.FAILED)
        priorityOrder.remove(sdk)
        selectSdkIfFirstPriority(sdk)
        listener?.onAdFailed(error, sdk)
    }

    private fun selectSdkIfFirstPriority(sdk: SdkType) {
        if (isFirstPriorityAndLoaded()) {
            selectedSDK = getFirstLoadedSdk()
            selectedSDK?.let {
                cancelOtherRequests(it)
                listener?.onAdLoaded(it)
            }
        } else {
            maybeLoadPrebid(sdk)
        }
    }

    private fun isFirstPriorityAndLoaded(): Boolean {
        return priorityOrder.firstOrNull()?.let { firstPriority ->
            getSdkState(firstPriority) == SdkState.LOADED
        } ?: false
    }

    private fun getFirstLoadedSdk(): SdkType? {
        return priorityOrder.firstOrNull { sdk ->
            getSdkState(sdk) == SdkState.LOADED
        }
    }

    private fun cancelOtherRequests(successfulSdk: SdkType) {
        adLoaders.keys
            .filter { it != successfulSdk }
            .forEach { sdk ->
                adLoaders[sdk]?.destroy()
                setSdkState(sdk, SdkState.NOT_STARTED)
            }
    }

    private fun maybeLoadPrebid(sdk: SdkType) {
        if (sdk == SdkType.PREBID) return

        if (getSdkState(SdkType.PREBID) == SdkState.NOT_STARTED && priorityOrder.contains(SdkType.PREBID)) {
            val prebidIndex = priorityOrder.indexOf(SdkType.PREBID)
            if (prebidIndex != -1) {
                val highPrioritySDKs = priorityOrder.take(prebidIndex)

                val allHighPriorityFailed = highPrioritySDKs.all { highPrioritySdk ->
                    getSdkState(highPrioritySdk) == SdkState.FAILED
                }

                if (allHighPriorityFailed) {
                    setSdkState(SdkType.PREBID, SdkState.LOADING)
                    adLoaders[SdkType.PREBID]?.load()
                }
            }
        }
    }

    private inner class PrebidAdLoader : AdLoader {
        private var interstitial: InterstitialAdUnit? = null

        override fun load() {
            if (!PrebidMobile.isSdkInitialized()) {
                handleAdFailed("Prebid SDK is not initialized!", SdkType.PREBID)
                return
            }

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
            LogUtil.debug(TAG, "Ad destroyed: ${SdkType.PREBID}")
        }
    }

    private inner class GamAdLoader : AdLoader {
        private var interstitial: InterstitialAd? = null

        override fun load() {
            if (gamAdUnitId.isNullOrEmpty()) {
                handleAdFailed("GAM AdUnitId is empty", SdkType.GAM)
                return
            }

            try {
                val request = AdRequest.Builder(gamAdUnitId).build()
                gamLoad(request, object : AdLoadCallback<InterstitialAd> {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        super.onAdLoaded(ad)
                        interstitial = ad
                        ad.adEventCallback = object : InterstitialAdEventCallback {
                            override fun onAdShowedFullScreenContent() {
                                super.onAdShowedFullScreenContent()
                                listener?.onAdDisplayed(SdkType.GAM)
                            }

                            override fun onAdDismissedFullScreenContent() {
                                super.onAdDismissedFullScreenContent()
                                listener?.onAdClosed(SdkType.GAM)
                            }

                            override fun onAdClicked() {
                                super.onAdClicked()
                                listener?.onAdClicked(SdkType.GAM)
                            }

                            override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                                super.onAdFailedToShowFullScreenContent(error)
                                listener?.onAdFailedToShow(error.message, SdkType.GAM)
                            }
                        }
                        handleAdLoaded(SdkType.GAM)
                        SdkLogUtil.info("GAM Ad loaded", SdkAdStatus.LOADED, AdFormat.INTERSTITIAL, gamAdUnitId, SdkType.GAM)
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        super.onAdFailedToLoad(loadAdError)
                        handleAdFailed(loadAdError.message, SdkType.GAM)
                        SdkLogUtil.info(
                            "GAM Ad failed to load: ${loadAdError.message}", SdkAdStatus.FAILED,
                            AdFormat.INTERSTITIAL, gamAdUnitId, SdkType.GAM
                        )
                    }
                })
            } catch (t: Throwable) {
                handleAdFailed(t.message, SdkType.GAM)
            }
        }

        override fun show() {
            interstitial?.show(context as Activity)
        }

        override fun destroy() {
            interstitial = null
            LogUtil.debug(TAG, "Ad destroyed: ${SdkType.GAM}")
        }
    }

    private inner class YandexAdLoader : AdLoader {
        private var interstitial: YandexInterstitialAd? = null

        override fun load() {
            if (yandexAdUnitId.isNullOrEmpty()) {
                handleAdFailed("Yandex AdUnitId is empty", SdkType.YANDEX)
                return
            }

            InterstitialAdLoader(context).apply {
                setAdLoadListener(object : InterstitialAdLoadListener {
                    override fun onAdLoaded(interstitialAd: YandexInterstitialAd) {
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
            LogUtil.debug(TAG, "Ad destroyed: ${SdkType.YANDEX}")
        }
    }

    private interface AdLoader {
        fun load()
        fun show()
        fun destroy()
    }

}
