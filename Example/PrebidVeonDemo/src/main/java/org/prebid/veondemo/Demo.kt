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

package org.prebid.veondemo

import android.app.Application
import android.util.Log
import org.prebid.mobile.Host
import org.prebid.mobile.PrebidMobile
import org.prebid.mobile.TargetingParams
import org.prebid.mobile.api.data.InitializationStatus
import org.prebid.mobile.api.rendering.MultiAdLoader.AdPlatformSDK
import org.prebid.mobile.rendering.sdk.AsyncSdkConfigLoader
import org.prebid.veondemo.utils.Settings

object SdkConfigHolder {
    var priorityOrderSDK: List<AdPlatformSDK> = listOf(AdPlatformSDK.YANDEX, AdPlatformSDK.GAM, AdPlatformSDK.PREBID)
}

class Demo : Application() {

    companion object {
        private const val TAG = "Demo"
    }

    override fun onCreate() {
        super.onCreate()
        initSdkConfig()
        initTestPrebidSDK()
        TargetingParams.setSubjectToGDPR(true)
        Settings.init(this)
    }

    private fun initSdkConfig() {
        Log.d(TAG, "Load SDK priority started")
        AsyncSdkConfigLoader().loadSdkConfig(object : AsyncSdkConfigLoader.SdkConfigResponseHandler {
            override fun onSdkConfigReceived(sdks: List<AdPlatformSDK>) {
                Log.d(TAG, "Successfully loaded SDK priority order from server: ${sdks.joinToString()}")
                SdkConfigHolder.priorityOrderSDK = sdks
            }

            override fun onError(error: String) {
                Log.e(TAG, "Failed to load SDK config: $error")
            }
        })
    }

    private fun initTestPrebidSDK() {
        Log.d(TAG, "SDK start initialization")

        PrebidMobile.setPrebidServerAccountId("test")
        PrebidMobile.setPrebidServerHost(Host.createCustomHost("https://prebid-01.veonadx.com/openrtb2/auction"))
        PrebidMobile.setCustomStatusEndpoint("https://prebid.veonadx.com/status")
        PrebidMobile.setTimeoutMillis(3000)
        PrebidMobile.setShareGeoLocation(true)
        PrebidMobile.useExternalBrowser = true
        PrebidMobile.setLogLevel(PrebidMobile.LogLevel.DEBUG)

        PrebidMobile.initializeSdk(applicationContext) { status ->
            if (status == InitializationStatus.SUCCEEDED) {
                Log.d(TAG, "SDK initialized successfully!")
            } else {
                Log.e(TAG, "SDK initialization error: $status\n${status.description}")
            }
        }
    }

}
