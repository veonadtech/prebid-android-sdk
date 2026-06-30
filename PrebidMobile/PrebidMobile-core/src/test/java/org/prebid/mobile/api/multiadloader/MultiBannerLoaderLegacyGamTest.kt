package org.prebid.mobile.api.multiadloader

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdView
import com.yandex.mobile.ads.banner.BannerAdEventListener
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.MockedConstruction
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.MockitoAnnotations
import org.prebid.mobile.AdSize
import org.prebid.mobile.PrebidMobile
import org.prebid.mobile.api.data.SdkType
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.multiadloader.MultiBannerLoaderLegacyGam.SdkState
import org.prebid.mobile.api.multiadloader.listeners.MultiBannerViewListener
import org.prebid.mobile.api.rendering.BannerView
import org.prebid.mobile.api.rendering.listeners.BannerViewListener
import org.prebid.mobile.configuration.SdkConfigHolder
import org.prebid.mobile.test.utils.WhiteBox
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class MultiBannerLoaderLegacyGamTest {

    @Mock
    private lateinit var mockListener: MultiBannerViewListener

    private lateinit var context: Context
    private lateinit var adSize: AdSize

    private lateinit var bannerConstruction: MockedConstruction<BannerView>
    private lateinit var gamViewConstruction: MockedConstruction<AdManagerAdView>
    private lateinit var yandexViewConstruction: MockedConstruction<BannerAdView>
    private lateinit var prebidMobileStatic: MockedStatic<PrebidMobile>

    private val configId = "test-config-id"
    private val gamAdUnitId = "/1234/test-gam-ad-unit"
    private val yandexAdUnitId = "R-M-test-yandex-unit"

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        prebidMobileStatic = Mockito.mockStatic(PrebidMobile::class.java)
        prebidMobileStatic.`when`<Boolean> { PrebidMobile.isSdkInitialized() }.thenReturn(true)
        context = Robolectric.buildActivity(Activity::class.java).create().get()
        adSize = AdSize(300, 250)
        bannerConstruction = Mockito.mockConstruction(BannerView::class.java)
        gamViewConstruction = Mockito.mockConstruction(AdManagerAdView::class.java)
        // BannerAdView's context is read inside apply{...} via View.getContext(); stub it so
        // BannerAdSize.inlineSize(context, ...) doesn't NPE on the mock.
        val testContext = context
        yandexViewConstruction = Mockito.mockConstruction(BannerAdView::class.java) { mock, _ ->
            Mockito.`when`(mock.context).thenReturn(testContext)
        }
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.PREBID, SdkType.GAM, SdkType.YANDEX)
    }

    @After
    fun tearDown() {
        bannerConstruction.close()
        gamViewConstruction.close()
        yandexViewConstruction.close()
        prebidMobileStatic.close()
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.PREBID, SdkType.GAM, SdkType.YANDEX)
    }

    private fun createLoader(
        size: AdSize? = adSize,
        cfg: String? = configId,
        gamId: String? = gamAdUnitId,
        yandexId: String? = yandexAdUnitId,
    ): MultiBannerLoaderLegacyGam {
        val loader = MultiBannerLoaderLegacyGam(context, size, cfg, gamId, yandexId)
        loader.setListener(mockListener)
        return loader
    }

    private fun capturePrebidListener(): BannerViewListener {
        val banner = bannerConstruction.constructed()[0]
        val captor = ArgumentCaptor.forClass(BannerViewListener::class.java)
        verify(banner).setBannerListener(captor.capture())
        return captor.value
    }

    private fun captureGamListener(): AdListener {
        val gamView = gamViewConstruction.constructed()[0]
        val captor = ArgumentCaptor.forClass(AdListener::class.java)
        verify(gamView).adListener = captor.capture()
        return captor.value
    }

    private fun captureYandexListener(): BannerAdEventListener {
        val yandexView = yandexViewConstruction.constructed()[0]
        val captor = ArgumentCaptor.forClass(BannerAdEventListener::class.java)
        verify(yandexView).setBannerAdEventListener(captor.capture())
        return captor.value
    }

    @Suppress("UNCHECKED_CAST")
    private fun sdkStatesOf(loader: MultiBannerLoaderLegacyGam): Map<SdkType, SdkState> =
        WhiteBox.getInternalState(loader, "sdkStates") as Map<SdkType, SdkState>

    private fun selectedSdkOf(loader: MultiBannerLoaderLegacyGam): SdkType? =
        WhiteBox.getInternalState(loader, "selectedSDK") as SdkType?

    // 1. SdkState bootstraps to NOT_STARTED for all three SDKs
    @Test
    fun init_allSdkStatesAreNotStarted() {
        val loader = createLoader()
        val states = sdkStatesOf(loader)

        assertEquals(SdkState.NOT_STARTED, states[SdkType.PREBID])
        assertEquals(SdkState.NOT_STARTED, states[SdkType.GAM])
        assertEquals(SdkState.NOT_STARTED, states[SdkType.YANDEX])
    }

    // 2. loadAd constructs all three SDK views with default priority and valid inputs
    @Test
    fun loadAd_defaultPriority_constructsAllThreeSdkLoaders() {
        val loader = createLoader()

        loader.loadAd()

        assertEquals(1, bannerConstruction.constructed().size)
        assertEquals(1, gamViewConstruction.constructed().size)
        assertEquals(1, yandexViewConstruction.constructed().size)

        verify(bannerConstruction.constructed()[0]).setAutoRefreshDelay(anyInt())
        verify(bannerConstruction.constructed()[0]).loadAd()

        val gamView = gamViewConstruction.constructed()[0]
        verify(gamView).adUnitId = gamAdUnitId
        verify(gamView).setAdSizes(any())
        verify(gamView).loadAd(any())

        val yandexView = yandexViewConstruction.constructed()[0]
        verify(yandexView).setAdUnitId(yandexAdUnitId)
        // setAdSize() and loadAd() take Kotlin non-null params; capture the event listener
        // to confirm the apply{} block (which contains those calls) ran to completion.
        captureYandexListener()
    }

    // 3. After loadAd all states transition to LOADING
    @Test
    fun loadAd_setsAllStatesToLoading() {
        val loader = createLoader()
        loader.loadAd()

        val states = sdkStatesOf(loader)
        assertEquals(SdkState.LOADING, states[SdkType.PREBID])
        assertEquals(SdkState.LOADING, states[SdkType.GAM])
        assertEquals(SdkState.LOADING, states[SdkType.YANDEX])
    }

    // 4. configId=null → Prebid fails immediately, no BannerView constructed
    @Test
    fun loadAd_configIdNull_prebidFailsImmediately() {
        val loader = createLoader(cfg = null)
        loader.loadAd()

        assertEquals(0, bannerConstruction.constructed().size)
        verify(mockListener).onAdFailed(eq("ConfigId is empty"), eq(SdkType.PREBID))
        assertEquals(SdkState.FAILED, sdkStatesOf(loader)[SdkType.PREBID])
    }

    // 5. gamAdUnitId=null → GAM fails immediately
    @Test
    fun loadAd_gamAdUnitIdNull_gamFailsImmediately() {
        val loader = createLoader(gamId = null)
        loader.loadAd()

        assertEquals(0, gamViewConstruction.constructed().size)
        verify(mockListener).onAdFailed(eq("GAM AdUnitId is empty"), eq(SdkType.GAM))
        assertEquals(SdkState.FAILED, sdkStatesOf(loader)[SdkType.GAM])
    }

    // 6. yandexAdUnitId=null → Yandex fails immediately
    @Test
    fun loadAd_yandexAdUnitIdNull_yandexFailsImmediately() {
        val loader = createLoader(yandexId = null)
        loader.loadAd()

        assertEquals(0, yandexViewConstruction.constructed().size)
        verify(mockListener).onAdFailed(eq("Yandex AdUnitId is empty"), eq(SdkType.YANDEX))
        assertEquals(SdkState.FAILED, sdkStatesOf(loader)[SdkType.YANDEX])
    }

    // 7. adSize=null → both GAM and Yandex fail (Prebid tolerates null adSize)
    @Test
    fun loadAd_adSizeNull_gamAndYandexFail() {
        val loader = createLoader(size = null)
        loader.loadAd()

        assertEquals(0, gamViewConstruction.constructed().size)
        assertEquals(0, yandexViewConstruction.constructed().size)
        verify(mockListener).onAdFailed(eq("GAM adSize is null"), eq(SdkType.GAM))
        verify(mockListener).onAdFailed(eq("Yandex adSize is null"), eq(SdkType.YANDEX))
    }

    // 8. Prebid loads first while head of priority → listener notified, GAM and Yandex destroyed
    @Test
    fun prebidLoadsFirst_prebidIsHead_notifiesListenerAndCancelsOthers() {
        val loader = createLoader()
        loader.loadAd()

        val prebidBanner = bannerConstruction.constructed()[0]
        val prebidListener = capturePrebidListener()
        prebidListener.onAdLoaded(prebidBanner)

        verify(mockListener, times(1)).onAdLoaded(prebidBanner, SdkType.PREBID)
        verify(gamViewConstruction.constructed()[0]).destroy()
        verify(yandexViewConstruction.constructed()[0]).destroy()
        assertEquals(SdkType.PREBID, selectedSdkOf(loader))
        assertEquals(SdkState.LOADED, sdkStatesOf(loader)[SdkType.PREBID])
        assertEquals(SdkState.NOT_STARTED, sdkStatesOf(loader)[SdkType.GAM])
        assertEquals(SdkState.NOT_STARTED, sdkStatesOf(loader)[SdkType.YANDEX])
    }

    // 9. GAM loads first but Prebid is head → listener NOT notified (held back)
    @Test
    fun gamLoadsFirstButPrebidIsHead_listenerNotNotified() {
        val loader = createLoader()
        loader.loadAd()

        val gamListener = captureGamListener()
        gamListener.onAdLoaded()

        verifyNoInteractions(mockListener)
        assertEquals(SdkState.LOADED, sdkStatesOf(loader)[SdkType.GAM])
        assertNull(selectedSdkOf(loader))
    }

    // 10. Yandex loads first but Prebid is head → listener NOT notified (held back)
    @Test
    fun yandexLoadsFirstButPrebidIsHead_listenerNotNotified() {
        val loader = createLoader()
        loader.loadAd()

        val yandexListener = captureYandexListener()
        yandexListener.onAdLoaded()

        verifyNoInteractions(mockListener)
        assertEquals(SdkState.LOADED, sdkStatesOf(loader)[SdkType.YANDEX])
        assertNull(selectedSdkOf(loader))
    }

    // 11. Prebid fails → trySelectAd promotes next priority head (GAM if loaded)
    @Test
    fun prebidFails_thenGamLoaded_gamSelectedAfterPriorityShift() {
        val loader = createLoader()
        loader.loadAd()

        val gamListener = captureGamListener()
        gamListener.onAdLoaded() // GAM loaded but not selected (Prebid still head)

        val prebidListener = capturePrebidListener()
        prebidListener.onAdFailed(null, AdException(AdException.INTERNAL_ERROR, "prebid err"))

        // After Prebid fails and is removed, GAM is now head and is LOADED → selected
        val gamView = gamViewConstruction.constructed()[0]
        verify(mockListener).onAdLoaded(gamView, SdkType.GAM)
        assertEquals(SdkType.GAM, selectedSdkOf(loader))
        verify(yandexViewConstruction.constructed()[0]).destroy()
    }

    // 12. Custom priority [YANDEX, GAM, PREBID]: Yandex loads → Yandex selected
    @Test
    fun customPriorityYandexFirst_yandexLoads_yandexSelected() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.YANDEX, SdkType.GAM, SdkType.PREBID)
        val loader = createLoader()
        loader.loadAd()

        val yandexListener = captureYandexListener()
        yandexListener.onAdLoaded()

        val yandexView = yandexViewConstruction.constructed()[0]
        verify(mockListener).onAdLoaded(yandexView, SdkType.YANDEX)
        verify(bannerConstruction.constructed()[0]).destroy()
        verify(gamViewConstruction.constructed()[0]).destroy()
        assertEquals(SdkType.YANDEX, selectedSdkOf(loader))
    }

    // 13. trySelectAd is idempotent: second LOADED on a non-head SDK doesn't override selection
    @Test
    fun trySelectAd_alreadySelected_doesNotReselect() {
        val loader = createLoader()
        loader.loadAd()

        val prebidListener = capturePrebidListener()
        val prebidBanner = bannerConstruction.constructed()[0]
        prebidListener.onAdLoaded(prebidBanner) // PREBID selected

        // Now GAM "loads" too (in real flow it would already be cancelled, but simulate the race)
        val gamListener = captureGamListener()
        gamListener.onAdLoaded()

        verify(mockListener, times(1)).onAdLoaded(prebidBanner, SdkType.PREBID)
        // Listener should NOT have been called for GAM
        assertEquals(SdkType.PREBID, selectedSdkOf(loader))
    }

    // 14. destroy resets all states to NOT_STARTED and destroys all three loaders
    @Test
    fun destroy_resetsAllStatesAndDestroysAllLoaders() {
        val loader = createLoader()
        loader.loadAd()

        loader.destroy()

        verify(bannerConstruction.constructed()[0]).destroy()
        verify(gamViewConstruction.constructed()[0]).destroy()
        verify(yandexViewConstruction.constructed()[0]).destroy()
        val states = sdkStatesOf(loader)
        assertEquals(SdkState.NOT_STARTED, states[SdkType.PREBID])
        assertEquals(SdkState.NOT_STARTED, states[SdkType.GAM])
        assertEquals(SdkState.NOT_STARTED, states[SdkType.YANDEX])
    }

    // 15. loadAd called twice destroys previous instances first
    @Test
    fun loadAdCalledTwice_destroysPreviousResources() {
        val loader = createLoader()

        loader.loadAd()
        val firstPrebid = bannerConstruction.constructed()[0]
        val firstGam = gamViewConstruction.constructed()[0]
        val firstYandex = yandexViewConstruction.constructed()[0]

        loader.loadAd()

        verify(firstPrebid).destroy()
        verify(firstGam).destroy()
        verify(firstYandex).destroy()
        assertEquals(2, bannerConstruction.constructed().size)
        assertEquals(2, gamViewConstruction.constructed().size)
        assertEquals(2, yandexViewConstruction.constructed().size)
    }

    // 16. Prebid duplicate onAdLoaded → listener notified once (isAdLoaded guard)
    @Test
    fun prebidOnAdLoaded_calledTwice_listenerNotifiedOnce() {
        val loader = createLoader()
        loader.loadAd()

        val prebidBanner = bannerConstruction.constructed()[0]
        val prebidListener = capturePrebidListener()
        prebidListener.onAdLoaded(prebidBanner)
        prebidListener.onAdLoaded(prebidBanner)

        verify(mockListener, times(1)).onAdLoaded(prebidBanner, SdkType.PREBID)
    }

    // 17. GAM passthrough callbacks route to listener
    @Test
    fun gamPassthroughCallbacks_routedToListener() {
        val loader = createLoader()
        loader.loadAd()

        val gamListener = captureGamListener()
        gamListener.onAdClicked()
        gamListener.onAdImpression()
        gamListener.onAdOpened()
        gamListener.onAdClosed()

        verify(mockListener).onAdClicked(null, SdkType.GAM)
        verify(mockListener).onImpression(null, SdkType.GAM)
        verify(mockListener).onAdOpened(SdkType.GAM)
        verify(mockListener).onAdClosed(null, SdkType.GAM)
    }

    // 18. Yandex passthrough callbacks: onAdClicked, onLeftApplication, onReturnedToApplication, onImpression
    @Test
    fun yandexPassthroughCallbacks_routedToListener() {
        val loader = createLoader()
        loader.loadAd()

        val yandexListener = captureYandexListener()
        val impressionData = mock(ImpressionData::class.java)

        yandexListener.onAdClicked()
        yandexListener.onLeftApplication()
        yandexListener.onReturnedToApplication()
        yandexListener.onImpression(impressionData)

        verify(mockListener).onAdClicked(null, SdkType.YANDEX)
        verify(mockListener).onLeftApplication(SdkType.YANDEX)
        verify(mockListener).onReturnedToApplication(SdkType.YANDEX)
        verify(mockListener).onImpression(impressionData, SdkType.YANDEX)
    }

    // 19. Prebid onAdDisplayed only fires when selectedSDK == PREBID
    @Test
    fun prebidCallback_onAdDisplayed_onlyFiresWhenSelected() {
        val loader = createLoader()
        loader.loadAd()

        val prebidBanner = bannerConstruction.constructed()[0]
        val prebidListener = capturePrebidListener()

        // Before selection
        prebidListener.onAdDisplayed(prebidBanner)
        verifyNoInteractions(mockListener)

        // Select Prebid
        prebidListener.onAdLoaded(prebidBanner)

        // After selection
        prebidListener.onAdDisplayed(prebidBanner)
        verify(mockListener).onAdDisplayed(prebidBanner, SdkType.PREBID)
    }

    // 20. GAM onAdFailedToLoad → listener receives error and state becomes FAILED
    @Test
    fun gamOnAdFailedToLoad_listenerReceivesGamError() {
        val loader = createLoader()
        loader.loadAd()

        val loadAdError = mock(LoadAdError::class.java)
        Mockito.`when`(loadAdError.message).thenReturn("gam error")

        val gamListener = captureGamListener()
        gamListener.onAdFailedToLoad(loadAdError)

        verify(mockListener).onAdFailed(eq("gam error"), eq(SdkType.GAM))
        assertEquals(SdkState.FAILED, sdkStatesOf(loader)[SdkType.GAM])
    }

    // 21. Yandex onAdFailedToLoad → listener receives Yandex error and state becomes FAILED
    @Test
    fun yandexOnAdFailedToLoad_listenerReceivesYandexError() {
        val loader = createLoader()
        loader.loadAd()

        val error = mock(AdRequestError::class.java)
        Mockito.`when`(error.description).thenReturn("yandex error")

        val yandexListener = captureYandexListener()
        yandexListener.onAdFailedToLoad(error)

        verify(mockListener).onAdFailed(eq("yandex error"), eq(SdkType.YANDEX))
        assertEquals(SdkState.FAILED, sdkStatesOf(loader)[SdkType.YANDEX])
    }

    // 22. All SDKs fail → no selection happens, three onAdFailed events fire
    @Test
    fun allSdksFail_noSelectionAndThreeFailures() {
        val loader = createLoader()
        loader.loadAd()

        val prebidException = AdException(AdException.INTERNAL_ERROR, "prebid err")
        val prebidListener = capturePrebidListener()
        prebidListener.onAdFailed(null, prebidException)

        val gamLoadAdError = mock(LoadAdError::class.java)
        Mockito.`when`(gamLoadAdError.message).thenReturn("gam err")
        val gamListener = captureGamListener()
        gamListener.onAdFailedToLoad(gamLoadAdError)

        val yandexErr = mock(AdRequestError::class.java)
        Mockito.`when`(yandexErr.description).thenReturn("yandex err")
        val yandexListener = captureYandexListener()
        yandexListener.onAdFailedToLoad(yandexErr)

        verify(mockListener).onAdFailed(eq(prebidException.message), eq(SdkType.PREBID))
        verify(mockListener).onAdFailed(eq("gam err"), eq(SdkType.GAM))
        verify(mockListener).onAdFailed(eq("yandex err"), eq(SdkType.YANDEX))
        assertNull(selectedSdkOf(loader))
        // No onAdLoaded was called: selectedSDK == null is the canonical guarantee
        verifyNoMoreInteractions(mockListener)
    }

    // 23. setListener replaces previous listener
    @Test
    fun setListener_replacesPreviousListener() {
        val loader = createLoader()
        val newListener = mock(MultiBannerViewListener::class.java)
        loader.setListener(newListener)

        loader.loadAd()
        val prebidBanner = bannerConstruction.constructed()[0]
        val prebidListener = capturePrebidListener()
        prebidListener.onAdLoaded(prebidBanner)

        verify(newListener).onAdLoaded(prebidBanner, SdkType.PREBID)
        verifyNoInteractions(mockListener)
    }

    // 24. Prebid onAdClicked and onAdClosed pass through
    @Test
    fun prebidPassthroughCallbacks_onAdClicked_onAdClosed_routedToListener() {
        val loader = createLoader()
        loader.loadAd()

        val prebidBanner = bannerConstruction.constructed()[0]
        val prebidListener = capturePrebidListener()
        prebidListener.onAdClicked(prebidBanner)
        prebidListener.onAdClosed(prebidBanner)

        verify(mockListener).onAdClicked(prebidBanner, SdkType.PREBID)
        verify(mockListener).onAdClosed(prebidBanner, SdkType.PREBID)
    }

    // 25. Prebid fails first, then Yandex loads (GAM also fails) → Yandex selected
    @Test
    fun prebidAndGamFail_yandexLoads_yandexSelected() {
        val loader = createLoader()
        loader.loadAd()

        val prebidListener = capturePrebidListener()
        prebidListener.onAdFailed(null, AdException(AdException.INTERNAL_ERROR, "prebid err"))

        val gamLoadAdError = mock(LoadAdError::class.java)
        Mockito.`when`(gamLoadAdError.message).thenReturn("gam err")
        val gamListener = captureGamListener()
        gamListener.onAdFailedToLoad(gamLoadAdError)

        val yandexListener = captureYandexListener()
        yandexListener.onAdLoaded()

        val yandexView = yandexViewConstruction.constructed()[0]
        verify(mockListener).onAdLoaded(yandexView, SdkType.YANDEX)
        assertEquals(SdkType.YANDEX, selectedSdkOf(loader))
    }

    // 26. Empty configId (not null) is treated same as null for Prebid
    @Test
    fun loadAd_emptyConfigId_prebidFailsSameAsNull() {
        val loader = createLoader(cfg = "")
        loader.loadAd()

        assertEquals(0, bannerConstruction.constructed().size)
        verify(mockListener).onAdFailed(eq("ConfigId is empty"), eq(SdkType.PREBID))
    }
}
