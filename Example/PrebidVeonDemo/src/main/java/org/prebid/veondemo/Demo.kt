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
import org.prebid.veondemo.utils.Settings

class Demo : Application() {

    companion object {
        private const val TAG = "Demo"
    }

    override fun onCreate() {
        super.onCreate()
        initPrebidSDK()
        TargetingParams.setSubjectToGDPR(true)
        Settings.init(this)
    }

    private fun initPrebidSDK() {
        Log.d(org.prebid.veondemo.Demo.Companion.TAG, "SDK start initialization")

        PrebidMobile.setPrebidServerAccountId("test")
        PrebidMobile.setPrebidServerHost(Host.createCustomHost("http://prebid.jazzdsp.com/openrtb2/auction"))
        PrebidMobile.setCustomStatusEndpoint("http://prebid.jazzdsp.com/status")
        PrebidMobile.setTimeoutMillis(3000)
        PrebidMobile.setShareGeoLocation(true)

        PrebidMobile.initializeSdk(applicationContext) { status ->
            if (status == InitializationStatus.SUCCEEDED) {
                Log.d(org.prebid.veondemo.Demo.Companion.TAG, "SDK initialized successfully!")
            } else {
                Log.e(org.prebid.veondemo.Demo.Companion.TAG, "SDK initialization error: $status\n${status.description}")
            }
        }
        PrebidMobile.setShareGeoLocation(true)
    }

}
