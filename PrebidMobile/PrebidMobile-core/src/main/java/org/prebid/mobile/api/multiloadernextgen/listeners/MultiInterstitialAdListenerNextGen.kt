package org.prebid.mobile.api.multiloadernextgen.listeners

import org.prebid.mobile.api.data.SdkType

interface MultiInterstitialAdListenerNextGen {

    fun onAdLoaded(sdk: SdkType)
    fun onAdFailed(error: String?, sdk: SdkType?)
    fun onAdDisplayed(sdk: SdkType)
    fun onAdFailedToShow(error: String?, sdk: SdkType?)
    fun onAdClicked(sdk: SdkType)
    fun onAdClosed(sdk: SdkType)

}
