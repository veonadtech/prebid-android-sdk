package org.prebid.mobile.api.rendering.listeners

import android.view.View
import com.yandex.mobile.ads.common.ImpressionData
import org.prebid.mobile.api.data.AdPlatformSDK
import org.prebid.mobile.api.rendering.BannerView

interface MultiAdLoaderListener {

    fun onAdLoaded(adView: View, sdk: AdPlatformSDK)
    fun onAdFailed(bannerView: BannerView?, error: String?, sdk: AdPlatformSDK?)
    fun onAdClicked(bannerView: BannerView?, sdk: AdPlatformSDK)
    fun onLeftApplication(sdk: AdPlatformSDK)
    fun onReturnedToApplication(sdk: AdPlatformSDK)
    fun onImpression(impressionData: ImpressionData?, sdk: AdPlatformSDK)
    fun onAdUrlClicked(url: String?, sdk: AdPlatformSDK)
    fun onAdClosed(bannerView: BannerView?, sdk: AdPlatformSDK)
    fun onAdDisplayed(bannerView: BannerView?, sdk: AdPlatformSDK)
    fun onAdOpened(sdk: AdPlatformSDK)

}