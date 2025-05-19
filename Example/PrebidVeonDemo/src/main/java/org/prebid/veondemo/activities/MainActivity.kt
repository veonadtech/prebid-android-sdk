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
import java.util.EnumSet

enum class BannerFormat(val description: String) {
    AUCTION_SIMPLE_BANNER("Auction Simple Banner"),
    AUCTION_SIMPLE_BANNER_300_250("300x250"),
    SIMPLE_TEST_BANNER("Simple Test Banner"),
    SIMPLE_BANNER("Simple Banner"),
    INTERSTITIAL_BANNER("Interstitial Banner"),
    VIDEO_REWARDED("Rewarded Video"),

    GAM_SIMPLE_BANNER("GAM Simple Banner"),
    GAM_INTERSTITIAL_BANNER("GAM Interstitial Banner"),
    GAM_REWARD_VIDEO("GAM Rewarded Video"),
}

class MainActivity : AppCompatActivity() {

    private var adBannerFormat: BannerFormat? = null
    private lateinit var binding: ActivityMainBinding
    private val adWrapperView: ViewGroup get() = binding.adLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        initAdFormatSelector()
        setupAdFormatSelectionActions()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    private fun setupAdFormatSelectionActions() {
        binding.showBanner.setOnClickListener {
            removeAllBannerSlots()
            adBannerFormat?.let { format ->
                handleAdFormat(format)
            }
        }
    }

    private fun handleAdFormat(bannerFormat: BannerFormat) {
        when (bannerFormat) {
            BannerFormat.SIMPLE_TEST_BANNER -> setupSimpleBanner(
                configId = "test_320x50",
                size = AdSize(320, 50)
            )

            BannerFormat.SIMPLE_BANNER -> setupSimpleBanner(
                configId = "prebid-ita-banner-320-50",
                size = AdSize(320, 50)
            )

            BannerFormat.VIDEO_REWARDED -> setupRewardedVideo(
                configId = "test_video_content_320x100"
            )

            BannerFormat.INTERSTITIAL_BANNER -> setupInterstitialBanner(
                configId = "test_interstitial",
                adSize = AdSize(50, 50)
            )

            BannerFormat.AUCTION_SIMPLE_BANNER -> setupAuctionBanner(
                adUnitId = "/6355419/Travel/Europe/France/Paris",
                size = AdSize(320, 50),
                slot = binding.banner320x50,
                cpm = 10F
            )

            BannerFormat.AUCTION_SIMPLE_BANNER_300_250 -> setupAuctionBanner(
                adUnitId = "/6355419/Travel/Europe/France/Paris",
                size = AdSize(300, 250),
                slot = binding.banner300x250,
                cpm = 50F
            )

            BannerFormat.GAM_SIMPLE_BANNER -> setupGamSimpleBanner(
                configId = "prebid-ita-banner-320-50",
                adSize = AdSize(320, 50),
                adUnitId = "/6355419/Travel/Europe/France/Paris"
            )

            BannerFormat.GAM_INTERSTITIAL_BANNER -> setupGamInterstitialBanner(
                gamAdUnitId = "/ca-app-pub-3940256099942544/1033173712",
                configId = "banner-interstitial",
                adSize = AdSize(80, 60)
            )

            BannerFormat.GAM_REWARD_VIDEO ->
                setupGamRewardVideo(
                    gamAdUnitId = "/21952429235,23020124565/be_org.prebid.veondemo_app/be_org.prebid.veondemo_appopen",
                    configId = "prebid-ita-video-rewarded-320-480"
                )
        }
    }

    private fun removeAllBannerSlots() {
        adWrapperView.removeAllViewsInLayout()
        binding.banner320x50.removeAllViewsInLayout()
        binding.banner300x250.removeAllViewsInLayout()
    }

    private fun setupSimpleBanner(configId: String, size: AdSize) {
        val adUnit = BannerView(this, configId, size).apply {
            setBannerListener(defaultBannerListener())
            loadAd()
            setAutoRefreshDelay(30)
        }
        adWrapperView.addView(adUnit)
    }

