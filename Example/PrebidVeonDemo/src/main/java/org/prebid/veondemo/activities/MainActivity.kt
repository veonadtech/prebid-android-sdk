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

package org.prebid.veondemo.activities

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
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import org.prebid.mobile.AdSize
import org.prebid.mobile.BannerAdUnit
import org.prebid.mobile.BannerParameters
import org.prebid.mobile.Signals
import org.prebid.mobile.addendum.AdViewUtils
import org.prebid.mobile.addendum.PbFindSizeError
import org.prebid.mobile.api.data.AdUnitFormat
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.rendering.BannerView
import org.prebid.mobile.api.rendering.InterstitialAdUnit
import org.prebid.mobile.api.rendering.RewardedAdUnit
import org.prebid.mobile.api.rendering.listeners.BannerViewListener
import org.prebid.mobile.api.rendering.listeners.InterstitialAdUnitListener
import org.prebid.mobile.api.rendering.listeners.RewardedAdUnitListener
import org.prebid.mobile.eventhandlers.AuctionBannerEventHandler
import org.prebid.mobile.eventhandlers.AuctionListener
import org.prebid.mobile.eventhandlers.GamBannerEventHandler
import org.prebid.mobile.eventhandlers.GamInterstitialEventHandler
import org.prebid.mobile.eventhandlers.GamRewardedEventHandler
import org.prebid.veondemo.R
import org.prebid.veondemo.databinding.ActivityMainBinding
import org.prebid.veondemo.utils.Settings
import java.util.EnumSet


enum class Format(val description: String) {
    AUCTION_SIMPLE_BANNER("Auction Simple Banner"),
    SIMPLE_BANNER("Simple Banner"),
    INTERSTITIAL_BANNER("Interstitial Banner"),
    VIDEO_REWARDED("Rewarded Video"),

    GAM_SIMPLE_BANNER("GAM Simple Banner"),
    GAM_INTERSTITIAL_BANNER("GAM Interstitial Banner"),
    GAM_REWARD_VIDEO("GAM Rewarded Video"),
}

class MainActivity : AppCompatActivity() {

