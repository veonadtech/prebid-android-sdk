package org.prebid.mobile.api.multiadloader

import android.app.Activity
import android.content.Context
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.MockedConstruction
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.prebid.mobile.PrebidMobile
import org.prebid.mobile.api.data.SdkType
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.multiadloader.listeners.MultiInterstitialAdListener
import org.prebid.mobile.api.rendering.InterstitialAdUnit
import org.prebid.mobile.api.rendering.listeners.InterstitialAdUnitListener
import org.prebid.mobile.configuration.SdkConfigHolder
import org.prebid.mobile.logging.SdkLogUtil
import org.prebid.mobile.test.utils.WhiteBox
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class MultiInterstitialAdLoaderTest {

    private lateinit var activity: Activity
    private lateinit var listener: MultiInterstitialAdListener

    private lateinit var prebidMobileMS: MockedStatic<PrebidMobile>
    private lateinit var sdkLogUtilMS: MockedStatic<SdkLogUtil>
    private lateinit var gamInterstitialMS: MockedStatic<AdManagerInterstitialAd>

    private lateinit var prebidUnitMC: MockedConstruction<InterstitialAdUnit>
    private lateinit var yandexLoaderMC: MockedConstruction<InterstitialAdLoader>

    private val capturedPrebidListener = AtomicReference<InterstitialAdUnitListener>()
    private val capturedYandexLoadListener = AtomicReference<InterstitialAdLoadListener>()
    private val capturedGamCallbacks = mutableListOf<AdManagerInterstitialAdLoadCallback>()

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).create().get()
        listener = Mockito.mock(MultiInterstitialAdListener::class.java)

        prebidMobileMS = Mockito.mockStatic(PrebidMobile::class.java)
        prebidMobileMS.`when`<Boolean> { PrebidMobile.isSdkInitialized() }.thenReturn(true)

        sdkLogUtilMS = Mockito.mockStatic(SdkLogUtil::class.java)

        gamInterstitialMS = Mockito.mockStatic(AdManagerInterstitialAd::class.java)
        gamInterstitialMS.`when`<Unit> {
            AdManagerInterstitialAd.load(
                any(Context::class.java),
                any(String::class.java),
                any(AdManagerAdRequest::class.java),
                any(AdManagerInterstitialAdLoadCallback::class.java)
            )
        }.thenAnswer { invocation ->
            capturedGamCallbacks.add(invocation.getArgument(3))
            null
        }

        prebidUnitMC = Mockito.mockConstruction(InterstitialAdUnit::class.java) { mock, _ ->
            Mockito.doAnswer { inv ->
                capturedPrebidListener.set(inv.getArgument(0))
                null
            }.`when`(mock).setInterstitialAdUnitListener(any(InterstitialAdUnitListener::class.java))
        }
        yandexLoaderMC = Mockito.mockConstruction(InterstitialAdLoader::class.java) { mock, _ ->
            Mockito.doAnswer { inv ->
                capturedYandexLoadListener.set(inv.getArgument(0))
                null
            }.`when`(mock).setAdLoadListener(any(InterstitialAdLoadListener::class.java))
        }

        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.PREBID, SdkType.GAM, SdkType.YANDEX)
    }

    @After
    fun tearDown() {
        if (::yandexLoaderMC.isInitialized) yandexLoaderMC.close()
        if (::prebidUnitMC.isInitialized) prebidUnitMC.close()
        if (::gamInterstitialMS.isInitialized) gamInterstitialMS.close()
        if (::sdkLogUtilMS.isInitialized) sdkLogUtilMS.close()
        if (::prebidMobileMS.isInitialized) prebidMobileMS.close()
        capturedGamCallbacks.clear()
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.PREBID, SdkType.GAM, SdkType.YANDEX)
    }

    // region helpers

    @Suppress("UNCHECKED_CAST")
    private fun <T> uninit(): T = null as T

    private inline fun <reified T : Any> anyK(): T {
        Mockito.any(T::class.java)
        return uninit()
    }

    private fun <T : Any> eqK(value: T): T {
        Mockito.eq(value)
        return value
    }

    private fun newLoader(
        configId: String? = "config-id",
        gamAdUnitId: String? = "gam/unit",
        yandexAdUnitId: String? = "yandex-unit",
        attachListener: Boolean = true
    ): MultiInterstitialAdLoader {
        val loader = MultiInterstitialAdLoader(activity, configId, gamAdUnitId, yandexAdUnitId)
        if (attachListener) loader.setListener(listener)
        return loader
    }

    private fun fireGamLoaded(idx: Int = 0): AdManagerInterstitialAd {
        val mockAd = Mockito.mock(AdManagerInterstitialAd::class.java)
        capturedGamCallbacks[idx].onAdLoaded(mockAd)
        return mockAd
    }

    private fun fireGamFailed(message: String = "GAM no fill", idx: Int = 0) {
        val err = Mockito.mock(LoadAdError::class.java)
        Mockito.`when`(err.message).thenReturn(message)
        capturedGamCallbacks[idx].onAdFailedToLoad(err)
    }

    /**
     * Fires Yandex's two-step load: first the load listener's onAdLoaded(InterstitialAd), then
     * captures the resulting InterstitialAdEventListener that the loader installs on the ad.
     */
    private fun fireYandexLoaded(): Pair<InterstitialAd, InterstitialAdEventListener> {
        val mockYandexAd = Mockito.mock(InterstitialAd::class.java)
        val captured = AtomicReference<InterstitialAdEventListener>()
        Mockito.doAnswer { inv ->
            captured.set(inv.getArgument(0))
            null
        }.`when`(mockYandexAd).setAdEventListener(any(InterstitialAdEventListener::class.java))

        capturedYandexLoadListener.get().onAdLoaded(mockYandexAd)
        return mockYandexAd to captured.get()
    }

    private fun fireYandexFailed(description: String = "Yandex no fill") {
        val err = Mockito.mock(AdRequestError::class.java)
        Mockito.`when`(err.description).thenReturn(description)
        capturedYandexLoadListener.get().onAdFailedToLoad(err)
    }

    private fun firePrebidLoaded(unit: InterstitialAdUnit = Mockito.mock(InterstitialAdUnit::class.java)) {
        capturedPrebidListener.get().onAdLoaded(unit)
    }

    private fun firePrebidFailed(message: String = "no fill") {
        val ex = AdException(AdException.THIRD_PARTY, message)
        capturedPrebidListener.get().onAdFailed(null, ex)
    }

    private fun selectedSdk(loader: MultiInterstitialAdLoader): SdkType? =
        WhiteBox.getInternalState(loader, "selectedSDK") as SdkType?

    @Suppress("UNCHECKED_CAST")
    private fun statesOf(loader: MultiInterstitialAdLoader): MutableMap<SdkType, MultiInterstitialAdLoader.SdkState> =
        WhiteBox.getInternalState(loader, "sdkStates") as MutableMap<SdkType, MultiInterstitialAdLoader.SdkState>

    @Suppress("UNCHECKED_CAST")
    private fun priorityOrderOf(loader: MultiInterstitialAdLoader): MutableList<SdkType> =
        WhiteBox.getInternalState(loader, "priorityOrder") as MutableList<SdkType>

    // endregion

    // region init & state

    @Test
    fun `init populates sdkStates with NOT_STARTED for all SdkType values`() {
        val loader = newLoader(attachListener = false)

        val states = statesOf(loader)
        assertEquals(SdkType.entries.size, states.size)
        SdkType.entries.forEach { sdk ->
            assertEquals(MultiInterstitialAdLoader.SdkState.NOT_STARTED, states[sdk])
        }
    }

    @Test
    fun `priorityOrder is a defensive copy`() {
        val loader = newLoader(attachListener = false)

        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM)

        assertEquals(listOf(SdkType.PREBID, SdkType.GAM, SdkType.YANDEX), priorityOrderOf(loader).toList())
    }

    // endregion

    // region loadAd ordering

    @Test
    fun `loadAd starts non-Prebid SDKs and Prebid because Prebid is first priority by default`() {
        val loader = newLoader()

        loader.loadAd()

        // Default priority is [PREBID, GAM, YANDEX]: Prebid is first → loaded immediately.
        assertEquals(1, prebidUnitMC.constructed().size)
        // GAM and Yandex also get triggered (they're in priorityOrder, just not Prebid).
        assertEquals(1, capturedGamCallbacks.size)
        assertEquals(1, yandexLoaderMC.constructed().size)
    }

    @Test
    fun `loadAd does NOT start Prebid when Prebid is not the first priority`() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM, SdkType.YANDEX, SdkType.PREBID)
        val loader = newLoader()

        loader.loadAd()

        assertTrue("Prebid must not be constructed yet", prebidUnitMC.constructed().isEmpty())
        assertEquals(1, capturedGamCallbacks.size)
        assertEquals(1, yandexLoaderMC.constructed().size)
    }

    @Test
    fun `loadAd resets selectedSDK and all states to NOT_STARTED before loading`() {
        val loader = newLoader()
        WhiteBox.setInternalState(loader, "selectedSDK", SdkType.GAM)
        statesOf(loader)[SdkType.GAM] = MultiInterstitialAdLoader.SdkState.LOADED

        loader.loadAd()

        assertNull(selectedSdk(loader))
        // Prebid (first priority) is set to LOADING; GAM/YANDEX too via the upfront pass.
        val states = statesOf(loader)
        assertEquals(MultiInterstitialAdLoader.SdkState.LOADING, states[SdkType.PREBID])
        assertEquals(MultiInterstitialAdLoader.SdkState.LOADING, states[SdkType.GAM])
        assertEquals(MultiInterstitialAdLoader.SdkState.LOADING, states[SdkType.YANDEX])
    }

    @Test
    fun `loadAd called twice destroys previous and constructs new`() {
        val loader = newLoader()

        loader.loadAd()
        val firstPrebid = prebidUnitMC.constructed()[0]

        loader.loadAd()

        verify(firstPrebid).destroy()
        assertEquals(2, prebidUnitMC.constructed().size)
        assertEquals(2, yandexLoaderMC.constructed().size)
    }

    // endregion

    // region maybeLoadPrebid (lazy Prebid)

    @Test
    fun `Prebid loads only after all higher priority SDKs have FAILED`() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM, SdkType.YANDEX, SdkType.PREBID)
        val loader = newLoader()

        loader.loadAd()
        assertTrue(prebidUnitMC.constructed().isEmpty())

        fireGamFailed()
        assertTrue("Prebid still NOT_STARTED until all higher fail", prebidUnitMC.constructed().isEmpty())

        fireYandexFailed()
        assertEquals("Now Prebid loads", 1, prebidUnitMC.constructed().size)
    }

    @Test
    fun `Prebid does not load if any higher priority SDK is still LOADING`() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM, SdkType.YANDEX, SdkType.PREBID)
        val loader = newLoader()

        loader.loadAd()
        fireGamFailed()
        // Yandex still LOADING → Prebid must not load.
        assertTrue(prebidUnitMC.constructed().isEmpty())
    }

    @Test
    fun `maybeLoadPrebid is a no-op when sdk is PREBID`() {
        // Default priority [PREBID, GAM, YANDEX] → Prebid loads immediately.
        val loader = newLoader()
        loader.loadAd()

        // Prebid loaded once (from loadAd). Now fire Prebid failed via the listener.
        firePrebidFailed("boom")

        // Should not retry Prebid construction because sdk == PREBID short-circuits.
        assertEquals(1, prebidUnitMC.constructed().size)
    }

    @Test
    fun `maybeLoadPrebid is a no-op when PREBID is not in priorityOrder`() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM, SdkType.YANDEX)
        val loader = newLoader()

        loader.loadAd()
        fireGamFailed()
        fireYandexFailed()

        assertTrue("Prebid must never be constructed", prebidUnitMC.constructed().isEmpty())
    }

    // endregion

    // region selectSdkIfFirstPriority

    @Test
    fun `selection only fires onAdLoaded when first item of priorityOrder is the LOADED one`() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM, SdkType.YANDEX, SdkType.PREBID)
        val loader = newLoader()

        loader.loadAd()
        fireYandexLoaded() // Yandex is not first; selection should NOT fire.

        verify(listener, never()).onAdLoaded(anyK<SdkType>())
        assertNull(selectedSdk(loader))
    }

    @Test
    fun `selection fires when first priority loads`() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM, SdkType.YANDEX, SdkType.PREBID)
        val loader = newLoader()

        loader.loadAd()
        fireGamLoaded()

        verify(listener).onAdLoaded(SdkType.GAM)
        assertEquals(SdkType.GAM, selectedSdk(loader))
    }

    @Test
    fun `if first priority fails, the new first priority becomes the candidate`() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM, SdkType.YANDEX, SdkType.PREBID)
        val loader = newLoader()

        loader.loadAd()
        fireGamFailed()        // priority becomes [YANDEX, PREBID]
        val (_, _) = fireYandexLoaded()

        verify(listener).onAdLoaded(SdkType.YANDEX)
        assertEquals(SdkType.YANDEX, selectedSdk(loader))
    }

    @Test
    fun `cancelOtherRequests resets state for non-winner SDKs after a winner is selected`() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM, SdkType.YANDEX, SdkType.PREBID)
        val loader = newLoader()

        loader.loadAd()
        fireGamLoaded()

        val states = statesOf(loader)
        assertEquals(MultiInterstitialAdLoader.SdkState.NOT_STARTED, states[SdkType.YANDEX])
        assertEquals(MultiInterstitialAdLoader.SdkState.NOT_STARTED, states[SdkType.PREBID])
        assertEquals(SdkType.GAM, selectedSdk(loader))
    }

    // endregion

    // region handleAdFailed ordering — opposite of MultiBannerLoader

    @Test
    fun `handleAdFailed invokes selectSdkIfFirstPriority BEFORE listener_onAdFailed`() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM, SdkType.YANDEX, SdkType.PREBID)
        val loader = newLoader()

        loader.loadAd()

        // After GAM fails: priority = [YANDEX, PREBID]; Yandex still LOADING → maybeLoadPrebid
        // checks if all higher-than-Prebid (just [YANDEX]) have failed — false → no Prebid yet.
        // Then listener.onAdFailed fires.
        val inOrder = Mockito.inOrder(listener)
        fireGamFailed("gam err")
        // Verify Prebid was NOT constructed (selectSdkIfFirstPriority ran but Prebid stayed NOT_STARTED).
        assertTrue(prebidUnitMC.constructed().isEmpty())
        inOrder.verify(listener).onAdFailed("gam err", SdkType.GAM)
    }

    // endregion

    // region inner-loader guards

    @Test
    fun `Prebid load fails when SDK is not initialized`() {
        prebidMobileMS.`when`<Boolean> { PrebidMobile.isSdkInitialized() }.thenReturn(false)
        val loader = newLoader()

        loader.loadAd()

        verify(listener).onAdFailed("Prebid SDK is not initialized!", SdkType.PREBID)
        assertTrue(prebidUnitMC.constructed().isEmpty())
        assertFalse(priorityOrderOf(loader).contains(SdkType.PREBID))
    }

    @Test
    fun `Prebid load fails when configId is null`() {
        val loader = newLoader(configId = null)

        loader.loadAd()

        verify(listener).onAdFailed("ConfigId is empty", SdkType.PREBID)
        assertTrue(prebidUnitMC.constructed().isEmpty())
    }

    @Test
    fun `GAM load fails when gamAdUnitId is null`() {
        val loader = newLoader(gamAdUnitId = null)

        loader.loadAd()

        verify(listener).onAdFailed("GAM AdUnitId is empty", SdkType.GAM)
        assertTrue(capturedGamCallbacks.isEmpty())
    }

    @Test
    fun `Yandex load fails when yandexAdUnitId is null`() {
        val loader = newLoader(yandexAdUnitId = null)

        loader.loadAd()

        verify(listener).onAdFailed("Yandex AdUnitId is empty", SdkType.YANDEX)
        assertTrue(yandexLoaderMC.constructed().isEmpty())
    }

    // endregion

    // region showAd

    @Test
    fun `showAd with no selectedSDK fires onAdFailedToShow`() {
        val loader = newLoader()
        loader.loadAd()

        loader.showAd()

        verify(listener).onAdFailedToShow("No loaded interstitial ad to show", null)
    }

    @Test
    fun `showAd with selectedSDK PREBID delegates to InterstitialAdUnit_show`() {
        val loader = newLoader()
        loader.loadAd()
        firePrebidLoaded() // selects PREBID

        loader.showAd()

        verify(prebidUnitMC.constructed()[0]).show()
    }

    @Test
    fun `showAd with selectedSDK GAM delegates to AdManagerInterstitialAd_show(activity)`() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM, SdkType.YANDEX, SdkType.PREBID)
        val loader = newLoader()
        loader.loadAd()
        val gamAd = fireGamLoaded()

        loader.showAd()

        verify(gamAd).show(activity)
    }

    @Test
    fun `showAd with selectedSDK YANDEX delegates to InterstitialAd_show(activity)`() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM, SdkType.YANDEX, SdkType.PREBID)
        val loader = newLoader()
        loader.loadAd()
        fireGamFailed()                 // priority [YANDEX, PREBID]
        val (yandexAd, _) = fireYandexLoaded()

        loader.showAd()

        verify(yandexAd).show(activity)
    }

    // endregion

    // region Yandex event listener routing

    @Test
    fun `Yandex onAdShown propagates as listener_onAdDisplayed`() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM, SdkType.YANDEX, SdkType.PREBID)
        val loader = newLoader()
        loader.loadAd()
        fireGamFailed()
        val (_, eventListener) = fireYandexLoaded()

        eventListener.onAdShown()

        verify(listener).onAdDisplayed(SdkType.YANDEX)
    }

    @Test
    fun `Yandex other events propagate (clicked, dismissed, failedToShow, impression)`() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM, SdkType.YANDEX, SdkType.PREBID)
        val loader = newLoader()
        loader.loadAd()
        fireGamFailed()
        val (_, eventListener) = fireYandexLoaded()

        val adError = Mockito.mock(AdError::class.java)
        Mockito.`when`(adError.description).thenReturn("show err")
        val impressionData = Mockito.mock(ImpressionData::class.java)

        eventListener.onAdClicked()
        eventListener.onAdDismissed()
        eventListener.onAdFailedToShow(adError)
        eventListener.onAdImpression(impressionData)

        verify(listener).onAdClicked(SdkType.YANDEX)
        verify(listener).onAdClosed(SdkType.YANDEX)
        verify(listener).onAdFailedToShow("show err", SdkType.YANDEX)
        verify(listener).onImpression(impressionData, SdkType.YANDEX)
    }

    // endregion

    // region Prebid listener routing

    @Test
    fun `Prebid listener events propagate (displayed, clicked, closed)`() {
        val loader = newLoader()
        loader.loadAd()

        capturedPrebidListener.get().onAdDisplayed(Mockito.mock(InterstitialAdUnit::class.java))
        capturedPrebidListener.get().onAdClicked(Mockito.mock(InterstitialAdUnit::class.java))
        capturedPrebidListener.get().onAdClosed(Mockito.mock(InterstitialAdUnit::class.java))

        verify(listener).onAdDisplayed(SdkType.PREBID)
        verify(listener).onAdClicked(SdkType.PREBID)
        verify(listener).onAdClosed(SdkType.PREBID)
    }

    @Test
    fun `Prebid onAdFailed routes through handleAdFailed with prefixed AdException message`() {
        val loader = newLoader()
        loader.loadAd()
        val ex = AdException(AdException.THIRD_PARTY, "boom")

        capturedPrebidListener.get().onAdFailed(null, ex)

        verify(listener).onAdFailed(ex.message, SdkType.PREBID)
        assertFalse(priorityOrderOf(loader).contains(SdkType.PREBID))
    }

    // endregion

    // region GAM event listener routing

    @Test
    fun `GAM onAdFailedToLoad routes through handleAdFailed`() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM, SdkType.YANDEX, SdkType.PREBID)
        val loader = newLoader()
        loader.loadAd()

        fireGamFailed("gam err")

        verify(listener).onAdFailed("gam err", SdkType.GAM)
        assertFalse(priorityOrderOf(loader).contains(SdkType.GAM))
    }

    // endregion

    // region destroy lifecycle

    @Test
    fun `destroy destroys all inner interstitials and resets sdkStates`() {
        val loader = newLoader()
        loader.loadAd()
        val prebidMock = prebidUnitMC.constructed()[0]

        loader.destroy()

        verify(prebidMock).destroy()
        val states = statesOf(loader)
        SdkType.entries.forEach { sdk ->
            assertEquals(MultiInterstitialAdLoader.SdkState.NOT_STARTED, states[sdk])
        }
    }

    @Test
    fun `destroy before loadAd does not throw`() {
        val loader = newLoader()
        loader.destroy()
    }

    // endregion

    // region listener null safety

    @Test
    fun `failure path with no listener attached does not throw`() {
        val loader = newLoader(configId = null, attachListener = false)
        loader.loadAd()
    }

    // endregion
}