    private fun setupRewardedVideo(configId: String) {
        val adUnit = RewardedAdUnit(this, configId)
        adUnit.setRewardedAdUnitListener(object : RewardedAdUnitListener {
            override fun onAdLoaded(unit: RewardedAdUnit?) = adUnit.show()
            override fun onAdDisplayed(unit: RewardedAdUnit?) {}
            override fun onAdFailed(unit: RewardedAdUnit?, e: AdException?) {}
            override fun onAdClicked(unit: RewardedAdUnit?) {}
            override fun onAdClosed(unit: RewardedAdUnit?) {}
            override fun onUserEarnedReward(unit: RewardedAdUnit?) {}
        })
        adUnit.loadAd()
    }

    private fun setupInterstitialBanner(configId: String, adSize: AdSize) {
        val adUnit = InterstitialAdUnit(this, configId, EnumSet.of(AdUnitFormat.BANNER))
        adUnit.setMinSizePercentage(adSize)
        adUnit.setInterstitialAdUnitListener(object : InterstitialAdUnitListener {
            override fun onAdLoaded(unit: InterstitialAdUnit?) {
                showToast("onAdLoaded")
                adUnit.show()
            }

            override fun onAdDisplayed(unit: InterstitialAdUnit?) = showToast("onAdDisplayed")
            override fun onAdFailed(unit: InterstitialAdUnit?, e: AdException?) = showToast("onAdFailed")
            override fun onAdClicked(unit: InterstitialAdUnit?) = showToast("onAdClicked")
            override fun onAdClosed(unit: InterstitialAdUnit?) = showToast("onAdClosed")
        })
        adUnit.loadAd()
    }

    private fun setupAuctionBanner(adUnitId: String, size: AdSize, slot: ViewGroup, cpm: Float) {
        val eventHandler = AuctionBannerEventHandler(
            this, adUnitId, cpm, size
        ).apply {
            setAuctionEventListener(object : AuctionListener {
                override fun onPRBWin(price: Float) = showToast("onPRBWin")
                override fun onGAMWin(view: View?) = showToast("onGAMWin")
            })
        }

        val adUnit = BannerView(this, "prebid-ita-banner-${size.width}x${size.height}", eventHandler).apply {
            setBannerListener(defaultBannerListener())
            loadAd()
        }

        slot.addView(adUnit)
    }

    private fun setupGamSimpleBanner(configId: String, adSize: AdSize, adUnitId: String) {
        val eventHandler = GamBannerEventHandler(this, adUnitId, adSize)
        val bannerView = BannerView(this, configId, eventHandler)
        adWrapperView.addView(bannerView)
        bannerView.loadAd()

        bannerView.setBannerListener(object : BannerViewListener {
            override fun onAdLoaded(bannerView: BannerView?) {
                showToast("onAdLoaded")
            }

            override fun onAdDisplayed(bannerView: BannerView?) {
                showToast("onAdDisplayed")
            }

            override fun onAdFailed(bannerView: BannerView?, exception: AdException?) {
                showToast("onAdFailed")
            }

            override fun onAdClicked(bannerView: BannerView?) {
                showToast("onAdClicked")
            }

            override fun onAdUrlClicked(url: String?) {
                showToast("onAdUrlClicked")
            }

            override fun onAdClosed(bannerView: BannerView?) {
                showToast("onAdClosed")
            }

        })

//        val adUnit = BannerAdUnit(configId, adSize.width, adSize.height).apply {
//            bannerParameters = BannerParameters().apply {
//                api = listOf(Signals.Api.MRAID_3, Signals.Api.OMID_1)
//            }
//            setAutoRefreshInterval(30)
//        }
//
//        val adView = AdManagerAdView(this)
//        adView.adUnitId = adUnitId
//        adView.setAdSizes(com.google.android.gms.ads.AdSize(320, 50))
//
//        adView.adListener = object : AdListener() {
//            override fun onAdClicked() = showToast("onAdClicked")
//            override fun onAdClosed() = showToast("onAdClosed")
//            override fun onAdFailedToLoad(adError: LoadAdError) = showToast("onAdFailedToLoad")
//            override fun onAdImpression() = showToast("onAdImpression")
//            override fun onAdOpened() = showToast("onAdOpened")
//            override fun onAdLoaded() {
//                showToast("onAdLoaded")
//                AdViewUtils.findPrebidCreativeSize(adView, object : AdViewUtils.PbFindSizeListener {
//                    override fun success(width: Int, height: Int) {
//                        adView.setAdSizes(
//                            com.google.android.gms.ads.AdSize(
//                                width,
//                                height
//                            )
//                        )
//                    }
//
//                    override fun failure(error: PbFindSizeError) {}
//                })
//            }
//        }
//
//        adWrapperView.addView(adView)
//
//        val request = AdManagerAdRequest.Builder().build()
//        adUnit.fetchDemand(request) { adView.loadAd(request) }
    }

