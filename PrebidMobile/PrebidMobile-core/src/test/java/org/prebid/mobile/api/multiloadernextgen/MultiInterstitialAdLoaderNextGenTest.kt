package org.prebid.mobile.api.multiloadernextgen

import android.app.Activity
import android.content.Context
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
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
import org.prebid.mobile.PrebidMobile
import org.prebid.mobile.api.data.SdkType
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.multiloadernextgen.MultiInterstitialAdLoaderNextGen.SdkState
import org.prebid.mobile.api.multiloadernextgen.listeners.MultiInterstitialAdListenerNextGen
import org.prebid.mobile.api.rendering.InterstitialAdUnit
import org.prebid.mobile.api.rendering.listeners.InterstitialAdUnitListener
import org.prebid.mobile.configuration.SdkConfigHolder
import org.prebid.mobile.test.utils.WhiteBox
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class MultiInterstitialAdLoaderNextGenTest {

    @Mock
    private lateinit var mockListener: MultiInterstitialAdListenerNextGen

    private lateinit var context: Context

    private lateinit var interstitialConstruction: MockedConstruction<InterstitialAdUnit>
    private lateinit var mockedPrebidMobile: MockedStatic<PrebidMobile>

    // Injected GAM-load seam capture (the Next-Gen SDK's InterstitialAd.load is a Kotlin
    // companion method and cannot be intercepted by Mockito's mockStatic).
    private var gamLoadCount = 0
    private var capturedGamRequest: AdRequest? = null
    private var capturedGamCallback: AdLoadCallback<InterstitialAd>? = null

    private val configId = "test-config-id"
    private val gamAdUnitId = "/1234/test-gam-ad-unit"
    private val yandexAdUnitId = "demo-interstitial-yandex"

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        context = Robolectric.buildActivity(Activity::class.java).create().get()
        interstitialConstruction = Mockito.mockConstruction(InterstitialAdUnit::class.java)

        gamLoadCount = 0
        capturedGamRequest = null
        capturedGamCallback = null

        mockedPrebidMobile = Mockito.mockStatic(PrebidMobile::class.java)
        mockedPrebidMobile.`when`<Boolean> { PrebidMobile.isSdkInitialized() }.thenReturn(true)

        // Race only PREBID vs GAM; YANDEX uses its own SDK and is exercised in instrumentation/demo.
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.PREBID, SdkType.GAM)
    }

    @After
    fun tearDown() {
        interstitialConstruction.close()
        mockedPrebidMobile.close()
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.PREBID, SdkType.GAM, SdkType.YANDEX)
    }

    private fun createLoader(
        cfg: String? = configId,
        gamId: String? = gamAdUnitId,
        yandexId: String? = yandexAdUnitId,
    ): MultiInterstitialAdLoaderNextGen {
        val loader = MultiInterstitialAdLoaderNextGen(context, cfg, gamId, yandexId) { request, callback ->
            gamLoadCount++
            capturedGamRequest = request
            capturedGamCallback = callback
        }
        loader.setListener(mockListener)
        return loader
    }

    private fun capturePrebidListener(instanceIndex: Int = 0): InterstitialAdUnitListener {
        val unit = interstitialConstruction.constructed()[instanceIndex]
        val captor = ArgumentCaptor.forClass(InterstitialAdUnitListener::class.java)
        verify(unit).setInterstitialAdUnitListener(captor.capture())
        return captor.value
    }

    // Kotlin-safe non-null matcher: Mockito's any(Class) returns null which violates
    // the Next-Gen SDK's non-null Kotlin parameters (throws "any(...) must not be null").
    // The return is routed through an unbounded generic so Kotlin does NOT emit a
    // non-null check (a direct `null as SomeType` would throw at runtime).
    @Suppress("UNCHECKED_CAST")
    private fun <T> anyNonNull(type: Class<T>): T {
        Mockito.any<T>(type)
        return uninitialized()
    }

    // Unbounded-generic identity casts: erase to no-op, so they can carry a null
    // (e.g. a captor.capture() recording value) into a non-null SDK parameter slot.
    @Suppress("UNCHECKED_CAST")
    private fun <T> uninitialized(): T = null as T

    @Suppress("UNCHECKED_CAST")
    private fun <T> cast(value: Any?): T = value as T

    private fun captureGamCallback(expectedTimes: Int = 1): AdLoadCallback<InterstitialAd> {
        assertEquals(expectedTimes, gamLoadCount)
        return capturedGamCallback!!
    }

    @Suppress("UNCHECKED_CAST")
    private fun sdkStatesOf(loader: MultiInterstitialAdLoaderNextGen): Map<SdkType, SdkState> =
        WhiteBox.getInternalState(loader, "sdkStates") as Map<SdkType, SdkState>

    private fun selectedSdkOf(loader: MultiInterstitialAdLoaderNextGen): SdkType? =
        WhiteBox.getInternalState(loader, "selectedSDK") as SdkType?

    // 1. On construction all SDK states are NOT_STARTED
    @Test
    fun init_allSdkStatesAreNotStarted() {
        val loader = createLoader()
        val states = sdkStatesOf(loader)

        assertEquals(SdkState.NOT_STARTED, states[SdkType.PREBID])
        assertEquals(SdkState.NOT_STARTED, states[SdkType.GAM])
        assertNull(selectedSdkOf(loader))
    }

    // 2. With default priority PREBID-first, both SDKs are loaded
    @Test
    fun loadAd_prebidFirstPriority_loadsGamAndPrebid() {
        val loader = createLoader()

        loader.loadAd()

        // GAM load was called
        assertEquals(1, gamLoadCount)
        // PREBID was constructed and loadAd invoked
        assertEquals(1, interstitialConstruction.constructed().size)
        verify(interstitialConstruction.constructed()[0]).loadAd()

        val states = sdkStatesOf(loader)
        assertEquals(SdkState.LOADING, states[SdkType.PREBID])
        assertEquals(SdkState.LOADING, states[SdkType.GAM])
    }

    // 3. Custom priority [GAM, PREBID] -> only GAM loads initially, PREBID held back
    @Test
    fun loadAd_gamFirstPriority_prebidNotLoadedInitially() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM, SdkType.PREBID)

        val loader = createLoader()
        loader.loadAd()

        assertEquals(1, gamLoadCount)
        assertEquals(0, interstitialConstruction.constructed().size)

        val states = sdkStatesOf(loader)
        assertEquals(SdkState.LOADING, states[SdkType.GAM])
        assertEquals(SdkState.NOT_STARTED, states[SdkType.PREBID])
    }

    // 4. configId=null -> PREBID fails immediately
    @Test
    fun loadAd_configIdNull_prebidFailsWithConfigIdIsEmpty() {
        val loader = createLoader(cfg = null)

        loader.loadAd()

        assertEquals(0, interstitialConstruction.constructed().size)
        verify(mockListener).onAdFailed(eq("ConfigId is empty"), eq(SdkType.PREBID))
        assertEquals(SdkState.FAILED, sdkStatesOf(loader)[SdkType.PREBID])
    }

    // 5. gamAdUnitId=null -> GAM fails immediately
    @Test
    fun loadAd_gamAdUnitIdNull_gamFailsWithAdUnitIdIsEmpty() {
        val loader = createLoader(gamId = null)

        loader.loadAd()

        // GAM.load was not called (null path returns before the load seam)
        assertEquals(0, gamLoadCount)
        verify(mockListener).onAdFailed(eq("GAM AdUnitId is empty"), eq(SdkType.GAM))
        assertEquals(SdkState.FAILED, sdkStatesOf(loader)[SdkType.GAM])
    }

    // 6. PREBID loads first with default priority -> listener notified, GAM destroyed and reset
    @Test
    fun prebidLoadedFirst_notifiesListenerAndCancelsGam() {
        val loader = createLoader()
        loader.loadAd()

        val prebidListener = capturePrebidListener()
        prebidListener.onAdLoaded(interstitialConstruction.constructed()[0])

        verify(mockListener, times(1)).onAdLoaded(SdkType.PREBID)
        val states = sdkStatesOf(loader)
        assertEquals(SdkState.LOADED, states[SdkType.PREBID])
        assertEquals(SdkState.NOT_STARTED, states[SdkType.GAM])
        assertEquals(SdkType.PREBID, selectedSdkOf(loader))
    }

    // 7. GAM loads but PREBID is first priority and hasn't responded yet -> listener not notified
    @Test
    fun gamLoadedButPrebidFirstPriority_listenerNotNotifiedYet() {
        val loader = createLoader()
        loader.loadAd()

        val gamCallback = captureGamCallback()
        gamCallback.onAdLoaded(mock(InterstitialAd::class.java))

        verifyNoInteractions(mockListener)
        val states = sdkStatesOf(loader)
        assertEquals(SdkState.LOADED, states[SdkType.GAM])
        assertEquals(SdkState.LOADING, states[SdkType.PREBID])
        assertNull(selectedSdkOf(loader))
    }

    // 8. GAM loads, then PREBID fails -> GAM becomes first priority and is selected
    @Test
    fun gamLoaded_thenPrebidFails_gamBecomesSelected() {
        val loader = createLoader()
        loader.loadAd()

        val gamCallback = captureGamCallback()
        gamCallback.onAdLoaded(mock(InterstitialAd::class.java))

        val prebidListener = capturePrebidListener()
        val prebidException = AdException(AdException.INTERNAL_ERROR, "prebid error")
        prebidListener.onAdFailed(interstitialConstruction.constructed()[0], prebidException)

        verify(mockListener).onAdLoaded(SdkType.GAM)
        verify(mockListener).onAdFailed(eq(prebidException.message), eq(SdkType.PREBID))
        assertEquals(SdkType.GAM, selectedSdkOf(loader))
    }

    // 9. PREBID fails first, then GAM loads -> GAM selected
    @Test
    fun prebidFailsFirst_gamLoadsAfter_gamSelected() {
        val loader = createLoader()
        loader.loadAd()

        val prebidListener = capturePrebidListener()
        prebidListener.onAdFailed(
            interstitialConstruction.constructed()[0],
            AdException(AdException.INTERNAL_ERROR, "prebid error")
        )

        val gamCallback = captureGamCallback()
        gamCallback.onAdLoaded(mock(InterstitialAd::class.java))

        verify(mockListener).onAdLoaded(SdkType.GAM)
        verify(interstitialConstruction.constructed()[0]).destroy()
        assertEquals(SdkType.GAM, selectedSdkOf(loader))
    }

    // 10. Both SDKs fail -> onAdLoaded never fires, onAdFailed fires for each
    @Test
    fun bothSdksFail_noOnAdLoadedEverCalled() {
        val loader = createLoader()
        loader.loadAd()

        val prebidListener = capturePrebidListener()
        val prebidException = AdException(AdException.INTERNAL_ERROR, "prebid error")
        prebidListener.onAdFailed(interstitialConstruction.constructed()[0], prebidException)

        val gamLoadAdError = LoadAdError(LoadAdError.ErrorCode.INTERNAL_ERROR, "gam error", null)
        val gamCallback = captureGamCallback()
        gamCallback.onAdFailedToLoad(gamLoadAdError)

        verify(mockListener, never()).onAdLoaded(SdkType.PREBID)
        verify(mockListener, never()).onAdLoaded(SdkType.GAM)
        verify(mockListener).onAdFailed(eq(prebidException.message), eq(SdkType.PREBID))
        verify(mockListener).onAdFailed(eq("gam error"), eq(SdkType.GAM))
        assertNull(selectedSdkOf(loader))
    }

    // 11. showAd() with no selected ad -> onAdFailedToShow fires
    @Test
    fun showAd_noSelectedSdk_callsOnAdFailedToShow() {
        val loader = createLoader()

        loader.showAd()

        verify(mockListener).onAdFailedToShow(eq("No loaded interstitial ad to show"), isNull())
    }

    // 12. showAd() with PREBID selected -> delegates to PrebidAdLoader.show()
    @Test
    fun showAd_prebidSelected_delegatesToPrebidShow() {
        val loader = createLoader()
        loader.loadAd()

        val prebidUnit = interstitialConstruction.constructed()[0]
        val prebidListener = capturePrebidListener()
        prebidListener.onAdLoaded(prebidUnit)

        loader.showAd()

        verify(prebidUnit).show()
    }

    // 13. showAd() with GAM selected -> delegates to GamAdLoader.show() which calls interstitialAd.show(activity)
    @Test
    fun showAd_gamSelected_delegatesToGamShowWithActivity() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM, SdkType.PREBID)
        val loader = createLoader()
        loader.loadAd()

        val gamAd = mock(InterstitialAd::class.java)
        val gamCallback = captureGamCallback()
        gamCallback.onAdLoaded(gamAd)

        loader.showAd()

        verify(gamAd).show(anyNonNull(Activity::class.java))
    }

    // 14. destroy() resets all states to NOT_STARTED and destroys inner loaders
    @Test
    fun destroy_resetsAllStatesToNotStarted() {
        val loader = createLoader()
        loader.loadAd()

        val prebidUnit = interstitialConstruction.constructed()[0]

        loader.destroy()

        verify(prebidUnit).destroy()
        val states = sdkStatesOf(loader)
        assertEquals(SdkState.NOT_STARTED, states[SdkType.PREBID])
        assertEquals(SdkState.NOT_STARTED, states[SdkType.GAM])
    }

    // 15. maybeLoadPrebid: custom priority [GAM, PREBID] -> GAM fails -> PREBID gets loaded lazily
    @Test
    fun maybeLoadPrebid_gamFirstAndFails_triggersPrebidLoad() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM, SdkType.PREBID)

        val loader = createLoader()
        loader.loadAd()

        // Initially Prebid is not loaded
        assertEquals(0, interstitialConstruction.constructed().size)

        // GAM fails
        val gamLoadAdError = LoadAdError(LoadAdError.ErrorCode.INTERNAL_ERROR, "gam error", null)
        val gamCallback = captureGamCallback()
        gamCallback.onAdFailedToLoad(gamLoadAdError)

        // Now Prebid should have been constructed lazily via maybeLoadPrebid
        assertEquals(1, interstitialConstruction.constructed().size)
        verify(interstitialConstruction.constructed()[0]).loadAd()
        assertEquals(SdkState.LOADING, sdkStatesOf(loader)[SdkType.PREBID])
    }

    // 16. maybeLoadPrebid: priority without PREBID -> GAM fails -> PREBID never constructed
    @Test
    fun maybeLoadPrebid_prebidNotInPriority_doesNotLoad() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM)

        val loader = createLoader()
        loader.loadAd()

        val gamLoadAdError = LoadAdError(LoadAdError.ErrorCode.INTERNAL_ERROR, "gam error", null)
        val gamCallback = captureGamCallback()
        gamCallback.onAdFailedToLoad(gamLoadAdError)

        assertEquals(0, interstitialConstruction.constructed().size)
    }

    // 17. Prebid passthrough callbacks: onAdDisplayed, onAdClicked, onAdClosed route to listener
    @Test
    fun prebidCallbacks_passThrough_onAdDisplayed_onAdClicked_onAdClosed() {
        val loader = createLoader()
        loader.loadAd()

        val prebidUnit = interstitialConstruction.constructed()[0]
        val prebidListener = capturePrebidListener()

        prebidListener.onAdDisplayed(prebidUnit)
        prebidListener.onAdClicked(prebidUnit)
        prebidListener.onAdClosed(prebidUnit)

        verify(mockListener).onAdDisplayed(SdkType.PREBID)
        verify(mockListener).onAdClicked(SdkType.PREBID)
        verify(mockListener).onAdClosed(SdkType.PREBID)
    }

    // 18. GAM onAdFailedToLoad -> listener receives error with GAM SdkType
    @Test
    fun gamOnAdFailedToLoad_listenerReceivesError() {
        val loader = createLoader()
        loader.loadAd()

        val gamLoadAdError =
            LoadAdError(LoadAdError.ErrorCode.INTERNAL_ERROR, "gam specific error", null)

        val gamCallback = captureGamCallback()
        gamCallback.onAdFailedToLoad(gamLoadAdError)

        verify(mockListener).onAdFailed(eq("gam specific error"), eq(SdkType.GAM))
        assertEquals(SdkState.FAILED, sdkStatesOf(loader)[SdkType.GAM])
    }

    // Bonus: loadAd called twice -> previous resources destroyed first, states reset
    @Test
    fun loadAdCalledTwice_destroysPreviousPrebidInstance() {
        val loader = createLoader()

        loader.loadAd()
        val firstPrebid = interstitialConstruction.constructed()[0]

        loader.loadAd()

        verify(firstPrebid).destroy()
        assertEquals(2, interstitialConstruction.constructed().size)
    }

    // Bonus: setListener replaces a previous one
    @Test
    fun setListener_replacesPreviousListener() {
        val loader = createLoader()
        val newListener = mock(MultiInterstitialAdListenerNextGen::class.java)
        loader.setListener(newListener)

        loader.loadAd()
        val prebidListener = capturePrebidListener()
        prebidListener.onAdLoaded(interstitialConstruction.constructed()[0])

        verify(newListener).onAdLoaded(SdkType.PREBID)
        verifyNoInteractions(mockListener)
    }

    // Bonus: Prebid onAdFailed with null exception message gets "Unknown error"
    @Test
    fun prebidOnAdFailed_nullException_listenerReceivesUnknownError() {
        val loader = createLoader()
        loader.loadAd()

        val prebidListener = capturePrebidListener()
        prebidListener.onAdFailed(interstitialConstruction.constructed()[0], null)

        verify(mockListener).onAdFailed(eq("Unknown error"), eq(SdkType.PREBID))
        assertEquals(SdkState.FAILED, sdkStatesOf(loader)[SdkType.PREBID])
    }
}
