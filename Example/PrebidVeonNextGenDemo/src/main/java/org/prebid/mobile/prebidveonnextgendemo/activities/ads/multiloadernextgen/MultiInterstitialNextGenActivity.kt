/*
 *    Copyright 2018-2019 Prebid.org, Inc.
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
package org.prebid.mobile.prebidveonnextgendemo.activities.ads.multiloadernextgen

import android.os.Bundle
import org.prebid.mobile.api.data.SdkType
import org.prebid.mobile.api.multiloadernextgen.MultiInterstitialAdLoaderNextGenGam
import org.prebid.mobile.api.multiloadercommon.MultiInterstitialAdListener
import org.prebid.mobile.prebidveonnextgendemo.activities.BaseAdActivity

class MultiInterstitialNextGenActivity : BaseAdActivity() {

    companion object {
        const val CONFIG_ID = "prebid-demo-display-interstitial-320-480"
        const val GAM_AD_UNIT_ID = "/21808260008/prebid-demo-app-original-api-display-interstitial"
    }

    private var loader: MultiInterstitialAdLoaderNextGenGam? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createAd()
    }

    private fun createAd() {
        loader = MultiInterstitialAdLoaderNextGenGam(
            this,
            CONFIG_ID,
            GAM_AD_UNIT_ID
        ).apply {
            setListener(object : MultiInterstitialAdListener {
                override fun onAdLoaded(sdk: SdkType) {
                    events.loaded(true)
                    showAd()
                }

                override fun onAdFailed(error: String?, sdk: SdkType?) {
                    events.failed(true)
                }

                override fun onAdDisplayed(sdk: SdkType) {
                    events.displayed(true)
                }

                override fun onAdFailedToShow(error: String?, sdk: SdkType?) {
                    events.failed(true)
                }

                override fun onAdClicked(sdk: SdkType) {
                    events.clicked(true)
                }

                override fun onAdClosed(sdk: SdkType) {
                    events.closed(true)
                }
            })
            loadAd()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        loader?.destroy()
    }

}
