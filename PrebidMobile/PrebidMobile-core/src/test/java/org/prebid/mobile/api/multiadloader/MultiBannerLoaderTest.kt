package org.prebid.mobile.api.multiadloader

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mock
import org.mockito.MockedConstruction
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.MockitoAnnotations
import org.prebid.mobile.AdSize
import org.prebid.mobile.PrebidMobile
import org.prebid.mobile.api.data.SdkType
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.multiadloader.listeners.MultiBannerViewListener
import org.prebid.mobile.api.rendering.BannerView
import org.prebid.mobile.api.rendering.listeners.BannerViewListener
import org.prebid.mobile.configuration.SdkConfigHolder
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class MultiBannerLoaderTest {

    @Mock
    private lateinit var mockListener: MultiBannerViewListener

    private lateinit var context: Context
    private lateinit var adSize: AdSize

    private lateinit var bannerConstruction: MockedConstruction<BannerView>
    private lateinit var gamViewConstruction: MockedConstruction<AdManagerAdView>

    private val configId = "test-config-id"
    private val gamAdUnitId = "/1234/test-gam-ad-unit"
    private lateinit var mockedPrebidMobile: MockedStatic<PrebidMobile>

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        context = Robolectric.buildActivity(Activity::class.java).create().get()
        adSize = AdSize(300, 250)

        mockedPrebidMobile = Mockito.mockStatic(PrebidMobile::class.java)
        mockedPrebidMobile.`when`<Boolean> { PrebidMobile.isSdkInitialized() }.thenReturn(true)


        bannerConstruction = Mockito.mockConstruction(BannerView::class.java)
        gamViewConstruction = Mockito.mockConstruction(AdManagerAdView::class.java)
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.PREBID, SdkType.GAM)
    }

    @After
    fun tearDown() {
        bannerConstruction.close()
        gamViewConstruction.close()
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.PREBID, SdkType.GAM)
        mockedPrebidMobile.close()
    }

    private fun createLoader(
        size: AdSize? = adSize,
        cfg: String? = configId,
        gamId: String? = gamAdUnitId,
    ): MultiBannerLoader {
        val loader = MultiBannerLoader(context, size, cfg, gamId)
        loader.setListener(mockListener)
        return loader
    }

    private fun capturePrebidListener(instanceIndex: Int = 0): BannerViewListener {
        val banner = bannerConstruction.constructed()[instanceIndex]
        val captor = ArgumentCaptor.forClass(BannerViewListener::class.java)
        verify(banner).setBannerListener(captor.capture())
        return captor.value
    }

    private fun captureGamListener(instanceIndex: Int = 0): AdListener {
        val gamView = gamViewConstruction.constructed()[instanceIndex]
        val captor = ArgumentCaptor.forClass(AdListener::class.java)
        verify(gamView).adListener = captor.capture()
        return captor.value
    }

    // 1. loadAd builds both inner SDK loaders when priority is default and inputs valid
    @Test
    fun loadAd_prebidFirstPriority_constructsBothSdkLoaders() {
        val loader = createLoader()

        loader.loadAd()

        assertEquals(1, bannerConstruction.constructed().size)
        assertEquals(1, gamViewConstruction.constructed().size)

        val banner = bannerConstruction.constructed()[0]
        verify(banner).setBannerListener(any())
        verify(banner).setAutoRefreshDelay(anyInt())
        verify(banner).loadAd()

        val gamView = gamViewConstruction.constructed()[0]
        verify(gamView).adUnitId = gamAdUnitId
        verify(gamView).setAdSizes(any())
        verify(gamView).adListener = any()
        verify(gamView).loadAd(any())
    }

    // 2. configId=null → Prebid reports failure immediately and never constructs BannerView
    @Test
    fun loadAd_configIdNull_prebidFailsImmediatelyWithoutConstructingBannerView() {
        val loader = createLoader(cfg = null)

        loader.loadAd()

        assertEquals(0, bannerConstruction.constructed().size)
        verify(mockListener).onAdFailed(isNull(), eq("ConfigId is empty"), eq(SdkType.PREBID))
    }

    // 3. gamAdUnitId=null → GAM reports failure immediately and never constructs AdManagerAdView
    @Test
    fun loadAd_gamAdUnitIdNull_gamFailsImmediatelyWithoutConstructingAdView() {
        val loader = createLoader(gamId = null)

        loader.loadAd()

        assertEquals(0, gamViewConstruction.constructed().size)
        verify(mockListener).onAdFailed(isNull(), eq("GAM AdUnitId is empty"), eq(SdkType.GAM))
    }

    // 4. adSize=null with GAM id present → GAM fails with "GAM adSize is null"
    @Test
    fun loadAd_adSizeNull_gamFailsWithAdSizeNullMessage() {
        val loader = createLoader(size = null)

        loader.loadAd()

        assertEquals(0, gamViewConstruction.constructed().size)
        verify(mockListener).onAdFailed(isNull(), eq("GAM adSize is null"), eq(SdkType.GAM))
    }

    // 5. Prebid is priority head and loads → listener notified once and GAM is destroyed (cancelOtherRequests)
    @Test
    fun prebidLoadedFirst_prebidIsFirstPriority_notifiesListenerAndCancelsGam() {
        val loader = createLoader()
        loader.loadAd()

        val banner = bannerConstruction.constructed()[0]
        val prebidListener = capturePrebidListener()

        prebidListener.onAdLoaded(banner)

        verify(mockListener, times(1)).onAdLoaded(banner, SdkType.PREBID)
        val gamView = gamViewConstruction.constructed()[0]
        verify(gamView).destroy()
    }

    // 6. GAM loads while Prebid is still priority head → listener NOT notified (GAM held back)
    @Test
    fun gamLoadedButPrebidFirstPriority_listenerNotNotified() {
        val loader = createLoader()
        loader.loadAd()

        val gamListener = captureGamListener()
        gamListener.onAdLoaded()

        verifyNoInteractions(mockListener)
    }

    // 7. Prebid fails → removed from priority → GAM succeeds afterwards → listener notified with GAM
    @Test
    fun prebidFails_thenGamSucceeds_gamSelectedAfterPriorityShift() {
        val loader = createLoader()
        loader.loadAd()

        val prebidListener = capturePrebidListener()
        prebidListener.onAdFailed(null, AdException(AdException.INTERNAL_ERROR, "prebid error"))

        val gamView = gamViewConstruction.constructed()[0]
        val gamListener = captureGamListener()
        gamListener.onAdLoaded()

        verify(mockListener).onAdLoaded(gamView, SdkType.GAM)
    }

    // 8. Prebid fails → listener receives onAdFailed with PREBID and the exception message
    @Test
    fun prebidFails_listenerReceivesOnAdFailed() {
        val loader = createLoader()
        loader.loadAd()

        val prebidListener = capturePrebidListener()
        val ex = AdException(AdException.INTERNAL_ERROR, "prebid error")
        prebidListener.onAdFailed(null, ex)

        verify(mockListener).onAdFailed(
            isNull(), eq(ex.message), eq(SdkType.PREBID)
        )
    }

    // 9. Custom priority [GAM, PREBID] → GAM loads → listener notified with GAM
    @Test
    fun customPriorityOrder_gamFirst_gamWinsImmediately() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM, SdkType.PREBID)

        val loader = createLoader()
        loader.loadAd()

        val gamView = gamViewConstruction.constructed()[0]
        val gamListener = captureGamListener()
        gamListener.onAdLoaded()

        verify(mockListener).onAdLoaded(gamView, SdkType.GAM)
        val banner = bannerConstruction.constructed()[0]
        verify(banner).destroy()
    }

    // 10. destroy() after loadAd destroys both inner loaders
    @Test
    fun destroy_destroysBothInnerLoaders() {
        val loader = createLoader()
        loader.loadAd()

        val banner = bannerConstruction.constructed()[0]
        val gamView = gamViewConstruction.constructed()[0]

        loader.destroy()

        verify(banner).destroy()
        verify(gamView).destroy()
    }

    // 11. loadAd called twice destroys previous instances before creating new ones
    @Test
    fun loadAdCalledTwice_destroysPreviousResourcesFirst() {
        val loader = createLoader()

        loader.loadAd()
        val firstBanner = bannerConstruction.constructed()[0]
        val firstGamView = gamViewConstruction.constructed()[0]

        loader.loadAd()

        verify(firstBanner).destroy()
        verify(firstGamView).destroy()
        assertEquals(2, bannerConstruction.constructed().size)
        assertEquals(2, gamViewConstruction.constructed().size)
    }

    // 12. Duplicate Prebid onAdLoaded callbacks only notify listener once (isAdLoaded guard)
    @Test
    fun prebidOnAdLoaded_calledTwice_listenerNotifiedOnce() {
        val loader = createLoader()
        loader.loadAd()

        val banner = bannerConstruction.constructed()[0]
        val prebidListener = capturePrebidListener()

        prebidListener.onAdLoaded(banner)
        prebidListener.onAdLoaded(banner)

        verify(mockListener, times(1)).onAdLoaded(banner, SdkType.PREBID)
    }

    // 13. GAM passthrough callbacks: onAdClicked, onAdImpression, onAdOpened, onAdClosed route to listener
    @Test
    fun gamPassthroughCallbacks_routedToMultiBannerViewListener() {
        val loader = createLoader()
        loader.loadAd()

        val gamListener = captureGamListener()

        gamListener.onAdClicked()
        gamListener.onAdImpression()
        gamListener.onAdOpened()
        gamListener.onAdClosed()

        verify(mockListener).onAdClicked(null, SdkType.GAM)
        verify(mockListener).onImpression(SdkType.GAM)
        verify(mockListener).onAdOpened(SdkType.GAM)
        verify(mockListener).onAdClosed(null, SdkType.GAM)
    }

    // 14. Prebid onAdDisplayed only fires listener when selectedSDK == PREBID
    @Test
    fun prebidCallback_onAdDisplayed_onlyFiresWhenSelected() {
        val loader = createLoader()
        loader.loadAd()

        val banner = bannerConstruction.constructed()[0]
        val prebidListener = capturePrebidListener()

        // selectedSDK is still null here → should be ignored
        prebidListener.onAdDisplayed(banner)
        verifyNoInteractions(mockListener)

        // Trigger selection
        prebidListener.onAdLoaded(banner)

        // Now onAdDisplayed should route
        prebidListener.onAdDisplayed(banner)
        verify(mockListener).onAdDisplayed(banner, SdkType.PREBID)
    }

    // 15. Prebid onAdClicked and onAdClosed route to listener
    @Test
    fun prebidCallback_onAdClicked_onAdClosed_routedToListener() {
        val loader = createLoader()
        loader.loadAd()

        val banner = bannerConstruction.constructed()[0]
        val prebidListener = capturePrebidListener()

        prebidListener.onAdClicked(banner)
        prebidListener.onAdClosed(banner)

        verify(mockListener).onAdClicked(banner, SdkType.PREBID)
        verify(mockListener).onAdClosed(banner, SdkType.PREBID)
    }

    // Bonus: GAM failure after Prebid already loaded → listener.onAdFailed still fires for GAM
    @Test
    fun gamFails_listenerReceivesGamFailure() {
        val loader = createLoader()
        loader.loadAd()

        val gamListener = captureGamListener()
        val loadAdError = mock(LoadAdError::class.java)
        Mockito.`when`(loadAdError.message).thenReturn("gam error")

        gamListener.onAdFailedToLoad(loadAdError)

        verify(mockListener).onAdFailed(isNull(), eq("gam error"), eq(SdkType.GAM))
    }

    // Bonus: setListener replaces previous listener
    @Test
    fun setListener_replacesPreviousListener() {
        val loader = createLoader()
        val newListener = mock(MultiBannerViewListener::class.java)
        loader.setListener(newListener)
        loader.loadAd()

        val banner = bannerConstruction.constructed()[0]
        val prebidListener = capturePrebidListener()
        prebidListener.onAdLoaded(banner)

        verify(newListener).onAdLoaded(banner, SdkType.PREBID)
        verifyNoInteractions(mockListener)
    }

    // Bonus: initial priorityOrder snapshot — mutating SdkConfigHolder after construction doesn't affect loader
    @Test
    fun priorityOrder_snapshotAtConstructionTime_isIndependentOfLaterMutations() {
        val loader = createLoader()
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM, SdkType.PREBID)

        loader.loadAd()
        val banner = bannerConstruction.constructed()[0]
        val prebidListener = capturePrebidListener()
        prebidListener.onAdLoaded(banner)

        // Prebid should still win because the snapshot taken at construction was [PREBID, GAM]
        verify(mockListener).onAdLoaded(banner, SdkType.PREBID)
        assertEquals(1, gamViewConstruction.constructed().size)
        verify(gamViewConstruction.constructed()[0]).destroy()
    }
}
