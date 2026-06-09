/*
 *    Copyright 2018-2021 Prebid.org, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.prebid.mobile.api.multiloadernextgen

import android.app.Activity
import android.content.Context
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
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
import org.prebid.mobile.api.rendering.BannerView
import org.prebid.mobile.api.rendering.listeners.BannerViewListener
import org.prebid.mobile.configuration.SdkConfigHolder
import org.prebid.mobile.api.multiloadernextgen.listeners.MultiBannerViewListenerNextGen
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class MultiBannerLoaderNextGenTest {

    @Mock
    private lateinit var mockListener: MultiBannerViewListenerNextGen

    private lateinit var context: Context
    private lateinit var adSize: AdSize

    private lateinit var bannerConstruction: MockedConstruction<BannerView>
    private lateinit var adViewConstruction: MockedConstruction<AdView>

    private val configId = "test-config-id"
    private val gamAdUnitId = "/1234/test-gam-ad-unit"
    private val yandexAdUnitId = "demo-banner-yandex"
    private lateinit var mockedPrebidMobile: MockedStatic<PrebidMobile>

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        context = Robolectric.buildActivity(Activity::class.java).create().get()
        adSize = AdSize(300, 250)

        mockedPrebidMobile = Mockito.mockStatic(PrebidMobile::class.java)
        mockedPrebidMobile.`when`<Boolean> { PrebidMobile.isSdkInitialized() }.thenReturn(true)

        bannerConstruction = Mockito.mockConstruction(BannerView::class.java)
        adViewConstruction = Mockito.mockConstruction(AdView::class.java)
        // Race only PREBID vs GAM; YANDEX uses its own SDK and is exercised in instrumentation/demo.
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.PREBID, SdkType.GAM)
    }

    @After
    fun tearDown() {
        bannerConstruction.close()
        adViewConstruction.close()
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.PREBID, SdkType.GAM, SdkType.YANDEX)
        mockedPrebidMobile.close()
    }

    private fun createLoader(
        size: AdSize? = adSize,
        cfg: String? = configId,
        gamId: String? = gamAdUnitId,
        yandexId: String? = yandexAdUnitId,
    ): MultiBannerLoaderNextGen {
        val loader = MultiBannerLoaderNextGen(context, size, cfg, gamId, yandexId)
        loader.setListener(mockListener)
        return loader
    }

    private fun capturePrebidListener(instanceIndex: Int = 0): BannerViewListener {
        val banner = bannerConstruction.constructed()[instanceIndex]
        val captor = ArgumentCaptor.forClass(BannerViewListener::class.java)
        verify(banner).setBannerListener(captor.capture())
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

    // Unbounded-generic identity cast: erases to no-op, so it can carry a null
    // (e.g. a captor.capture() recording value) into a non-null SDK parameter slot.
    @Suppress("UNCHECKED_CAST")
    private fun <T> uninitialized(): T = null as T

    @Suppress("UNCHECKED_CAST")
    private fun <T> cast(value: Any?): T = value as T

    private fun captureGamCallback(instanceIndex: Int = 0): AdLoadCallback<BannerAd> {
        val adView = adViewConstruction.constructed()[instanceIndex]
        val captor = ArgumentCaptor.forClass(AdLoadCallback::class.java)
        verify(adView).loadAd(
            anyNonNull(BannerAdRequest::class.java),
            cast(captor.capture())
        )
        return cast(captor.value)
    }

    private fun captureGamEventCallback(bannerAd: BannerAd): BannerAdEventCallback {
        val captor = ArgumentCaptor.forClass(BannerAdEventCallback::class.java)
        verify(bannerAd).adEventCallback = captor.capture()
        return captor.value
    }

    // 1. loadAd builds both inner SDK loaders when priority is default and inputs valid
    @Test
    fun loadAd_prebidFirstPriority_constructsBothSdkLoaders() {
        val loader = createLoader()

        loader.loadAd()

        assertEquals(1, bannerConstruction.constructed().size)
        assertEquals(1, adViewConstruction.constructed().size)

        val banner = bannerConstruction.constructed()[0]
        verify(banner).setBannerListener(any())
        verify(banner).setAutoRefreshDelay(anyInt())
        verify(banner).loadAd()

        val adView = adViewConstruction.constructed()[0]
        verify(adView).loadAd(
            anyNonNull(BannerAdRequest::class.java),
            cast(anyNonNull(AdLoadCallback::class.java))
        )
    }

    // 2. configId=null → Prebid reports failure immediately and never constructs BannerView
    @Test
    fun loadAd_configIdNull_prebidFailsImmediatelyWithoutConstructingBannerView() {
        val loader = createLoader(cfg = null)

        loader.loadAd()

        assertEquals(0, bannerConstruction.constructed().size)
        verify(mockListener).onAdFailed(isNull(), eq("ConfigId is empty"), eq(SdkType.PREBID))
    }

    // 3. gamAdUnitId=null → GAM reports failure immediately and never constructs AdView
    @Test
    fun loadAd_gamAdUnitIdNull_gamFailsImmediatelyWithoutConstructingAdView() {
        val loader = createLoader(gamId = null)

        loader.loadAd()

        assertEquals(0, adViewConstruction.constructed().size)
        verify(mockListener).onAdFailed(isNull(), eq("GAM AdUnitId is empty"), eq(SdkType.GAM))
    }

    // 4. adSize=null with GAM id present → GAM fails with "GAM adSize is null"
    @Test
    fun loadAd_adSizeNull_gamFailsWithAdSizeNullMessage() {
        val loader = createLoader(size = null)

        loader.loadAd()

        assertEquals(0, adViewConstruction.constructed().size)
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
        val adView = adViewConstruction.constructed()[0]
        verify(adView).destroy()
    }

    // 6. GAM loads while Prebid is still priority head → listener NOT notified (GAM held back)
    @Test
    fun gamLoadedButPrebidFirstPriority_listenerNotNotified() {
        val loader = createLoader()
        loader.loadAd()

        val gamCallback = captureGamCallback()
        gamCallback.onAdLoaded(mock(BannerAd::class.java))

        verifyNoInteractions(mockListener)
    }

    // 7. Prebid fails → removed from priority → GAM succeeds afterwards → listener notified with GAM
    @Test
    fun prebidFails_thenGamSucceeds_gamSelectedAfterPriorityShift() {
        val loader = createLoader()
        loader.loadAd()

        val prebidListener = capturePrebidListener()
        prebidListener.onAdFailed(null, AdException(AdException.INTERNAL_ERROR, "prebid error"))

        val adView = adViewConstruction.constructed()[0]
        val gamCallback = captureGamCallback()
        gamCallback.onAdLoaded(mock(BannerAd::class.java))

        verify(mockListener).onAdLoaded(adView, SdkType.GAM)
    }

    // 7b. GAM loads first while PREBID is still priority head (held back), then PREBID fails →
    //     the already-loaded GAM is promoted to head and selected.
    @Test
    fun gamLoadedFirst_thenPrebidFails_gamSelectedAfterPriorityShift() {
        val loader = createLoader()
        loader.loadAd()

        // GAM loads while PREBID is the priority head → held back, no notification yet.
        val adView = adViewConstruction.constructed()[0]
        val gamCallback = captureGamCallback()
        gamCallback.onAdLoaded(mock(BannerAd::class.java))
        verifyNoInteractions(mockListener)

        // PREBID (priority head) fails → GAM should now be selected.
        val prebidListener = capturePrebidListener()
        prebidListener.onAdFailed(null, AdException(AdException.INTERNAL_ERROR, "prebid error"))

        verify(mockListener).onAdLoaded(adView, SdkType.GAM)
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

        val adView = adViewConstruction.constructed()[0]
        val gamCallback = captureGamCallback()
        gamCallback.onAdLoaded(mock(BannerAd::class.java))

        verify(mockListener).onAdLoaded(adView, SdkType.GAM)
        val banner = bannerConstruction.constructed()[0]
        verify(banner).destroy()
    }

    // 10. destroy() after loadAd destroys both inner loaders
    @Test
    fun destroy_destroysBothInnerLoaders() {
        val loader = createLoader()
        loader.loadAd()

        val banner = bannerConstruction.constructed()[0]
        val adView = adViewConstruction.constructed()[0]

        loader.destroy()

        verify(banner).destroy()
        verify(adView).destroy()
    }

    // 11. loadAd called twice destroys previous instances before creating new ones
    @Test
    fun loadAdCalledTwice_destroysPreviousResourcesFirst() {
        val loader = createLoader()

        loader.loadAd()
        val firstBanner = bannerConstruction.constructed()[0]
        val firstAdView = adViewConstruction.constructed()[0]

        loader.loadAd()

        verify(firstBanner).destroy()
        verify(firstAdView).destroy()
        assertEquals(2, bannerConstruction.constructed().size)
        assertEquals(2, adViewConstruction.constructed().size)
    }

    // 12a. Duplicate Prebid onAdLoaded callbacks only notify listener once (isAdLoaded guard)
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

    // 12b. Duplicate GAM onAdLoaded callbacks only notify listener once (isAdLoaded guard)
    @Test
    fun gamOnAdLoaded_calledTwice_listenerNotifiedOnce() {
        SdkConfigHolder.priorityOrderSDK = listOf(SdkType.GAM, SdkType.PREBID)

        val loader = createLoader()
        loader.loadAd()

        val adView = adViewConstruction.constructed()[0]
        val gamCallback = captureGamCallback()

        gamCallback.onAdLoaded(mock(BannerAd::class.java))
        gamCallback.onAdLoaded(mock(BannerAd::class.java))

        verify(mockListener, times(1)).onAdLoaded(adView, SdkType.GAM)
    }

    // 13. GAM passthrough callbacks: onAdClicked, onAdImpression, onAdDismissedFullScreenContent route to listener
    @Test
    fun gamPassthroughCallbacks_routedToMultiBannerViewListener() {
        val loader = createLoader()
        loader.loadAd()

        val gamCallback = captureGamCallback()
        val bannerAd = mock(BannerAd::class.java)
        gamCallback.onAdLoaded(bannerAd)

        val eventCallback = captureGamEventCallback(bannerAd)
        eventCallback.onAdClicked()
        eventCallback.onAdImpression()
        eventCallback.onAdDismissedFullScreenContent()

        verify(mockListener).onAdClicked(null, SdkType.GAM)
        verify(mockListener).onImpression(null, SdkType.GAM)
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

    // Bonus: GAM failure → listener.onAdFailed fires for GAM with the error message
    @Test
    fun gamFails_listenerReceivesGamFailure() {
        val loader = createLoader()
        loader.loadAd()

        val gamCallback = captureGamCallback()
        val loadAdError = LoadAdError(LoadAdError.ErrorCode.INTERNAL_ERROR, "gam error", null)

        gamCallback.onAdFailedToLoad(loadAdError)

        verify(mockListener).onAdFailed(isNull(), eq("gam error"), eq(SdkType.GAM))
    }

    // Bonus: setListener replaces previous listener
    @Test
    fun setListener_replacesPreviousListener() {
        val loader = createLoader()
        val newListener = mock(MultiBannerViewListenerNextGen::class.java)
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
        assertEquals(1, adViewConstruction.constructed().size)
        verify(adViewConstruction.constructed()[0]).destroy()
    }
}
