package org.prebid.mobile.api.multiadloader

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd
import com.google.android.gms.ads.admanager.AdManagerInterstitialAdLoadCallback
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.interstitial.InterstitialAd
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mock
import org.mockito.MockedConstruction
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.MockitoAnnotations
import org.prebid.mobile.api.data.SdkType
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.multiadloader.MultiInterstitialAdLoaderLegacyGam.SdkState
import org.prebid.mobile.api.multiadloader.listeners.MultiInterstitialAdListener
import org.prebid.mobile.api.rendering.InterstitialAdUnit
import org.prebid.mobile.api.rendering.listeners.InterstitialAdUnitListener
import org.prebid.mobile.configuration.SdkConfigHolder
import org.prebid.mobile.test.utils.WhiteBox
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class MultiInterstitialAdLoaderLegacyGamTest {

    @Mock
    private lateinit var mockListener: MultiInterstitialAdListener

    private lateinit var context: Context

    private lateinit var prebidConstruction: MockedConstruction<InterstitialAdUnit>
    private lateinit var gamInterstitialStatic: MockedStatic<AdManagerInterstitialAd>
    private lateinit var yandexLoaderConstruction: MockedConstruction<InterstitialAdLoader>

    private val configId = "test-config-id"
    private val gamAdUnitId = "/1234/test-gam-ad-unit"
    private val yandexAdUnitId = "R-M-test-yandex-unit"

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        context = Robolectric.buildActivity(Activity::class.java).create().get()
        prebidConstruction = Mockito.mockConstruction(InterstitialAdUnit::class.java)
        gamInterstitialStatic = Mockito.mockStatic(AdManagerInterstitialAd::class.java)
        yandexLoaderConstruction = Mockito.mockConstruction(InterstitialAdLoader::class.java)
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.PREBID, SdkType.GAM, SdkType.YANDEX)
    }

    @After
    fun tearDown() {
        prebidConstruction.close()
        gamInterstitialStatic.close()
        yandexLoaderConstruction.close()
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.PREBID, SdkType.GAM, SdkType.YANDEX)
    }

    private fun createLoader(
        cfg: String? = configId,
        gamId: String? = gamAdUnitId,
        yandexId: String? = yandexAdUnitId,
    ): MultiInterstitialAdLoaderLegacyGam {
        val loader = MultiInterstitialAdLoaderLegacyGam(context, cfg, gamId, yandexId)
        loader.setListener(mockListener)
        return loader
    }

    private fun capturePrebidListener(): InterstitialAdUnitListener {
        val unit = prebidConstruction.constructed()[0]
        val captor = ArgumentCaptor.forClass(InterstitialAdUnitListener::class.java)
        verify(unit).setInterstitialAdUnitListener(captor.capture())
        return captor.value
    }

    private fun captureGamCallback(expectedTimes: Int = 1): AdManagerInterstitialAdLoadCallback {
        val captor = ArgumentCaptor.forClass(AdManagerInterstitialAdLoadCallback::class.java)
        gamInterstitialStatic.verify({
            AdManagerInterstitialAd.load(
                any(Context::class.java),
                anyString(),
                any(AdManagerAdRequest::class.java),
                captor.capture()
            )
        }, times(expectedTimes))
        return captor.value
    }

    private fun captureYandexLoadListener(): InterstitialAdLoadListener {
        val loader = yandexLoaderConstruction.constructed()[0]
        val captor = ArgumentCaptor.forClass(InterstitialAdLoadListener::class.java)
        verify(loader).setAdLoadListener(captor.capture())
        return captor.value
    }

    /**
     * Drives Yandex's two-step load: first the loader's onAdLoaded gets a mock InterstitialAd,
     * then captures the event listener attached to that ad. Returns the event listener.
     */
    private fun deliverYandexLoadedAd(yandexAd: InterstitialAd): InterstitialAdEventListener {
        captureYandexLoadListener().onAdLoaded(yandexAd)
        val captor = ArgumentCaptor.forClass(InterstitialAdEventListener::class.java)
        verify(yandexAd).setAdEventListener(captor.capture())
        return captor.value
    }

    @Suppress("UNCHECKED_CAST")
    private fun sdkStatesOf(loader: MultiInterstitialAdLoaderLegacyGam): Map<SdkType, SdkState> =
        WhiteBox.getInternalState(loader, "sdkStates") as Map<SdkType, SdkState>

    private fun selectedSdkOf(loader: MultiInterstitialAdLoaderLegacyGam): SdkType? =
        WhiteBox.getInternalState(loader, "selectedSDK") as SdkType?

    // 1. All SDK states bootstrap to NOT_STARTED
    @Test
    fun init_allSdkStatesAreNotStarted() {
        val loader = createLoader()
        val states = sdkStatesOf(loader)

        assertEquals(SdkState.NOT_STARTED, states[SdkType.PREBID])
        assertEquals(SdkState.NOT_STARTED, states[SdkType.GAM])
        assertEquals(SdkState.NOT_STARTED, states[SdkType.YANDEX])
        assertNull(selectedSdkOf(loader))
    }

    // 2. Default priority [PREBID, GAM, YANDEX] → all three SDK loaders triggered
    @Test
    fun loadAd_defaultPriority_loadsAllThreeSdks() {
        val loader = createLoader()
        loader.loadAd()

        // GAM static load was invoked
        gamInterstitialStatic.verify {
            AdManagerInterstitialAd.load(
                any(Context::class.java),
                eq(gamAdUnitId),
                any(AdManagerAdRequest::class.java),
                any(AdManagerInterstitialAdLoadCallback::class.java)
            )
        }
        assertEquals(1, prebidConstruction.constructed().size)
        verify(prebidConstruction.constructed()[0]).loadAd()
        assertEquals(1, yandexLoaderConstruction.constructed().size)
        // loadAd(AdRequestConfiguration) is Kotlin non-null; capture the load listener instead
        // to confirm the apply{} block ran to completion.
        captureYandexLoadListener()

        val states = sdkStatesOf(loader)
        assertEquals(SdkState.LOADING, states[SdkType.PREBID])
        assertEquals(SdkState.LOADING, states[SdkType.GAM])
        assertEquals(SdkState.LOADING, states[SdkType.YANDEX])
    }

    // 3. Custom priority [YANDEX, GAM, PREBID] → Prebid is held back (per maybeLoadPrebid logic)
    @Test
    fun loadAd_yandexFirstPriority_prebidNotLoadedInitially() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.YANDEX, SdkType.GAM, SdkType.PREBID)
        val loader = createLoader()
        loader.loadAd()

        assertEquals(0, prebidConstruction.constructed().size)
        assertEquals(1, yandexLoaderConstruction.constructed().size)
        gamInterstitialStatic.verify {
            AdManagerInterstitialAd.load(
                any(Context::class.java),
                eq(gamAdUnitId),
                any(AdManagerAdRequest::class.java),
                any(AdManagerInterstitialAdLoadCallback::class.java)
            )
        }

        val states = sdkStatesOf(loader)
        assertEquals(SdkState.NOT_STARTED, states[SdkType.PREBID])
        assertEquals(SdkState.LOADING, states[SdkType.GAM])
        assertEquals(SdkState.LOADING, states[SdkType.YANDEX])
    }

    // 4. configId=null → Prebid fails immediately
    @Test
    fun loadAd_configIdNull_prebidFailsWithConfigIdEmpty() {
        val loader = createLoader(cfg = null)
        loader.loadAd()

        assertEquals(0, prebidConstruction.constructed().size)
        verify(mockListener).onAdFailed(eq("ConfigId is empty"), eq(SdkType.PREBID))
        assertEquals(SdkState.FAILED, sdkStatesOf(loader)[SdkType.PREBID])
    }

    // 5. gamAdUnitId=null → GAM fails immediately and static load is never called
    @Test
    fun loadAd_gamAdUnitIdNull_gamFailsWithoutStaticCall() {
        val loader = createLoader(gamId = null)
        loader.loadAd()

        gamInterstitialStatic.verifyNoInteractions()
        verify(mockListener).onAdFailed(eq("GAM AdUnitId is empty"), eq(SdkType.GAM))
        assertEquals(SdkState.FAILED, sdkStatesOf(loader)[SdkType.GAM])
    }

    // 6. yandexAdUnitId=null → Yandex fails immediately and InterstitialAdLoader never constructed
    @Test
    fun loadAd_yandexAdUnitIdNull_yandexFailsWithoutLoaderConstruction() {
        val loader = createLoader(yandexId = null)
        loader.loadAd()

        assertEquals(0, yandexLoaderConstruction.constructed().size)
        verify(mockListener).onAdFailed(eq("Yandex AdUnitId is empty"), eq(SdkType.YANDEX))
        assertEquals(SdkState.FAILED, sdkStatesOf(loader)[SdkType.YANDEX])
    }

    // 7. Prebid loads first (it is head) → listener notified, GAM and Yandex destroyed
    @Test
    fun prebidLoadsFirst_notifiesAndCancelsOthers() {
        val loader = createLoader()
        loader.loadAd()

        val prebidListener = capturePrebidListener()
        prebidListener.onAdLoaded(prebidConstruction.constructed()[0])

        verify(mockListener, times(1)).onAdLoaded(SdkType.PREBID)
        val states = sdkStatesOf(loader)
        assertEquals(SdkState.LOADED, states[SdkType.PREBID])
        assertEquals(SdkState.NOT_STARTED, states[SdkType.GAM])
        assertEquals(SdkState.NOT_STARTED, states[SdkType.YANDEX])
        assertEquals(SdkType.PREBID, selectedSdkOf(loader))
    }

    // 8. GAM loads but PREBID is head → listener not notified yet
    @Test
    fun gamLoadsFirstButPrebidHead_listenerNotNotified() {
        val loader = createLoader()
        loader.loadAd()

        val gamCallback = captureGamCallback()
        gamCallback.onAdLoaded(mock(AdManagerInterstitialAd::class.java))

        verifyNoInteractions(mockListener)
        assertEquals(SdkState.LOADED, sdkStatesOf(loader)[SdkType.GAM])
        assertNull(selectedSdkOf(loader))
    }

    // 9. Yandex loads but PREBID is head → listener not notified yet
    @Test
    fun yandexLoadsFirstButPrebidHead_listenerNotNotified() {
        val loader = createLoader()
        loader.loadAd()

        deliverYandexLoadedAd(mock(InterstitialAd::class.java))

        verifyNoInteractions(mockListener)
        assertEquals(SdkState.LOADED, sdkStatesOf(loader)[SdkType.YANDEX])
        assertNull(selectedSdkOf(loader))
    }

    // 10. GAM loaded then Prebid fails → GAM becomes selected (next priority head)
    @Test
    fun gamLoaded_thenPrebidFails_gamSelected() {
        val loader = createLoader()
        loader.loadAd()

        val gamCallback = captureGamCallback()
        gamCallback.onAdLoaded(mock(AdManagerInterstitialAd::class.java))

        val prebidListener = capturePrebidListener()
        val prebidException = AdException(AdException.INTERNAL_ERROR, "prebid err")
        prebidListener.onAdFailed(prebidConstruction.constructed()[0], prebidException)

        verify(mockListener).onAdLoaded(SdkType.GAM)
        verify(mockListener).onAdFailed(eq(prebidException.message), eq(SdkType.PREBID))
        assertEquals(SdkType.GAM, selectedSdkOf(loader))
    }

    // 11. Prebid fails first, then Yandex loads (but GAM is in between in priority and still LOADING) → Yandex NOT selected (GAM still ahead)
    @Test
    fun prebidFails_thenYandexLoadsButGamStillLoading_yandexNotSelected() {
        val loader = createLoader()
        loader.loadAd()

        val prebidListener = capturePrebidListener()
        prebidListener.onAdFailed(
            prebidConstruction.constructed()[0],
            AdException(AdException.INTERNAL_ERROR, "prebid err")
        )

        // Yandex loads but GAM is still loading and is ahead in priority
        deliverYandexLoadedAd(mock(InterstitialAd::class.java))

        // Yandex should NOT be selected: priorityOrder = [GAM, YANDEX], GAM is LOADING (not LOADED)
        // Listener saw onAdFailed for PREBID, but NOT onAdLoaded for YANDEX
        verify(mockListener, never()).onAdLoaded(SdkType.YANDEX)
        assertNull(selectedSdkOf(loader))
        assertEquals(SdkState.LOADED, sdkStatesOf(loader)[SdkType.YANDEX])
    }

    // 12. Prebid fails, GAM fails, Yandex loaded → Yandex selected (Yandex is now head of priority)
    @Test
    fun prebidAndGamFail_yandexLoaded_yandexSelected() {
        val loader = createLoader()
        loader.loadAd()

        val prebidListener = capturePrebidListener()
        prebidListener.onAdFailed(
            prebidConstruction.constructed()[0],
            AdException(AdException.INTERNAL_ERROR, "prebid err")
        )

        val gamErr = mock(LoadAdError::class.java)
        Mockito.`when`(gamErr.message).thenReturn("gam err")
        captureGamCallback().onAdFailedToLoad(gamErr)

        deliverYandexLoadedAd(mock(InterstitialAd::class.java))

        verify(mockListener).onAdLoaded(SdkType.YANDEX)
        assertEquals(SdkType.YANDEX, selectedSdkOf(loader))
    }

    // 13. All SDKs fail → listener gets three onAdFailed, no selection
    @Test
    fun allSdksFail_threeFailuresAndNoSelection() {
        val loader = createLoader()
        loader.loadAd()

        val prebidException = AdException(AdException.INTERNAL_ERROR, "prebid err")
        capturePrebidListener().onAdFailed(prebidConstruction.constructed()[0], prebidException)

        val gamErr = mock(LoadAdError::class.java)
        Mockito.`when`(gamErr.message).thenReturn("gam err")
        captureGamCallback().onAdFailedToLoad(gamErr)

        val yandexErr = mock(AdRequestError::class.java)
        Mockito.`when`(yandexErr.description).thenReturn("yandex err")
        captureYandexLoadListener().onAdFailedToLoad(yandexErr)

        verify(mockListener).onAdFailed(eq(prebidException.message), eq(SdkType.PREBID))
        verify(mockListener).onAdFailed(eq("gam err"), eq(SdkType.GAM))
        verify(mockListener).onAdFailed(eq("yandex err"), eq(SdkType.YANDEX))
        verify(mockListener, never()).onAdLoaded(SdkType.PREBID)
        verify(mockListener, never()).onAdLoaded(SdkType.GAM)
        verify(mockListener, never()).onAdLoaded(SdkType.YANDEX)
        assertNull(selectedSdkOf(loader))
    }

    // 14. showAd with no selected SDK → onAdFailedToShow fires
    @Test
    fun showAd_noSelectedSdk_callsOnAdFailedToShow() {
        val loader = createLoader()
        loader.showAd()

        verify(mockListener).onAdFailedToShow(eq("No loaded interstitial ad to show"), isNull())
    }

    // 15. showAd PREBID selected → delegates to InterstitialAdUnit.show()
    @Test
    fun showAd_prebidSelected_delegatesToPrebidShow() {
        val loader = createLoader()
        loader.loadAd()

        val prebidUnit = prebidConstruction.constructed()[0]
        capturePrebidListener().onAdLoaded(prebidUnit)

        loader.showAd()
        verify(prebidUnit).show()
    }

    // 16. showAd GAM selected → delegates to AdManagerInterstitialAd.show(activity)
    @Test
    fun showAd_gamSelected_delegatesToGamShowWithActivity() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM, SdkType.PREBID, SdkType.YANDEX)
        val loader = createLoader()
        loader.loadAd()

        val gamAd = mock(AdManagerInterstitialAd::class.java)
        captureGamCallback().onAdLoaded(gamAd)

        loader.showAd()
        verify(gamAd).show(context as Activity)
    }

    // 17. showAd YANDEX selected → delegates to InterstitialAd.show(activity)
    @Test
    fun showAd_yandexSelected_delegatesToYandexShowWithActivity() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.YANDEX, SdkType.GAM, SdkType.PREBID)
        val loader = createLoader()
        loader.loadAd()

        val yandexAd = mock(InterstitialAd::class.java)
        deliverYandexLoadedAd(yandexAd)

        loader.showAd()
        // Yandex InterstitialAd.show(Activity) takes a Kotlin non-null param; pass the real
        // Robolectric activity rather than any() (which would be null and throw).
        verify(yandexAd).show(context as Activity)
    }

    // 18. destroy resets all states to NOT_STARTED
    @Test
    fun destroy_resetsAllStatesToNotStarted() {
        val loader = createLoader()
        loader.loadAd()

        loader.destroy()

        verify(prebidConstruction.constructed()[0]).destroy()
        val states = sdkStatesOf(loader)
        assertEquals(SdkState.NOT_STARTED, states[SdkType.PREBID])
        assertEquals(SdkState.NOT_STARTED, states[SdkType.GAM])
        assertEquals(SdkState.NOT_STARTED, states[SdkType.YANDEX])
    }

    // 19. maybeLoadPrebid: priority [YANDEX, GAM, PREBID], YANDEX and GAM both fail → PREBID loaded lazily
    @Test
    fun maybeLoadPrebid_yandexAndGamFail_prebidLoadedLazily() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.YANDEX, SdkType.GAM, SdkType.PREBID)
        val loader = createLoader()
        loader.loadAd()

        // Initially PREBID not loaded
        assertEquals(0, prebidConstruction.constructed().size)

        // Yandex fails
        val yandexErr = mock(AdRequestError::class.java)
        Mockito.`when`(yandexErr.description).thenReturn("yandex err")
        captureYandexLoadListener().onAdFailedToLoad(yandexErr)

        // PREBID still not loaded — GAM is still LOADING (not all higher priorities failed)
        assertEquals(0, prebidConstruction.constructed().size)

        // GAM fails
        val gamErr = mock(LoadAdError::class.java)
        Mockito.`when`(gamErr.message).thenReturn("gam err")
        captureGamCallback().onAdFailedToLoad(gamErr)

        // Now both higher priorities failed → PREBID loaded
        assertEquals(1, prebidConstruction.constructed().size)
        verify(prebidConstruction.constructed()[0]).loadAd()
    }

    // 20. maybeLoadPrebid: priority [GAM, YANDEX] (no PREBID) → GAM fails → PREBID never loaded
    @Test
    fun maybeLoadPrebid_prebidNotInPriority_doesNotLoad() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM, SdkType.YANDEX)
        val loader = createLoader()
        loader.loadAd()

        val gamErr = mock(LoadAdError::class.java)
        Mockito.`when`(gamErr.message).thenReturn("gam err")
        captureGamCallback().onAdFailedToLoad(gamErr)

        assertEquals(0, prebidConstruction.constructed().size)
    }

    // 21. Prebid passthrough callbacks: onAdDisplayed, onAdClicked, onAdClosed
    @Test
    fun prebidPassthroughCallbacks_routedToListener() {
        val loader = createLoader()
        loader.loadAd()

        val prebidUnit = prebidConstruction.constructed()[0]
        val prebidListener = capturePrebidListener()
        prebidListener.onAdDisplayed(prebidUnit)
        prebidListener.onAdClicked(prebidUnit)
        prebidListener.onAdClosed(prebidUnit)

        verify(mockListener).onAdDisplayed(SdkType.PREBID)
        verify(mockListener).onAdClicked(SdkType.PREBID)
        verify(mockListener).onAdClosed(SdkType.PREBID)
    }

    // 22. Yandex event listener callbacks: onAdShown, onAdFailedToShow, onAdDismissed, onAdClicked, onAdImpression
    @Test
    fun yandexEventCallbacks_routedToListener() {
        val loader = createLoader()
        loader.loadAd()

        val yandexAd = mock(InterstitialAd::class.java)
        val eventListener = deliverYandexLoadedAd(yandexAd)

        val showError = mock(AdError::class.java)
        Mockito.`when`(showError.description).thenReturn("show error")
        val impressionData = mock(ImpressionData::class.java)

        eventListener.onAdShown()
        eventListener.onAdFailedToShow(showError)
        eventListener.onAdDismissed()
        eventListener.onAdClicked()
        eventListener.onAdImpression(impressionData)

        verify(mockListener).onAdDisplayed(SdkType.YANDEX)
        verify(mockListener).onAdFailedToShow(eq("show error"), eq(SdkType.YANDEX))
        verify(mockListener).onAdClosed(SdkType.YANDEX)
        verify(mockListener).onAdClicked(SdkType.YANDEX)
        verify(mockListener).onImpression(impressionData, SdkType.YANDEX)
    }

    // 23. GAM onAdFailedToLoad → listener receives error
    @Test
    fun gamOnAdFailedToLoad_listenerReceivesError() {
        val loader = createLoader()
        loader.loadAd()

        val gamErr = mock(LoadAdError::class.java)
        Mockito.`when`(gamErr.message).thenReturn("gam specific")

        captureGamCallback().onAdFailedToLoad(gamErr)

        verify(mockListener).onAdFailed(eq("gam specific"), eq(SdkType.GAM))
        assertEquals(SdkState.FAILED, sdkStatesOf(loader)[SdkType.GAM])
    }

    // 24. Yandex load-listener onAdFailedToLoad → listener receives error
    @Test
    fun yandexOnAdFailedToLoad_listenerReceivesError() {
        val loader = createLoader()
        loader.loadAd()

        val yandexErr = mock(AdRequestError::class.java)
        Mockito.`when`(yandexErr.description).thenReturn("yandex specific")

        captureYandexLoadListener().onAdFailedToLoad(yandexErr)

        verify(mockListener).onAdFailed(eq("yandex specific"), eq(SdkType.YANDEX))
        assertEquals(SdkState.FAILED, sdkStatesOf(loader)[SdkType.YANDEX])
    }

    // 25. loadAd called twice destroys previous Prebid instance
    @Test
    fun loadAdCalledTwice_destroysPreviousPrebid() {
        val loader = createLoader()

        loader.loadAd()
        val firstPrebid = prebidConstruction.constructed()[0]

        loader.loadAd()

        verify(firstPrebid).destroy()
        assertEquals(2, prebidConstruction.constructed().size)
    }

    // 26. setListener replaces previous listener
    @Test
    fun setListener_replacesPreviousListener() {
        val loader = createLoader()
        val newListener = mock(MultiInterstitialAdListener::class.java)
        loader.setListener(newListener)

        loader.loadAd()
        capturePrebidListener().onAdLoaded(prebidConstruction.constructed()[0])

        verify(newListener).onAdLoaded(SdkType.PREBID)
        verifyNoInteractions(mockListener)
    }

    // 27. Prebid onAdFailed with null exception message → listener gets "Unknown error"
    @Test
    fun prebidOnAdFailed_nullException_listenerReceivesUnknownError() {
        val loader = createLoader()
        loader.loadAd()

        capturePrebidListener().onAdFailed(prebidConstruction.constructed()[0], null)

        verify(mockListener).onAdFailed(eq("Unknown error"), eq(SdkType.PREBID))
        assertEquals(SdkState.FAILED, sdkStatesOf(loader)[SdkType.PREBID])
    }
}
