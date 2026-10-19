package org.prebid.mobile.api.multiloadernextgen

import android.app.Activity
import android.content.Context
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.yandex.mobile.ads.common.AdError
import com.yandex.mobile.ads.common.AdRequest as YandexAdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
import org.prebid.mobile.api.multiloadernextgen.MultiInterstitialAdLoaderNextGenGam.SdkState
import org.prebid.mobile.api.multiloadernextgen.listeners.MultiInterstitialAdListenerNextGen
import org.prebid.mobile.api.rendering.InterstitialAdUnit
import org.prebid.mobile.api.rendering.listeners.InterstitialAdUnitListener
import org.prebid.mobile.configuration.SdkConfigHolder
import org.prebid.mobile.test.utils.WhiteBox
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicReference
import com.yandex.mobile.ads.interstitial.InterstitialAd as YandexInterstitialAd

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class MultiInterstitialAdLoaderNextGenGamTest {

    @Mock
    private lateinit var mockListener: MultiInterstitialAdListenerNextGen

    private lateinit var context: Context

    private lateinit var interstitialConstruction: MockedConstruction<InterstitialAdUnit>
    private lateinit var yandexLoaderMC: MockedConstruction<InterstitialAdLoader>
    private lateinit var mockedPrebidMobile: MockedStatic<PrebidMobile>

    // Injected GAM-load seam capture (the Next-Gen SDK's InterstitialAd.load is a Kotlin
    // companion method and cannot be intercepted by Mockito's mockStatic).
    private var gamLoadCount = 0
    private var capturedGamRequest: AdRequest? = null
    private var capturedGamCallback: AdLoadCallback<InterstitialAd>? = null

    private val capturedYandexLoadListener = AtomicReference<InterstitialAdLoadListener>()

    private val configId = "test-config-id"
    private val gamAdUnitId = "/1234/test-gam-ad-unit"
    private val yandexAdUnitId = "demo-interstitial-yandex"

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        context = Robolectric.buildActivity(Activity::class.java).create().get()
        interstitialConstruction = Mockito.mockConstruction(InterstitialAdUnit::class.java)
        yandexLoaderMC = Mockito.mockConstruction(InterstitialAdLoader::class.java) { mock, _ ->
            Mockito.doAnswer { inv ->
                capturedYandexLoadListener.set(inv.getArgument(1))
                null
            }.`when`(mock).loadAd(
                anyNonNull(YandexAdRequest::class.java),
                anyNonNull(InterstitialAdLoadListener::class.java)
            )
        }

        gamLoadCount = 0
        capturedGamRequest = null
        capturedGamCallback = null

        mockedPrebidMobile = Mockito.mockStatic(PrebidMobile::class.java)
        mockedPrebidMobile.`when`<Boolean> { PrebidMobile.isSdkInitialized() }.thenReturn(true)

        // Default: race PREBID vs GAM. Yandex tests set their own priority.
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.PREBID, SdkType.GAM)
    }

    @After
    fun tearDown() {
        yandexLoaderMC.close()
        interstitialConstruction.close()
        mockedPrebidMobile.close()
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.PREBID, SdkType.GAM, SdkType.YANDEX)
    }

    private fun createLoader(
        cfg: String? = configId,
        gamId: String? = gamAdUnitId,
        yandexId: String? = yandexAdUnitId,
    ): MultiInterstitialAdLoaderNextGenGam {
        val loader = MultiInterstitialAdLoaderNextGenGam(context, cfg, gamId, yandexId) { request, callback ->
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

    private fun gamError(message: String = "gam error") =
        LoadAdError(LoadAdError.ErrorCode.INTERNAL_ERROR, message, null)

    /**
     * Fires Yandex's load callback. The event listener is installed on the ad only in show(),
     * so it is exposed lazily via the returned reference.
     */
    private fun fireYandexLoaded(): Pair<YandexInterstitialAd, AtomicReference<InterstitialAdEventListener>> {
        val mockYandexAd = mock(YandexInterstitialAd::class.java)
        val captured = AtomicReference<InterstitialAdEventListener>()
        Mockito.doAnswer { inv ->
            captured.set(inv.getArgument(0))
            null
        }.`when`(mockYandexAd).setAdEventListener(any(InterstitialAdEventListener::class.java))

        capturedYandexLoadListener.get().onAdLoaded(mockYandexAd)
        return mockYandexAd to captured
    }

    private fun fireYandexFailed(description: String = "Yandex no fill") {
        val err = mock(AdRequestError::class.java)
        Mockito.`when`(err.description).thenReturn(description)
        capturedYandexLoadListener.get().onAdFailedToLoad(err)
    }

    @Suppress("UNCHECKED_CAST")
    private fun sdkStatesOf(loader: MultiInterstitialAdLoaderNextGenGam): Map<SdkType, SdkState> =
        WhiteBox.getInternalState(loader, "sdkStates") as Map<SdkType, SdkState>

    private fun selectedSdkOf(loader: MultiInterstitialAdLoaderNextGenGam): SdkType? =
        WhiteBox.getInternalState(loader, "selectedSDK") as SdkType?

    @Suppress("UNCHECKED_CAST")
    private fun priorityOrderOf(loader: MultiInterstitialAdLoaderNextGenGam): MutableList<SdkType> =
        WhiteBox.getInternalState(loader, "priorityOrder") as MutableList<SdkType>

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

        assertEquals(1, gamLoadCount)
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

        val gamCallback = captureGamCallback()
        gamCallback.onAdFailedToLoad(gamError())

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

        assertEquals(0, interstitialConstruction.constructed().size)

        val gamCallback = captureGamCallback()
        gamCallback.onAdFailedToLoad(gamError())

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

        val gamCallback = captureGamCallback()
        gamCallback.onAdFailedToLoad(gamError())

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

        val gamCallback = captureGamCallback()
        gamCallback.onAdFailedToLoad(gamError("gam specific error"))

        verify(mockListener).onAdFailed(eq("gam specific error"), eq(SdkType.GAM))
        assertEquals(SdkState.FAILED, sdkStatesOf(loader)[SdkType.GAM])
        assertFalse(priorityOrderOf(loader).contains(SdkType.GAM))
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

    // region loadAd rebuilds priorityOrder

    @Test
    fun loadAd_afterFailure_retriesPreviouslyFailedSdk() {
        val loader = createLoader()
        loader.loadAd()
        capturePrebidListener().onAdFailed(
            interstitialConstruction.constructed()[0],
            AdException(AdException.INTERNAL_ERROR, "prebid error")
        )
        assertFalse(priorityOrderOf(loader).contains(SdkType.PREBID))

        loader.loadAd()

        assertEquals(2, interstitialConstruction.constructed().size)
        assertEquals(2, gamLoadCount)
        assertTrue(priorityOrderOf(loader).contains(SdkType.PREBID))
    }

    @Test
    fun loadAd_afterAllSdksFailed_restoresFullOrderAndKeepsPrebidLazy() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM, SdkType.PREBID)
        val loader = createLoader()
        loader.loadAd()
        captureGamCallback(1).onAdFailedToLoad(gamError())            // triggers lazy Prebid
        assertEquals(1, interstitialConstruction.constructed().size)
        capturePrebidListener().onAdFailed(
            interstitialConstruction.constructed()[0],
            AdException(AdException.INTERNAL_ERROR, "prebid error")
        )
        assertTrue(priorityOrderOf(loader).isEmpty())

        loader.loadAd()

        assertEquals(listOf(SdkType.GAM, SdkType.PREBID), priorityOrderOf(loader).toList())
        assertEquals(2, gamLoadCount)
        // Prebid is last priority, so it must stay lazy again
        assertEquals(1, interstitialConstruction.constructed().size)
    }

    @Test
    fun loadAd_afterWinnerSelected_resetsSelectionAndStates() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM, SdkType.PREBID)
        val loader = createLoader()
        loader.loadAd()
        captureGamCallback(1).onAdLoaded(mock(InterstitialAd::class.java))
        assertEquals(SdkType.GAM, selectedSdkOf(loader))

        loader.loadAd()

        assertNull(selectedSdkOf(loader))
        assertEquals(SdkState.LOADING, sdkStatesOf(loader)[SdkType.GAM])
        assertEquals(2, gamLoadCount)
    }

    // Replaces the old "snapshot" semantics: loadAd() now reads the actual config.
    @Test
    fun loadAd_picksUpChangedPriorityOrder() {
        val loader = createLoader()
        loader.loadAd()

        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM)
        loader.loadAd()

        assertEquals(listOf(SdkType.GAM), priorityOrderOf(loader).toList())
    }

    // endregion

    // region late callbacks from cancelled SDKs (needs the selectedSDK guard in selectSdkIfFirstPriority)

    @Test
    fun lateGamLoadedAfterPrebidWon_doesNotNotifyListenerAgain() {
        val loader = createLoader()
        loader.loadAd()
        val gamCallback = captureGamCallback()

        capturePrebidListener().onAdLoaded(interstitialConstruction.constructed()[0])
        verify(mockListener, times(1)).onAdLoaded(SdkType.PREBID)

        gamCallback.onAdLoaded(mock(InterstitialAd::class.java))

        verify(mockListener, times(1)).onAdLoaded(SdkType.PREBID)
        assertEquals(SdkType.PREBID, selectedSdkOf(loader))
    }

    @Test
    fun lateGamFailureAfterPrebidWon_doesNotNotifyListenerAgain() {
        val loader = createLoader()
        loader.loadAd()
        val gamCallback = captureGamCallback()

        capturePrebidListener().onAdLoaded(interstitialConstruction.constructed()[0])

        gamCallback.onAdFailedToLoad(gamError())

        verify(mockListener, times(1)).onAdLoaded(SdkType.PREBID)
        assertEquals(SdkType.PREBID, selectedSdkOf(loader))
    }

    // endregion

    // region Yandex (SDK 8.6.0 API)

    @Test
    fun yandex_isRequestedViaLoadAdRequestAndListener() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.YANDEX)
        val loader = createLoader()

        loader.loadAd()

        assertEquals(1, yandexLoaderMC.constructed().size)
        verify(yandexLoaderMC.constructed()[0]).loadAd(
            anyNonNull(YandexAdRequest::class.java),
            anyNonNull(InterstitialAdLoadListener::class.java)
        )
    }

    @Test
    fun yandex_adUnitIdNull_failsWithoutConstructingLoader() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.YANDEX)
        val loader = createLoader(yandexId = null)

        loader.loadAd()

        verify(mockListener).onAdFailed(eq("Yandex AdUnitId is empty"), eq(SdkType.YANDEX))
        assertTrue(yandexLoaderMC.constructed().isEmpty())
    }

    @Test
    fun yandex_loaded_isSelected_andShowInstallsEventListenerAndShowsAd() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.YANDEX)
        val loader = createLoader()
        loader.loadAd()

        val (yandexAd, _) = fireYandexLoaded()
        verify(mockListener).onAdLoaded(SdkType.YANDEX)

        loader.showAd()

        verify(yandexAd).setAdEventListener(any(InterstitialAdEventListener::class.java))
        verify(yandexAd).show(context as Activity)
    }

    @Test
    fun yandex_failedToLoad_routesThroughHandleAdFailed() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.YANDEX, SdkType.GAM)
        val loader = createLoader()
        loader.loadAd()

        fireYandexFailed("Yandex no fill")

        verify(mockListener).onAdFailed(eq("Yandex no fill"), eq(SdkType.YANDEX))
        assertFalse(priorityOrderOf(loader).contains(SdkType.YANDEX))
    }

    @Test
    fun yandex_eventListenerEvents_propagateToListener() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.YANDEX)
        val loader = createLoader()
        loader.loadAd()
        val (_, eventListenerRef) = fireYandexLoaded()
        loader.showAd()
        val eventListener = eventListenerRef.get()

        val adError = mock(AdError::class.java)
        Mockito.`when`(adError.description).thenReturn("show err")
        val impressionData = mock(ImpressionData::class.java)

        eventListener.onAdShown()
        eventListener.onAdClicked()
        eventListener.onAdImpression(impressionData)
        eventListener.onAdDismissed()
        eventListener.onAdFailedToShow(adError)

        verify(mockListener).onAdDisplayed(SdkType.YANDEX)
        verify(mockListener).onAdClicked(SdkType.YANDEX)
        verify(mockListener).onImpression(impressionData, SdkType.YANDEX)
        verify(mockListener).onAdClosed(SdkType.YANDEX)
        verify(mockListener).onAdFailedToShow("show err", SdkType.YANDEX)
    }

    // endregion
}