package org.prebid.mobile.api.multiloadercommon

import android.content.Context
import org.prebid.mobile.LogUtil
import org.prebid.mobile.PrebidMobile
import org.prebid.mobile.api.data.AdUnitFormat
import org.prebid.mobile.api.data.SdkType
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.rendering.InterstitialAdUnit
import org.prebid.mobile.api.rendering.listeners.InterstitialAdUnitListener
import org.prebid.mobile.configuration.SdkConfigHolder
import java.util.EnumSet

/**
 * Races the configured SDKs (see [SdkConfigHolder.priorityOrderSDK]) for an interstitial and
 * exposes the winner through [MultiInterstitialAdListener]. SDK-agnostic: concrete subclasses only
 * supply the GAM [AdLoader] via [createGamAdLoader] (legacy play-services-ads vs the Next-Gen SDK).
 */
abstract class BaseMultiInterstitialAdLoader(
    protected val context: Context,
    protected val configId: String?,
    protected val gamAdUnitId: String?,
) {

    enum class SdkState {
        NOT_STARTED,
        LOADING,
        LOADED,
        FAILED
    }

    /** Logs are tagged with the concrete loader's class name. */
    protected val logTag: String = javaClass.simpleName

    private var selectedSDK: SdkType? = null
    private var listener: MultiInterstitialAdListener? = null
    private val sdkStates = mutableMapOf<SdkType, SdkState>()
    private val priorityOrder = SdkConfigHolder.priorityOrderSDK.toMutableList()

    // Lazy so the subclass (and its ctor params, e.g. the gamLoad test seam) is fully constructed
    // before createGamAdLoader() runs on first access (loadAd/destroy).
    private val adLoaders by lazy {
        mapOf(
            SdkType.PREBID to PrebidAdLoader(),
            SdkType.GAM to createGamAdLoader()
        )
    }

    init {
        SdkType.values().forEach { sdk ->
            sdkStates[sdk] = SdkState.NOT_STARTED
        }
    }

    protected abstract fun createGamAdLoader(): AdLoader

    fun setListener(listener: MultiInterstitialAdListener) {
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
        } ?: notifyAdFailedToShow("No loaded interstitial ad to show", null)
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

    protected fun handleAdLoaded(sdk: SdkType) {
        setSdkState(sdk, SdkState.LOADED)
        selectSdkIfFirstPriority(sdk)
    }

    protected fun handleAdFailed(error: String?, sdk: SdkType) {
        LogUtil.debug(logTag, "Ad failed $sdk: $error")
        setSdkState(sdk, SdkState.FAILED)
        priorityOrder.remove(sdk)
        selectSdkIfFirstPriority(sdk)
        listener?.onAdFailed(error, sdk)
    }

    // Passthrough events the SDK-specific GamAdLoader (and PrebidAdLoader) forward to the listener.
    protected fun notifyAdDisplayed(sdk: SdkType) {
        listener?.onAdDisplayed(sdk)
    }

    protected fun notifyAdClicked(sdk: SdkType) {
        listener?.onAdClicked(sdk)
    }

    protected fun notifyAdClosed(sdk: SdkType) {
        listener?.onAdClosed(sdk)
    }

    protected fun notifyAdFailedToShow(error: String?, sdk: SdkType?) {
        listener?.onAdFailedToShow(error, sdk)
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

    protected inner class PrebidAdLoader : AdLoader {
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
                    override fun onAdDisplayed(unit: InterstitialAdUnit?) = notifyAdDisplayed(SdkType.PREBID)
                    override fun onAdFailed(unit: InterstitialAdUnit?, e: AdException?) =
                        handleAdFailed(e?.message ?: "Unknown error", SdkType.PREBID)

                    override fun onAdClicked(unit: InterstitialAdUnit?) = notifyAdClicked(SdkType.PREBID)
                    override fun onAdClosed(unit: InterstitialAdUnit?) = notifyAdClosed(SdkType.PREBID)
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
            LogUtil.debug(logTag, "Ad destroyed: ${SdkType.PREBID}")
        }
    }

    protected interface AdLoader {
        fun load()
        fun show()
        fun destroy()
    }

}
