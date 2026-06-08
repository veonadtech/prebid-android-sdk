package org.prebid.mobile.api.multiloadernextgen.listeners

import android.view.View
import com.yandex.mobile.ads.common.ImpressionData
import org.prebid.mobile.api.data.SdkType
import org.prebid.mobile.api.rendering.BannerView

interface MultiBannerViewListenerNextGen {

    fun onAdLoaded(adView: View, sdk: SdkType)
    fun onAdFailed(bannerView: BannerView?, error: String?, sdk: SdkType?)
    fun onAdClicked(bannerView: BannerView?, sdk: SdkType)
    fun onLeftApplication(sdk: SdkType)
    fun onReturnedToApplication(sdk: SdkType)
    fun onImpression(impressionData: ImpressionData?, sdk: SdkType)
    fun onAdClosed(bannerView: BannerView?, sdk: SdkType)
    fun onAdDisplayed(bannerView: BannerView?, sdk: SdkType)
    fun onAdOpened(sdk: SdkType)

}