    private var adFormat: org.prebid.veondemo.activities.Format? = null
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
            adWrapperView.removeAllViewsInLayout()
            when (adFormat) {

                org.prebid.veondemo.activities.Format.SIMPLE_BANNER -> {
                    val adUnit = BannerView(this, "prebid-ita-banner-320-50", AdSize(320, 50))
                    adWrapperView.addView(adUnit)
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

                    adUnit.loadAd()
                }

                org.prebid.veondemo.activities.Format.VIDEO_REWARDED -> {
                    val adUnit = RewardedAdUnit(this, "prebid-ita-video-rewarded-320-480")
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

                org.prebid.veondemo.activities.Format.INTERSTITIAL_BANNER -> {

                    val adUnit = InterstitialAdUnit(this, "prebid-ita-banner-320-50", EnumSet.of(AdUnitFormat.BANNER))
                    adUnit.setMinSizePercentage(AdSize(50, 50))
                    adUnit.setInterstitialAdUnitListener(object : InterstitialAdUnitListener {

                        override fun onAdLoaded(interstitialAdUnit: InterstitialAdUnit?) {
                            Toast.makeText(applicationContext, "onAdLoaded", Toast.LENGTH_LONG).show()
                            adUnit.show()
                        }

                        override fun onAdDisplayed(interstitialAdUnit: InterstitialAdUnit?) {
                            Toast.makeText(applicationContext, "onAdDisplayed", Toast.LENGTH_LONG).show()
                        }
                        override fun onAdFailed(interstitialAdUnit: InterstitialAdUnit?, e: AdException?) {
                            Toast.makeText(applicationContext, "onAdFailed", Toast.LENGTH_LONG).show()
                        }
                        override fun onAdClicked(interstitialAdUnit: InterstitialAdUnit?) {
                            Toast.makeText(applicationContext, "onAdClicked", Toast.LENGTH_LONG).show()
                        }
                        override fun onAdClosed(interstitialAdUnit: InterstitialAdUnit?) {
                            Toast.makeText(applicationContext, "onAdClosed", Toast.LENGTH_LONG).show()
                        }
                    })
                    adUnit.loadAd()

                }

                org.prebid.veondemo.activities.Format.AUCTION_SIMPLE_BANNER -> {

                    // listener for wrapping GAM rendering
                    val eventHandler = AuctionBannerEventHandler(
                        this,
                        "/6355419/Travel/Europe/France/Paris",
                        0.5F,
                        AdSize(320, 50)
                    )

                    // lister for understand where from demand
                    eventHandler.setAuctionEventListener(object : AuctionListener {
                        override fun onPRBWin(price: Float) {
                            Toast.makeText(applicationContext, "onPRBWin", Toast.LENGTH_LONG).show()
                        }
                        override fun onGAMWin(view: View?) {
                            Toast.makeText(applicationContext, "onGAMWin", Toast.LENGTH_LONG).show()
                        }
                    })

                    // configure banner placement
                    val adUnit = BannerView(this, "prebid-ita-banner-320-50", eventHandler)

                    // lister for custom tracking or custom display creative
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

                    val AdSlot = binding.banner320x50
                    AdSlot.addView(adUnit)
                    adUnit.loadAd()
                }

                org.prebid.veondemo.activities.Format.GAM_SIMPLE_BANNER -> {

                    val adUnit = BannerAdUnit(
                        "prebid-ita-banner-320-50",
                        320,
                        50,
                    )
                    adUnit.setAutoRefreshInterval(30)

                    val parameters = BannerParameters()
                    parameters.api = listOf(Signals.Api.MRAID_3, Signals.Api.OMID_1)
                    adUnit.bannerParameters = parameters

                    // GAM LOADER
                    val adView = AdManagerAdView(this)
                    adView.adUnitId = "/21952429235,23020124565/be_kg.beeline.odp_appbanner"
                    adView.setAdSizes(com.google.android.gms.ads.AdSize(320, 50))
                    adView.adListener = object: AdListener() {
                        override fun onAdClicked() {
                            super.onAdClicked()
                            Toast.makeText(applicationContext, "onAdClicked", Toast.LENGTH_LONG).show()
                        }
                        override fun onAdClosed() {
                            super.onAdClosed()
                            Toast.makeText(applicationContext, "onAdClosed", Toast.LENGTH_LONG).show()
                        }
                        override fun onAdFailedToLoad(adError : LoadAdError) {
                            super.onAdFailedToLoad(adError)
                            Toast.makeText(applicationContext, "onAdFailedToLoad", Toast.LENGTH_LONG).show()
                        }
                        override fun onAdImpression() {
                            super.onAdImpression()
                            Toast.makeText(applicationContext, "onAdImpression", Toast.LENGTH_LONG).show()
                        }
                        override fun onAdLoaded() {
                            super.onAdLoaded()
                            AdViewUtils.findPrebidCreativeSize(adView, object : AdViewUtils.PbFindSizeListener {
                                override fun success(width: Int, height: Int) {
                                    adView.setAdSizes(
                                        com.google.android.gms.ads.AdSize(
                                            width,
                                            height
                                        )
                                    )
                                }
                                override fun failure(error: PbFindSizeError) {}
                            })
                            Toast.makeText(applicationContext, "onAdLoaded", Toast.LENGTH_LONG).show()
                        }
                        override fun onAdOpened() {
                            super.onAdOpened()
                            Toast.makeText(applicationContext, "onAdOpened", Toast.LENGTH_LONG).show()
                        }
                    }

                    adWrapperView.addView(adView)

                    // PREBID LOADER
                    val request = AdManagerAdRequest.Builder().build()
                    adUnit.fetchDemand(request) {
                        adView.loadAd(request)
                    }
                }

                org.prebid.veondemo.activities.Format.GAM_INTERSTITIAL_BANNER -> {

                    val eventHandler = GamInterstitialEventHandler(
                        this,
                        "/ca-app-pub-3940256099942544/1033173712"
                    )

                    val adUnit = InterstitialAdUnit(this, "banner-interstitial", eventHandler)
                    adUnit.setMinSizePercentage(AdSize(80, 60))
                    adUnit.setInterstitialAdUnitListener(object : InterstitialAdUnitListener {
                        override fun onAdLoaded(bannerView: InterstitialAdUnit?) {
                            Toast.makeText(applicationContext, "onAdLoaded", Toast.LENGTH_LONG).show()
                            adUnit.show()
                        }
                        override fun onAdDisplayed(bannerView: InterstitialAdUnit?) {
                            Toast.makeText(applicationContext, "onAdDisplayed", Toast.LENGTH_LONG).show()
                        }

                        override fun onAdFailed(bannerView: InterstitialAdUnit?, exception: AdException?) {
                            Toast.makeText(applicationContext, "onAdFailed", Toast.LENGTH_LONG).show()
                        }

                        override fun onAdClicked(bannerView: InterstitialAdUnit?) {
                            Toast.makeText(applicationContext, "onAdClicked", Toast.LENGTH_LONG).show()
                        }

                        override fun onAdClosed(bannerView: InterstitialAdUnit?) {
                            Toast.makeText(applicationContext, "onAdClosed", Toast.LENGTH_LONG).show()
                        }
                    })

                    adUnit.loadAd()
                }

                org.prebid.veondemo.activities.Format.GAM_REWARD_VIDEO -> {
                    val eventHandler = GamRewardedEventHandler(
                        this,
                        "/21952429235,23020124565/be_org.prebid.veondemo_app/be_org.prebid.veondemo_appopen"
                    )
                    val adUnit = RewardedAdUnit(this, "prebid-ita-video-rewarded-320-480", eventHandler)
                    adUnit.setRewardedAdUnitListener(object : RewardedAdUnitListener {
                        override fun onAdLoaded(rewardedAdUnit: RewardedAdUnit?) {
                            if (adUnit.bidResponse.winningBid?.price!! > 0.5) {
                                adUnit.show()
                            }
                        }
                        override fun onAdDisplayed(rewardedAdUnit: RewardedAdUnit?) {
                            Toast.makeText(applicationContext, "onAdDisplayed", Toast.LENGTH_LONG).show()
                        }
                        override fun onAdFailed(rewardedAdUnit: RewardedAdUnit?, exception: AdException?) {
                            Toast.makeText(applicationContext, "onAdFailed", Toast.LENGTH_LONG).show()
                        }
                        override fun onAdClicked(rewardedAdUnit: RewardedAdUnit?) {
                            Toast.makeText(applicationContext, "onAdClicked", Toast.LENGTH_LONG).show()
                        }
                        override fun onAdClosed(rewardedAdUnit: RewardedAdUnit?) {
                            Toast.makeText(applicationContext, "onAdClosed", Toast.LENGTH_LONG).show()
                        }
                        override fun onUserEarnedReward(rewardedAdUnit: RewardedAdUnit?) {
                            Toast.makeText(applicationContext, "onUserEarnedReward", Toast.LENGTH_LONG).show()
                        }
                    })
                    adUnit.loadAd()
                }

                else -> {}
            }
        }
    }

    private fun initVariants() {

        binding.spinnerAdType.apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                org.prebid.veondemo.activities.Format.values().map { it.description }.toMutableList().apply{}
            )
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(adapterView: AdapterView<*>, view: View?, position: Int, l: Long) {
                    adFormat = org.prebid.veondemo.activities.Format.values()[position]
                    Log.d("SELECTED", position.toString())
                }

                override fun onNothingSelected(adapterView: AdapterView<*>) {}
            }
            setSelection(Settings.get().lastAdFormatId)
        }
    }

}

