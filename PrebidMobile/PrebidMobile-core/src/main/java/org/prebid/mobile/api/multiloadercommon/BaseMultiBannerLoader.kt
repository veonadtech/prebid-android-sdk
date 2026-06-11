package org.prebid.mobile.api.multiloadercommon

import android.content.Context
import android.view.View
import org.prebid.mobile.AdSize
import org.prebid.mobile.LogUtil
import org.prebid.mobile.PrebidMobile
import org.prebid.mobile.api.data.SdkType
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.rendering.BannerView
import org.prebid.mobile.api.rendering.listeners.BannerViewListener
import org.prebid.mobile.configuration.SdkConfigHolder

/**
 * Races the configured SDKs (see [SdkConfigHolder.priorityOrderSDK]) for a banner and exposes the
 * winner through [MultiBannerViewListener]. SDK-agnostic: concrete subclasses only supply the
 * GAM [AdLoader] via [createGamAdLoader] (legacy play-services-ads vs the Next-Gen GMA SDK).
 */
abstract class BaseMultiBannerLoader(
    protected val context: Context,
    protected val adSize: AdSize?,
    protected val configId: String?,
    protected val gamAdUnitId: String?,
    protected val autoRefreshDelay: Int = 30
) {

    /** Logs are tagged with the concrete loader's class name. */
    protected val logTag: String = javaClass.simpleName

    private var selectedSDK: SdkType? = null
    private var listener: MultiBannerViewListener? = null
    private val priorityOrder: MutableList<SdkType> = SdkConfigHolder.priorityOrderSDK.toMutableList()

    /** Views of SDKs that have already loaded, kept so a lower-priority SDK can still be selected
     *  if a higher-priority one fails after it had already loaded. */
    private val loadedViews = mutableMapOf<SdkType, View>()

    // Lazy so the subclass (and its ctor params, e.g. a test seam) is fully constructed before
    // createGamAdLoader() runs on first access (loadAd/destroy).
    private val adLoaders by lazy {
        mapOf(
            SdkType.GAM to createGamAdLoader(),
            SdkType.PREBID to PrebidAdLoader()
        )
    }

    protected abstract fun createGamAdLoader(): AdLoader

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
        loadedViews.clear()
    }

    protected fun handleAdLoaded(sdk: SdkType, view: View) {
        loadedViews[sdk] = view
        selectIfFirstPriorityLoaded()
    }

    protected fun handleAdFailed(view: BannerView?, error: String?, sdk: SdkType) {
        priorityOrder.remove(sdk)
        loadedViews.remove(sdk)
        listener?.onAdFailed(view, error, sdk)
        LogUtil.debug(logTag, "Ad failed $sdk: $error")
        // A higher-priority SDK failing may promote an already-loaded lower-priority SDK to head.
        selectIfFirstPriorityLoaded()
    }

    // Passthrough events the SDK-specific GamAdLoader (and PrebidAdLoader) forward to the listener.
    protected fun notifyAdClicked(bannerView: BannerView?, sdk: SdkType) {
        listener?.onAdClicked(bannerView, sdk)
    }

    protected fun notifyImpression(sdk: SdkType) {
        listener?.onImpression(sdk)
    }

    protected fun notifyAdClosed(bannerView: BannerView?, sdk: SdkType) {
        listener?.onAdClosed(bannerView, sdk)
    }

    protected fun notifyAdDisplayed(bannerView: BannerView?, sdk: SdkType) {
        listener?.onAdDisplayed(bannerView, sdk)
    }

    protected fun notifyAdOpened(sdk: SdkType) {
        listener?.onAdOpened(sdk)
    }

    /** Selects the priority head if it has already loaded. No-op once something is selected. */
    private fun selectIfFirstPriorityLoaded() {
        if (selectedSDK != null) return
        val head = priorityOrder.firstOrNull() ?: return
        val view = loadedViews[head] ?: return
        selectedSDK = head
        cancelOtherRequests(head)
        listener?.onAdLoaded(view, head)
    }

    private fun cancelOtherRequests(successfulSdk: SdkType) {
        adLoaders.keys
            .filter { it != successfulSdk }
            .forEach { sdk ->
                adLoaders[sdk]?.destroy()
            }
    }

    protected inner class PrebidAdLoader : AdLoader {
        private var banner: BannerView? = null
        private var isAdLoaded = false

        override fun load() {
            if (!PrebidMobile.isSdkInitialized()) {
                handleAdFailed(null, "Prebid SDK is not initialized!", SdkType.PREBID)
                return
            }

            if (configId.isNullOrEmpty()) {
                handleAdFailed(null, "ConfigId is empty", SdkType.PREBID)
                return
            }

            isAdLoaded = false
            banner = BannerView(context, configId, adSize).apply {
                setBannerListener(object : BannerViewListener {
                    override fun onAdLoaded(bannerView: BannerView?) {
                        if (!isAdLoaded) {
                            isAdLoaded = true
                            bannerView?.let { handleAdLoaded(SdkType.PREBID, it) }
                        }
                    }

                    override fun onAdDisplayed(bannerView: BannerView?) {
                        if (selectedSDK != SdkType.PREBID) return
                        notifyAdDisplayed(bannerView, SdkType.PREBID)
                    }

                    override fun onAdFailed(bannerView: BannerView?, exception: AdException?) {
                        handleAdFailed(bannerView, exception?.message, SdkType.PREBID)
                    }

                    override fun onAdClicked(bannerView: BannerView?) {
                        notifyAdClicked(bannerView, SdkType.PREBID)
                    }

                    override fun onAdClosed(bannerView: BannerView?) {
                        notifyAdClosed(bannerView, SdkType.PREBID)
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
            LogUtil.debug(logTag, "Ad destroyed: ${SdkType.PREBID}")
        }
    }

    protected interface AdLoader {
        fun load()
        fun destroy()
    }

}
