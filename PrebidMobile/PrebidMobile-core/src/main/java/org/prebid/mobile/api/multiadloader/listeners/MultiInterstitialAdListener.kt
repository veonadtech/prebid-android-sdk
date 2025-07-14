package org.prebid.mobile.api.multiadloader.listeners

import com.yandex.mobile.ads.common.ImpressionData
import org.prebid.mobile.api.data.AdPlatformSDK

interface MultiInterstitialAdListener {

    fun onAdLoaded(sdk: AdPlatformSDK)
    fun onAdFailed(error: String?, sdk: AdPlatformSDK?)
    fun onAdDisplayed(sdk: AdPlatformSDK)
    fun onAdFailedToShow(error: String?, sdk: AdPlatformSDK?)
    fun onAdClicked(sdk: AdPlatformSDK)
    fun onAdClosed(sdk: AdPlatformSDK)
    fun onImpression(impressionData: ImpressionData?, sdk: AdPlatformSDK)

}