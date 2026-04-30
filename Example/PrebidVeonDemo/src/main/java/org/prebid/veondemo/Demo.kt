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
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
        initTestPrebidSDK()
        initGamSDK()
        TargetingParams.setSubjectToGDPR(true)
        Settings.init(this)
    }

    private fun initTestPrebidSDK() {
        Log.d(TAG, "SDK start initialization")

        PrebidMobile.setPrebidServerAccountId("uz.beeline.odp")
        PrebidMobile.setCustomStatusEndpoint("https://prebid.veonadx.com/status")
        PrebidMobile.setTimeoutMillis(3000)
        PrebidMobile.setShareGeoLocation(true)
        PrebidMobile.setLogLevel(PrebidMobile.LogLevel.DEBUG)
        PrebidMobile.useExternalBrowser = true

        PrebidMobile.initializeSdk(
            applicationContext,
            "https://prebid.veonadx.com/openrtb2/auction",
   //         "https://dcdn.veonadx.com/sdk/uz.beeline.odp/config.json"
        ) { status ->
            if (status == InitializationStatus.SUCCEEDED) {
                Log.d(TAG, "Prebid SDK initialized successfully!")
            } else {
                Log.e(TAG, "Prebid SDK initialization error: $status\n${status.description}")
            }
        }
    }

    private fun initGamSDK() {
        Log.d(TAG, "GAM SDK initialization started")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Initialize the Google Mobile Ads SDK on a background thread.
                MobileAds.initialize(this@Demo) { initializationStatus ->
                    Log.d(TAG, "GAM SDK initialization completed")
                    Log.d(TAG, "GAM SDK Initialization status: $initializationStatus")

                    // Log individual adapter statuses
                    val adapterStatusMap = initializationStatus.adapterStatusMap
                    if (adapterStatusMap.isNotEmpty()) {
                        Log.d(TAG, "GAM SDK Adapter statuses:")
                        adapterStatusMap.forEach { (adapterClass, status) ->
                            Log.d(TAG, "  $adapterClass: ${status.initializationState} - ${status.description}")
                        }
                    } else {
                        Log.d(TAG, "No adapter statuses available")
                    }
                }
                Log.d(TAG, "GAM SDK initialized successfully on background thread")
            } catch (e: Exception) {
                Log.e(TAG, "GAM SDK initialization failed with error: ${e.message}", e)
            }
        }
    }
}