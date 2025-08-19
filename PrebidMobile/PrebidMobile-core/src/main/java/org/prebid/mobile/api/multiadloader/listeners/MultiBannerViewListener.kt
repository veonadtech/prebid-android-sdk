package org.prebid.mobile.api.multiadloader.listeners

import android.view.View
import org.prebid.mobile.api.data.SdkType
import org.prebid.mobile.api.rendering.BannerView

interface MultiBannerViewListener {

    fun onAdLoaded(adView: View, sdk: SdkType)
    fun onAdFailed(bannerView: BannerView?, error: String?, sdk: SdkType?)
    fun onAdClicked(bannerView: BannerView?, sdk: SdkType)
    fun onImpression(sdk: SdkType)
    fun onAdUrlClicked(url: String?, sdk: SdkType)
    fun onAdClosed(bannerView: BannerView?, sdk: SdkType)
    fun onAdDisplayed(bannerView: BannerView?, sdk: SdkType)
    fun onAdOpened(sdk: SdkType)

}