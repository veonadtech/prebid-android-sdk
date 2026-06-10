package org.prebid.mobile.api.multiadloader

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerInterstitialAd
import com.google.android.gms.ads.admanager.AdManagerInterstitialAdLoadCallback
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
import org.prebid.mobile.PrebidMobile
import org.prebid.mobile.api.data.SdkType
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.multiloadercommon.BaseMultiInterstitialAdLoader.SdkState
import org.prebid.mobile.api.multiloadercommon.MultiInterstitialAdListener
import org.prebid.mobile.api.rendering.InterstitialAdUnit
import org.prebid.mobile.api.rendering.listeners.InterstitialAdUnitListener
import org.prebid.mobile.configuration.SdkConfigHolder
import org.prebid.mobile.test.utils.WhiteBox
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class MultiInterstitialAdLoaderTest {

    @Mock
    private lateinit var mockListener: MultiInterstitialAdListener

    private lateinit var context: Context

    private lateinit var interstitialConstruction: MockedConstruction<InterstitialAdUnit>
    private lateinit var gamInterstitialStatic: MockedStatic<AdManagerInterstitialAd>
    private lateinit var mockedPrebidMobile: MockedStatic<PrebidMobile>

    private val configId = "test-config-id"
    private val gamAdUnitId = "/1234/test-gam-ad-unit"

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        context = Robolectric.buildActivity(Activity::class.java).create().get()
        interstitialConstruction = Mockito.mockConstruction(InterstitialAdUnit::class.java)
        gamInterstitialStatic = Mockito.mockStatic(AdManagerInterstitialAd::class.java)

        mockedPrebidMobile = Mockito.mockStatic(PrebidMobile::class.java)
        mockedPrebidMobile.`when`<Boolean> { PrebidMobile.isSdkInitialized() }.thenReturn(true)

        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.PREBID, SdkType.GAM)
    }

    @After
    fun tearDown() {
        interstitialConstruction.close()
        gamInterstitialStatic.close()
        mockedPrebidMobile.close()
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.PREBID, SdkType.GAM)
    }

    private fun createLoader(
        cfg: String? = configId,
        gamId: String? = gamAdUnitId,
    ): MultiInterstitialAdLoader {
        val loader = MultiInterstitialAdLoader(context, cfg, gamId)
        loader.setListener(mockListener)
        return loader
    }

    private fun capturePrebidListener(instanceIndex: Int = 0): InterstitialAdUnitListener {
        val unit = interstitialConstruction.constructed()[instanceIndex]
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

    @Suppress("UNCHECKED_CAST")
    private fun sdkStatesOf(loader: MultiInterstitialAdLoader): Map<SdkType, SdkState> =
        WhiteBox.getInternalState(loader, "sdkStates") as Map<SdkType, SdkState>

    private fun selectedSdkOf(loader: MultiInterstitialAdLoader): SdkType? =
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

    // 2. With default priority PREBID-first, both SDKs are loaded; GAM first then PREBID (per loadAd logic)
    @Test
    fun loadAd_prebidFirstPriority_loadsGamAndPrebid() {
        val loader = createLoader()

        loader.loadAd()

        // GAM static load was called
        gamInterstitialStatic.verify {
            AdManagerInterstitialAd.load(
                any(Context::class.java),
                eq(gamAdUnitId),
                any(AdManagerAdRequest::class.java),
                any(AdManagerInterstitialAdLoadCallback::class.java)
            )
        }
        // PREBID was constructed and loadAd invoked
        assertEquals(1, interstitialConstruction.constructed().size)
        verify(interstitialConstruction.constructed()[0]).loadAd()

        val states = sdkStatesOf(loader)
        assertEquals(SdkState.LOADING, states[SdkType.PREBID])
        assertEquals(SdkState.LOADING, states[SdkType.GAM])
    }

    // 3. Custom priority [GAM, PREBID] → only GAM loads initially, PREBID held back
    @Test
    fun loadAd_gamFirstPriority_prebidNotLoadedInitially() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM, SdkType.PREBID)

        val loader = createLoader()
        loader.loadAd()

        gamInterstitialStatic.verify {
            AdManagerInterstitialAd.load(
                any(Context::class.java),
                eq(gamAdUnitId),
                any(AdManagerAdRequest::class.java),
                any(AdManagerInterstitialAdLoadCallback::class.java)
            )
        }
        assertEquals(0, interstitialConstruction.constructed().size)

        val states = sdkStatesOf(loader)
        assertEquals(SdkState.LOADING, states[SdkType.GAM])
        assertEquals(SdkState.NOT_STARTED, states[SdkType.PREBID])
    }

    // 4. configId=null → PREBID fails immediately
    @Test
    fun loadAd_configIdNull_prebidFailsWithConfigIdIsEmpty() {
        val loader = createLoader(cfg = null)

        loader.loadAd()

        assertEquals(0, interstitialConstruction.constructed().size)
        verify(mockListener).onAdFailed(eq("ConfigId is empty"), eq(SdkType.PREBID))
        assertEquals(SdkState.FAILED, sdkStatesOf(loader)[SdkType.PREBID])
    }

    // 5. gamAdUnitId=null → GAM fails immediately
    @Test
    fun loadAd_gamAdUnitIdNull_gamFailsWithAdUnitIdIsEmpty() {
        val loader = createLoader(gamId = null)

        loader.loadAd()

        // GAM.load was not called (null path returns before static call)
        gamInterstitialStatic.verifyNoInteractions()
        verify(mockListener).onAdFailed(eq("GAM AdUnitId is empty"), eq(SdkType.GAM))
        assertEquals(SdkState.FAILED, sdkStatesOf(loader)[SdkType.GAM])
    }

    // 6. PREBID loads first with default priority → listener notified, GAM gets destroyed and reset
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

    // 7. GAM loads but PREBID is first priority and hasn't responded yet → listener not notified
    @Test
    fun gamLoadedButPrebidFirstPriority_listenerNotNotifiedYet() {
        val loader = createLoader()
        loader.loadAd()

        val gamCallback = captureGamCallback()
        gamCallback.onAdLoaded(mock(AdManagerInterstitialAd::class.java))

        verifyNoInteractions(mockListener)
        val states = sdkStatesOf(loader)
        assertEquals(SdkState.LOADED, states[SdkType.GAM])
        assertEquals(SdkState.LOADING, states[SdkType.PREBID])
        assertNull(selectedSdkOf(loader))
    }

    // 8. GAM loads, then PREBID fails → GAM becomes first priority and is selected
    @Test
    fun gamLoaded_thenPrebidFails_gamBecomesSelected() {
        val loader = createLoader()
        loader.loadAd()

        val gamCallback = captureGamCallback()
        gamCallback.onAdLoaded(mock(AdManagerInterstitialAd::class.java))

        val prebidListener = capturePrebidListener()
        val prebidException = AdException(AdException.INTERNAL_ERROR, "prebid error")
        prebidListener.onAdFailed(interstitialConstruction.constructed()[0], prebidException)

        verify(mockListener).onAdLoaded(SdkType.GAM)
        verify(mockListener).onAdFailed(eq(prebidException.message), eq(SdkType.PREBID))
        assertEquals(SdkType.GAM, selectedSdkOf(loader))
    }

    // 9. PREBID fails first, then GAM loads → GAM selected
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
        gamCallback.onAdLoaded(mock(AdManagerInterstitialAd::class.java))

        verify(mockListener).onAdLoaded(SdkType.GAM)
        verify(interstitialConstruction.constructed()[0]).destroy()
        assertEquals(SdkType.GAM, selectedSdkOf(loader))
    }

    // 10. Both SDKs fail → onAdLoaded never fires, onAdFailed fires for each
    @Test
    fun bothSdksFail_noOnAdLoadedEverCalled() {
        val loader = createLoader()
        loader.loadAd()

        val prebidListener = capturePrebidListener()
        val prebidException = AdException(AdException.INTERNAL_ERROR, "prebid error")
        prebidListener.onAdFailed(interstitialConstruction.constructed()[0], prebidException)

        val gamLoadAdError = mock(LoadAdError::class.java)
        Mockito.`when`(gamLoadAdError.message).thenReturn("gam error")
        val gamCallback = captureGamCallback()
        gamCallback.onAdFailedToLoad(gamLoadAdError)

        verify(mockListener, never()).onAdLoaded(SdkType.PREBID)
        verify(mockListener, never()).onAdLoaded(SdkType.GAM)
        verify(mockListener).onAdFailed(eq(prebidException.message), eq(SdkType.PREBID))
        verify(mockListener).onAdFailed(eq("gam error"), eq(SdkType.GAM))
        assertNull(selectedSdkOf(loader))
    }

    // 11. showAd() with no selected ad → onAdFailedToShow fires
    @Test
    fun showAd_noSelectedSdk_callsOnAdFailedToShow() {
        val loader = createLoader()

        loader.showAd()

        verify(mockListener).onAdFailedToShow(eq("No loaded interstitial ad to show"), isNull())
    }

    // 12. showAd() with PREBID selected → delegates to PrebidAdLoader.show() which calls interstitial.show()
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

    // 13. showAd() with GAM selected → delegates to GamAdLoader.show() which calls interstitialAd.show(activity)
    @Test
    fun showAd_gamSelected_delegatesToGamShowWithActivity() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM, SdkType.PREBID)
        val loader = createLoader()
        loader.loadAd()

        val gamAd = mock(AdManagerInterstitialAd::class.java)
        val gamCallback = captureGamCallback()
        gamCallback.onAdLoaded(gamAd)

        loader.showAd()

        verify(gamAd).show(any(Activity::class.java))
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

    // 15. maybeLoadPrebid: custom priority [GAM, PREBID] → GAM fails → PREBID gets loaded lazily
    @Test
    fun maybeLoadPrebid_gamFirstAndFails_triggersPrebidLoad() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM, SdkType.PREBID)

        val loader = createLoader()
        loader.loadAd()

        // Initially Prebid is not loaded
        assertEquals(0, interstitialConstruction.constructed().size)

        // GAM fails
        val gamLoadAdError = mock(LoadAdError::class.java)
        Mockito.`when`(gamLoadAdError.message).thenReturn("gam error")
        val gamCallback = captureGamCallback()
        gamCallback.onAdFailedToLoad(gamLoadAdError)

        // Now Prebid should have been constructed lazily via maybeLoadPrebid
        assertEquals(1, interstitialConstruction.constructed().size)
        verify(interstitialConstruction.constructed()[0]).loadAd()
        assertEquals(SdkState.LOADING, sdkStatesOf(loader)[SdkType.PREBID])
    }

    // 16. maybeLoadPrebid: priority without PREBID → GAM fails → PREBID never constructed
    @Test
    fun maybeLoadPrebid_prebidNotInPriority_doesNotLoad() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM)

        val loader = createLoader()
        loader.loadAd()

        val gamLoadAdError = mock(LoadAdError::class.java)
        Mockito.`when`(gamLoadAdError.message).thenReturn("gam error")
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

    // 18. GAM onAdFailedToLoad → listener receives error with GAM SdkType
    @Test
    fun gamOnAdFailedToLoad_listenerReceivesError() {
        val loader = createLoader()
        loader.loadAd()

        val gamLoadAdError = mock(LoadAdError::class.java)
        Mockito.`when`(gamLoadAdError.message).thenReturn("gam specific error")

        val gamCallback = captureGamCallback()
        gamCallback.onAdFailedToLoad(gamLoadAdError)

        verify(mockListener).onAdFailed(eq("gam specific error"), eq(SdkType.GAM))
        assertEquals(SdkState.FAILED, sdkStatesOf(loader)[SdkType.GAM])
    }

    // Bonus: loadAd called twice → previous resources destroyed first, states reset
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
        val newListener = mock(MultiInterstitialAdListener::class.java)
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
