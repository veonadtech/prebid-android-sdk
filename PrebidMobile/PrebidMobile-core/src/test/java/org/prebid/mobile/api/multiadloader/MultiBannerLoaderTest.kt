package org.prebid.mobile.api.multiadloader

import android.app.Activity
import android.view.View
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdView
import android.content.Context
import com.yandex.mobile.ads.banner.BannerAdEventListener
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
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
import org.prebid.mobile.AdSize
import org.prebid.mobile.PrebidMobile
import org.prebid.mobile.api.data.SdkType
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.multiadloader.listeners.MultiBannerViewListener
import org.prebid.mobile.api.rendering.BannerView
import org.prebid.mobile.api.rendering.listeners.BannerViewListener
import org.prebid.mobile.configuration.SdkConfigHolder
import org.prebid.mobile.logging.SdkLogUtil
import org.prebid.mobile.test.utils.WhiteBox
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class MultiBannerLoaderTest {

    private lateinit var activity: Activity
    private lateinit var listener: MultiBannerViewListener

    private lateinit var prebidMobileMS: MockedStatic<PrebidMobile>
    private lateinit var sdkLogUtilMS: MockedStatic<SdkLogUtil>

    private lateinit var bannerViewMC: MockedConstruction<BannerView>
    private lateinit var gamViewMC: MockedConstruction<AdManagerAdView>
    private lateinit var yandexViewMC: MockedConstruction<BannerAdView>

    private val capturedPrebidListener = AtomicReference<BannerViewListener>()
    private val capturedGamListener = AtomicReference<AdListener>()
    private val capturedYandexListener = AtomicReference<BannerAdEventListener>()

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).create().get()
        listener = Mockito.mock(MultiBannerViewListener::class.java)

        prebidMobileMS = Mockito.mockStatic(PrebidMobile::class.java)
        prebidMobileMS.`when`<Boolean> { PrebidMobile.isSdkInitialized() }.thenReturn(true)

        sdkLogUtilMS = Mockito.mockStatic(SdkLogUtil::class.java)

        bannerViewMC = Mockito.mockConstruction(BannerView::class.java) { mock, _ ->
            Mockito.doAnswer { inv ->
                capturedPrebidListener.set(inv.getArgument(0))
                null
            }.`when`(mock).setBannerListener(any(BannerViewListener::class.java))
        }
        gamViewMC = Mockito.mockConstruction(AdManagerAdView::class.java) { mock, _ ->
            Mockito.doAnswer { inv ->
                capturedGamListener.set(inv.getArgument(0))
                null
            }.`when`(mock).setAdListener(any(AdListener::class.java))
        }
        yandexViewMC = Mockito.mockConstruction(BannerAdView::class.java) { mock, _ ->
            Mockito.`when`(mock.context).thenReturn(activity)
            Mockito.doAnswer { inv ->
                capturedYandexListener.set(inv.getArgument(0))
                null
            }.`when`(mock).setBannerAdEventListener(any(BannerAdEventListener::class.java))
        }

        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.PREBID, SdkType.GAM, SdkType.YANDEX)
    }

    @After
    fun tearDown() {
        if (::bannerViewMC.isInitialized) bannerViewMC.close()
        if (::gamViewMC.isInitialized) gamViewMC.close()
        if (::yandexViewMC.isInitialized) yandexViewMC.close()
        if (::sdkLogUtilMS.isInitialized) sdkLogUtilMS.close()
        if (::prebidMobileMS.isInitialized) prebidMobileMS.close()
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.PREBID, SdkType.GAM, SdkType.YANDEX)
    }

    // region helpers

    /**
     * Kotlin-friendly Mockito.any() — registers an `any()` matcher and returns a typed null
     * via the standard mockito-kotlin trick so the compile-time null check at the call site
     * (against non-null Kotlin params) is bypassed.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> uninit(): T = null as T

    private inline fun <reified T : Any> anyK(): T {
        Mockito.any(T::class.java)
        return uninit()
    }

    /** Like Mockito.eq() but preserves Kotlin's non-null contract at the call site. */
    private fun <T : Any> eqK(value: T): T {
        Mockito.eq(value)
        return value
    }


    private class TestableMultiBannerLoader(
        context: Context,
        adSize: AdSize?,
        configId: String?,
        gamAdUnitId: String?,
        yandexAdUnitId: String?,
        autoRefreshDelay: Int
    ) : MultiBannerLoader(context, adSize, configId, gamAdUnitId, yandexAdUnitId, autoRefreshDelay) {
        public override fun yandexInlineBannerSize(context: Context, size: AdSize): BannerAdSize =
            Mockito.mock(BannerAdSize::class.java)
    }

    private fun newLoader(
        adSize: AdSize? = AdSize(300, 250),
        configId: String? = "config-id",
        gamAdUnitId: String? = "gam/unit",
        yandexAdUnitId: String? = "yandex-unit",
        autoRefreshDelay: Int = 30,
        attachListener: Boolean = true
    ): MultiBannerLoader {
        val loader = TestableMultiBannerLoader(activity, adSize, configId, gamAdUnitId, yandexAdUnitId, autoRefreshDelay)
        if (attachListener) loader.setListener(listener)
        return loader
    }

    private fun firePrebidLoaded(view: BannerView = Mockito.mock(BannerView::class.java)) {
        capturedPrebidListener.get().onAdLoaded(view)
    }

    private fun fireGamLoaded() {
        capturedGamListener.get().onAdLoaded()
    }

    private fun fireYandexLoaded() {
        capturedYandexListener.get().onAdLoaded()
    }

    private fun selectedSdk(loader: MultiBannerLoader): SdkType? =
        WhiteBox.getInternalState(loader, "selectedSDK") as SdkType?

    @Suppress("UNCHECKED_CAST")
    private fun statesOf(loader: MultiBannerLoader): MutableMap<SdkType, MultiBannerLoader.SdkState> =
        WhiteBox.getInternalState(loader, "sdkStates") as MutableMap<SdkType, MultiBannerLoader.SdkState>

    @Suppress("UNCHECKED_CAST")
    private fun priorityOrderOf(loader: MultiBannerLoader): MutableList<SdkType> =
        WhiteBox.getInternalState(loader, "priorityOrder") as MutableList<SdkType>

    // endregion

    // region init & state

    @Test
    fun `init populates sdkStates with NOT_STARTED for all SdkType values`() {
        val loader = newLoader(attachListener = false)

        val states = statesOf(loader)
        assertEquals(SdkType.entries.size, states.size)
        SdkType.entries.forEach { sdk ->
            assertEquals(MultiBannerLoader.SdkState.NOT_STARTED, states[sdk])
        }
    }

    @Test
    fun `priorityOrder is a defensive copy of SdkConfigHolder priorityOrderSDK`() {
        val loader = newLoader(attachListener = false)

        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM)

        val captured = priorityOrderOf(loader)
        assertEquals(listOf(SdkType.PREBID, SdkType.GAM, SdkType.YANDEX), captured.toList())
    }

    @Test
    fun `setListener attaches listener that receives subsequent failure callbacks`() {
        val loader = newLoader(configId = "", attachListener = false)
        loader.setListener(listener)

        loader.loadAd()

        verify(listener).onAdFailed("ConfigId is empty", SdkType.PREBID)
    }

    // endregion

    // region loadAd

    @Test
    fun `loadAd resets selectedSDK to null`() {
        val loader = newLoader()
        WhiteBox.setInternalState(loader, "selectedSDK", SdkType.GAM)

        loader.loadAd()

        assertNull(selectedSdk(loader))
    }

    @Test
    fun `loadAd transitions every sdkState to LOADING when guards pass`() {
        val loader = newLoader()

        loader.loadAd()

        val states = statesOf(loader)
        // All three SDKs pass their guards (mocked construction); none of them fired callback
        // back yet, so all three remain in LOADING.
        SdkType.entries.forEach { sdk ->
            assertEquals(
                "Expected LOADING for $sdk", MultiBannerLoader.SdkState.LOADING, states[sdk]
            )
        }
    }

    @Test
    fun `loadAd invokes load on every loader in priorityOrder`() {
        val loader = newLoader()

        loader.loadAd()

        assertEquals(1, bannerViewMC.constructed().size)
        assertEquals(1, gamViewMC.constructed().size)
        assertEquals(1, yandexViewMC.constructed().size)
    }

    @Test
    fun `loadAd called twice destroys the previous banners before constructing new ones`() {
        val loader = newLoader()

        loader.loadAd()
        val firstPrebid = bannerViewMC.constructed()[0]
        val firstGam = gamViewMC.constructed()[0]
        val firstYandex = yandexViewMC.constructed()[0]

        loader.loadAd()

        verify(firstPrebid).destroy()
        verify(firstGam).destroy()
        verify(firstYandex).destroy()
        assertEquals(2, bannerViewMC.constructed().size)
        assertEquals(2, gamViewMC.constructed().size)
        assertEquals(2, yandexViewMC.constructed().size)
    }

    @Test
    fun `Prebid loader propagates autoRefreshDelay to BannerView`() {
        val loader = newLoader(autoRefreshDelay = 45)

        loader.loadAd()

        verify(bannerViewMC.constructed()[0]).setAutoRefreshDelay(45)
    }

    // endregion

    // region inner-loader guards

    @Test
    fun `Prebid load fails when PrebidMobile is not initialized`() {
        prebidMobileMS.`when`<Boolean> { PrebidMobile.isSdkInitialized() }.thenReturn(false)
        val loader = newLoader()

        loader.loadAd()

        verify(listener).onAdFailed("Prebid SDK is not initialized!", SdkType.PREBID)
        assertTrue(bannerViewMC.constructed().isEmpty())
        assertFalse(priorityOrderOf(loader).contains(SdkType.PREBID))
    }

    @Test
    fun `Prebid load fails when configId is null`() {
        val loader = newLoader(configId = null)

        loader.loadAd()

        verify(listener).onAdFailed("ConfigId is empty", SdkType.PREBID)
        assertTrue(bannerViewMC.constructed().isEmpty())
    }

    @Test
    fun `Prebid load fails when configId is empty`() {
        val loader = newLoader(configId = "")

        loader.loadAd()

        verify(listener).onAdFailed("ConfigId is empty", SdkType.PREBID)
    }

    @Test
    fun `GAM load fails when gamAdUnitId is null`() {
        val loader = newLoader(gamAdUnitId = null)

        loader.loadAd()

        verify(listener).onAdFailed("GAM AdUnitId is empty", SdkType.GAM)
        assertTrue(gamViewMC.constructed().isEmpty())
    }

    @Test
    fun `GAM load fails when gamAdUnitId is empty`() {
        val loader = newLoader(gamAdUnitId = "")

        loader.loadAd()

        verify(listener).onAdFailed("GAM AdUnitId is empty", SdkType.GAM)
    }

    @Test
    fun `GAM load fails when adSize is null`() {
        val loader = newLoader(adSize = null)

        loader.loadAd()

        verify(listener).onAdFailed("GAM adSize is null", SdkType.GAM)
        assertTrue(gamViewMC.constructed().isEmpty())
    }

    @Test
    fun `Yandex load fails when yandexAdUnitId is null`() {
        val loader = newLoader(yandexAdUnitId = null)

        loader.loadAd()

        verify(listener).onAdFailed("Yandex AdUnitId is empty", SdkType.YANDEX)
        assertTrue(yandexViewMC.constructed().isEmpty())
    }

    @Test
    fun `Yandex load fails when adSize is null`() {
        val loader = newLoader(adSize = null)

        loader.loadAd()

        verify(listener).onAdFailed("Yandex adSize is null", SdkType.YANDEX)
        assertTrue(yandexViewMC.constructed().isEmpty())
    }

    @Test
    fun `failed SDK is removed from priorityOrder`() {
        val loader = newLoader(configId = null) // PREBID guard fails inside load()

        loader.loadAd()

        assertFalse(priorityOrderOf(loader).contains(SdkType.PREBID))
    }

    // endregion

    // region trySelectAd priority logic

    @Test
    fun `if first priority loads, listener_onAdLoaded fires with that SDK and view`() {
        val loader = newLoader()

        loader.loadAd()
        firePrebidLoaded()

        verify(listener).onAdLoaded(anyK<View>(), eqK(SdkType.PREBID))
        assertEquals(SdkType.PREBID, selectedSdk(loader))
    }

    @Test
    fun `if non-first priority loads first, no selection happens`() {
        val loader = newLoader()

        loader.loadAd()
        fireYandexLoaded()

        verify(listener, never()).onAdLoaded(anyK<View>(), anyK<SdkType>())
        assertNull(selectedSdk(loader))
    }

    @Test
    fun `once first priority later loads, selection happens`() {
        val loader = newLoader()

        loader.loadAd()
        fireYandexLoaded() // not selected yet
        fireGamLoaded()    // still not — Prebid is first
        firePrebidLoaded()

        verify(listener, times(1)).onAdLoaded(anyK<View>(), eqK(SdkType.PREBID))
        verify(listener, never()).onAdLoaded(anyK<View>(), eqK(SdkType.YANDEX))
        verify(listener, never()).onAdLoaded(anyK<View>(), eqK(SdkType.GAM))
        assertEquals(SdkType.PREBID, selectedSdk(loader))
    }

    @Test
    fun `if first priority fails, second priority becomes the candidate and is selected if loaded`() {
        val loader = newLoader(configId = "") // PREBID fails immediately via guard

        loader.loadAd()
        fireGamLoaded()

        verify(listener).onAdLoaded(anyK<View>(), eqK(SdkType.GAM))
        assertEquals(SdkType.GAM, selectedSdk(loader))
    }

    @Test
    fun `selection is idempotent — subsequent SDK loaded callback does NOT fire onAdLoaded again`() {
        val loader = newLoader()

        loader.loadAd()
        firePrebidLoaded()
        // Forcibly re-set selectedSDK and try to fire another callback (pretend cancelOther didn't kill the listener).
        WhiteBox.setInternalState(loader, "selectedSDK", SdkType.PREBID)
        capturedGamListener.get().onAdLoaded()

        verify(listener, times(1)).onAdLoaded(anyK<View>(), anyK<SdkType>())
    }

    @Test
    fun `cancelOtherRequests destroys non-winner banners and resets their states to NOT_STARTED`() {
        val loader = newLoader()

        loader.loadAd()
        val gamMock = gamViewMC.constructed()[0]
        val yandexMock = yandexViewMC.constructed()[0]

        firePrebidLoaded()

        verify(gamMock).destroy()
        verify(yandexMock).destroy()
        val states = statesOf(loader)
        assertEquals(MultiBannerLoader.SdkState.NOT_STARTED, states[SdkType.GAM])
        assertEquals(MultiBannerLoader.SdkState.NOT_STARTED, states[SdkType.YANDEX])
    }

    // endregion

    // region single-fire isAdLoaded

    @Test
    fun `Prebid duplicate onAdLoaded is ignored`() {
        val loader = newLoader()

        loader.loadAd()
        firePrebidLoaded()
        firePrebidLoaded()

        verify(listener, times(1)).onAdLoaded(anyK<View>(), eqK(SdkType.PREBID))
    }

    @Test
    fun `GAM duplicate onAdLoaded is ignored`() {
        val loader = newLoader(configId = "") // make GAM the winner

        loader.loadAd()
        fireGamLoaded()
        fireGamLoaded()

        verify(listener, times(1)).onAdLoaded(anyK<View>(), eqK(SdkType.GAM))
    }

    @Test
    fun `Yandex duplicate onAdLoaded is ignored`() {
        // Make Yandex the winner: fail PREBID and GAM via guards.
        val loader = newLoader(configId = "", gamAdUnitId = "")

        loader.loadAd()
        fireYandexLoaded()
        fireYandexLoaded()

        verify(listener, times(1)).onAdLoaded(anyK<View>(), eqK(SdkType.YANDEX))
    }

    // endregion

    // region listener routing

    @Test
    fun `Prebid onAdDisplayed only propagates when selectedSDK == PREBID`() {
        val loader = newLoader()
        loader.loadAd()
        WhiteBox.setInternalState(loader, "selectedSDK", SdkType.GAM)

        capturedPrebidListener.get().onAdDisplayed(Mockito.mock(BannerView::class.java))

        verify(listener, never()).onAdDisplayed(
            anyK<BannerView>(), anyK<SdkType>()
        )

        WhiteBox.setInternalState(loader, "selectedSDK", SdkType.PREBID)
        val v = Mockito.mock(BannerView::class.java)
        capturedPrebidListener.get().onAdDisplayed(v)
        verify(listener).onAdDisplayed(v, SdkType.PREBID)
    }

    @Test
    fun `Prebid onAdClicked and onAdClosed propagate to listener`() {
        val loader = newLoader()
        loader.loadAd()

        val view = Mockito.mock(BannerView::class.java)
        capturedPrebidListener.get().onAdClicked(view)
        capturedPrebidListener.get().onAdClosed(view)

        verify(listener).onAdClicked(view, SdkType.PREBID)
        verify(listener).onAdClosed(view, SdkType.PREBID)
    }

    @Test
    fun `Prebid onAdFailed routes through handleAdFailed and notifies listener with prefixed message`() {
        val loader = newLoader()
        loader.loadAd()
        val ex = AdException(AdException.THIRD_PARTY, "boom")

        capturedPrebidListener.get().onAdFailed(null, ex)

        verify(listener).onAdFailed(ex.message, SdkType.PREBID)
        assertFalse(priorityOrderOf(loader).contains(SdkType.PREBID))
    }

    @Test
    fun `GAM onAdFailedToLoad routes through handleAdFailed`() {
        val loader = newLoader()
        loader.loadAd()

        val err = Mockito.mock(LoadAdError::class.java)
        Mockito.`when`(err.message).thenReturn("GAM no fill")
        capturedGamListener.get().onAdFailedToLoad(err)

        verify(listener).onAdFailed("GAM no fill", SdkType.GAM)
        assertFalse(priorityOrderOf(loader).contains(SdkType.GAM))
    }

    @Test
    fun `GAM listener events propagate (clicked, closed, impression, opened)`() {
        val loader = newLoader()
        loader.loadAd()

        capturedGamListener.get().onAdClicked()
        capturedGamListener.get().onAdClosed()
        capturedGamListener.get().onAdImpression()
        capturedGamListener.get().onAdOpened()

        verify(listener).onAdClicked(null, SdkType.GAM)
        verify(listener).onAdClosed(null, SdkType.GAM)
        verify(listener).onImpression(null, SdkType.GAM)
        verify(listener).onAdOpened(SdkType.GAM)
    }

    @Test
    fun `Yandex onAdFailedToLoad routes through handleAdFailed`() {
        val loader = newLoader()
        loader.loadAd()

        val err = Mockito.mock(AdRequestError::class.java)
        Mockito.`when`(err.description).thenReturn("Yandex no fill")
        capturedYandexListener.get().onAdFailedToLoad(err)

        verify(listener).onAdFailed("Yandex no fill", SdkType.YANDEX)
        assertFalse(priorityOrderOf(loader).contains(SdkType.YANDEX))
    }

    @Test
    fun `Yandex listener events propagate (clicked, leftApp, returnedToApp, impression)`() {
        val loader = newLoader()
        loader.loadAd()
        val impressionData = Mockito.mock(ImpressionData::class.java)

        capturedYandexListener.get().onAdClicked()
        capturedYandexListener.get().onLeftApplication()
        capturedYandexListener.get().onReturnedToApplication()
        capturedYandexListener.get().onImpression(impressionData)

        verify(listener).onAdClicked(null, SdkType.YANDEX)
        verify(listener).onLeftApplication(SdkType.YANDEX)
        verify(listener).onReturnedToApplication(SdkType.YANDEX)
        verify(listener).onImpression(impressionData, SdkType.YANDEX)
    }

    // endregion

    // region destroy lifecycle

    @Test
    fun `destroy destroys every constructed banner and resets all states to NOT_STARTED`() {
        val loader = newLoader()
        loader.loadAd()
        val gamMock = gamViewMC.constructed()[0]
        val prebidMock = bannerViewMC.constructed()[0]
        val yandexMock = yandexViewMC.constructed()[0]

        loader.destroy()

        verify(prebidMock).destroy()
        verify(gamMock).destroy()
        verify(yandexMock).destroy()
        val states = statesOf(loader)
        SdkType.entries.forEach { sdk ->
            assertEquals(MultiBannerLoader.SdkState.NOT_STARTED, states[sdk])
        }
    }

    @Test
    fun `destroy before loadAd does not throw`() {
        val loader = newLoader()
        loader.destroy() // banners are still null
    }

    // endregion

    // region listener null safety

    @Test
    fun `failure path with no listener attached does not throw`() {
        val loader = newLoader(configId = null, attachListener = false)
        loader.loadAd() // would call listener.onAdFailed; should be a no-op
    }

    // endregion
}
