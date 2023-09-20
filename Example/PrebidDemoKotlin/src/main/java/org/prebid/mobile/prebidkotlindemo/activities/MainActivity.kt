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

package org.prebid.mobile.prebidkotlindemo.activities

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import org.prebid.mobile.AdSize
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.rendering.BannerView
import org.prebid.mobile.api.rendering.RewardedAdUnit
import org.prebid.mobile.api.rendering.listeners.BannerViewListener
import org.prebid.mobile.api.rendering.listeners.RewardedAdUnitListener
import org.prebid.mobile.prebidkotlindemo.R
import org.prebid.mobile.prebidkotlindemo.activities.ads.inapp.InAppVideoRewardedActivity
import org.prebid.mobile.prebidkotlindemo.databinding.ActivityMainBinding
import org.prebid.mobile.prebidkotlindemo.cases.*
import org.prebid.mobile.prebidkotlindemo.utils.Settings

enum class Format(val description: String) {
    SIMPLE_BANNER("Simple Banner"),
    INTERSTITIAL_BANNER("Interstitial Banner"),
    VIDEO_REWARDED("Rewarded Video"),
}

class MainActivity : AppCompatActivity() {

    private var adFormat: Format? = null
    private val adWrapperView: ViewGroup get() = binding.adLayout
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        initVariants()
        initActions()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    private fun initActions() {
        val ShowBanner = findViewById(R.id.show_banner) as Button;

        ShowBanner.setOnClickListener {

            when (adFormat) {
                Format.SIMPLE_BANNER -> {
                    val adUnit: BannerView?
                    adUnit = BannerView(this, "prebid-ita-banner-320-50", AdSize(350, 200))
                    adUnit.setBannerListener(object : BannerViewListener {
                        override fun onAdLoaded(bannerView: BannerView?) {
                            Toast.makeText(applicationContext, "onAdLoaded", Toast.LENGTH_LONG).show()
                        }
                        override fun onAdDisplayed(bannerView: BannerView?) {
                            Toast.makeText(applicationContext, "onAdDisplayed", Toast.LENGTH_LONG).show()
                        }

                        override fun onAdFailed(bannerView: BannerView?, exception: AdException?) {
                            Toast.makeText(applicationContext, "onAdFailed", Toast.LENGTH_LONG).show()
                        }

                        override fun onAdClicked(bannerView: BannerView?) {
                            Toast.makeText(applicationContext, "onAdClicked", Toast.LENGTH_LONG).show()
                        }

                        override fun onAdClosed(bannerView: BannerView?) {
                            Toast.makeText(applicationContext, "onAdClosed", Toast.LENGTH_LONG).show()
                        }
                    })
                    binding.adLayout.visibility = View.VISIBLE
                    adWrapperView.addView(adUnit)
                    adUnit.loadAd()
                }
                Format.VIDEO_REWARDED -> {
                    val adUnit: RewardedAdUnit?
                    adUnit = RewardedAdUnit(this, InAppVideoRewardedActivity.CONFIG_ID)
                    adUnit.setRewardedAdUnitListener(object : RewardedAdUnitListener {
                        override fun onAdLoaded(rewardedAdUnit: RewardedAdUnit?) {
                            adUnit.show()
                        }
                        override fun onAdDisplayed(rewardedAdUnit: RewardedAdUnit?) {}
                        override fun onAdFailed(rewardedAdUnit: RewardedAdUnit?, exception: AdException?) {}
                        override fun onAdClicked(rewardedAdUnit: RewardedAdUnit?) {}
                        override fun onAdClosed(rewardedAdUnit: RewardedAdUnit?) {}
                        override fun onUserEarnedReward(rewardedAdUnit: RewardedAdUnit?) {}
                    })
                    adUnit.loadAd()

                }
                Format.INTERSTITIAL_BANNER -> {}
                else -> {}
            }
        }
    }

    private fun initVariants() {

        binding.spinnerAdType.apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                Format.values().map { it.description }.toMutableList().apply{}
            )
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(adapterView: AdapterView<*>, view: View?, position: Int, l: Long) {
                    adFormat = Format.values()[position]
                    Log.d("SELECTED", position.toString())
                }

                override fun onNothingSelected(adapterView: AdapterView<*>) {}
            }
            setSelection(Settings.get().lastAdFormatId)
        }
    }

}