    private fun setupGamInterstitialBanner(gamAdUnitId: String, configId: String, adSize: AdSize) {
        val eventHandler = GamInterstitialEventHandler(this, gamAdUnitId)
        InterstitialAdUnit(this, configId, eventHandler).apply {
            setMinSizePercentage(adSize)
            setInterstitialAdUnitListener(object : InterstitialAdUnitListener {
                override fun onAdLoaded(unit: InterstitialAdUnit?) {
                    showToast("onAdLoaded")
                    show()
                }

                override fun onAdDisplayed(unit: InterstitialAdUnit?) = showToast("onAdDisplayed")
                override fun onAdFailed(unit: InterstitialAdUnit?, e: AdException?) = showToast("onAdFailed")
                override fun onAdClicked(unit: InterstitialAdUnit?) = showToast("onAdClicked")
                override fun onAdClosed(unit: InterstitialAdUnit?) = showToast("onAdClosed")
            })
            loadAd()
        }
    }

    private fun setupGamRewardVideo(gamAdUnitId: String, configId: String) {
        val eventHandler = GamRewardedEventHandler(this, gamAdUnitId)
        RewardedAdUnit(this, configId, eventHandler).apply {
            setRewardedAdUnitListener(object : RewardedAdUnitListener {
                override fun onAdLoaded(unit: RewardedAdUnit?) {
                    if ((bidResponse.winningBid?.price ?: 0.0) > 0.5) show()
                }

                override fun onAdDisplayed(unit: RewardedAdUnit?) = showToast("onAdDisplayed")
                override fun onAdFailed(unit: RewardedAdUnit?, e: AdException?) = showToast("onAdFailed")
                override fun onAdClicked(unit: RewardedAdUnit?) = showToast("onAdClicked")
                override fun onAdClosed(unit: RewardedAdUnit?) = showToast("onAdClosed")
                override fun onUserEarnedReward(unit: RewardedAdUnit?) = showToast("onUserEarnedReward")
            })
            loadAd()
        }
    }

    private fun defaultBannerListener() = object : BannerViewListener {
        override fun onAdUrlClicked(url: String?) = showToast(url ?: "Url Clicked")
        override fun onAdLoaded(bannerView: BannerView?) = showToast("onAdLoaded")
        override fun onAdDisplayed(bannerView: BannerView?) = showToast("onAdDisplayed")
        override fun onAdFailed(bannerView: BannerView?, exception: AdException?) = showToast("onAdFailed")
        override fun onAdClicked(bannerView: BannerView?) = showToast("onAdClicked")
        override fun onAdClosed(bannerView: BannerView?) = showToast("onAdClosed")
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun initAdFormatSelector() {
        binding.spinnerAdType.apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                BannerFormat.values().map { it.description }
            )
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(adapterView: AdapterView<*>, view: View?, position: Int, l: Long) {
                    adBannerFormat = BannerFormat.values()[position]
                    Log.d("SELECTED", position.toString())
                }

                override fun onNothingSelected(adapterView: AdapterView<*>) {}
            }
        }
    }

}

