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
package org.prebid.mobile.prebidnextgendemo.activities.ads.multiloadernextgen

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import org.prebid.mobile.AdSize
import org.prebid.mobile.api.data.SdkType
import org.prebid.mobile.api.rendering.BannerView
import org.prebid.mobile.api.multiloadernextgen.MultiBannerLoaderNextGen
import org.prebid.mobile.api.multiloadercommon.MultiBannerViewListener
import org.prebid.mobile.prebidnextgendemo.activities.BaseAdActivity

class MultiBannerNextGenActivity : BaseAdActivity() {

    companion object {
        const val CONFIG_ID = "prebid-ita-banner-320-50"
        const val GAM_AD_UNIT_ID = "/21808260008/prebid_demo_app_original_api_banner"
        const val WIDTH = 320
        const val HEIGHT = 50
    }

    private var loader: MultiBannerLoaderNextGen? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createAd()
    }

    private fun createAd() {
        loader = MultiBannerLoaderNextGen(
            this,
            AdSize(WIDTH, HEIGHT),
            CONFIG_ID,
            GAM_AD_UNIT_ID,
            refreshTimeSeconds
        ).apply {
            setListener(object : MultiBannerViewListener {
                override fun onAdLoaded(adView: View, sdk: SdkType) {
                    events.loaded(true)
                    adWrapperView.removeAllViews()
                    (adView.parent as? ViewGroup)?.removeView(adView)
                    adWrapperView.addView(adView)
                }

                override fun onAdFailed(bannerView: BannerView?, error: String?, sdk: SdkType?) {
                    events.failed(true)
                }

                override fun onAdClicked(bannerView: BannerView?, sdk: SdkType) {
                    events.clicked(true)
                }

                override fun onImpression(sdk: SdkType) {
                    events.displayed(true)
                }

                override fun onAdClosed(bannerView: BannerView?, sdk: SdkType) {
                    events.closed(true)
                }

                override fun onAdDisplayed(bannerView: BannerView?, sdk: SdkType) {
                    events.displayed(true)
                }

                override fun onAdOpened(sdk: SdkType) {}
            })
            loadAd()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        loader?.destroy()
    }

}
