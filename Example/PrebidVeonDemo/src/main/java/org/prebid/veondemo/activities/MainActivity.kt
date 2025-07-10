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
import android.view.ViewTreeObserver
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.databinding.DataBindingUtil
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import com.yandex.mobile.ads.banner.BannerAdEventListener
import com.yandex.mobile.ads.banner.BannerAdSize
import com.yandex.mobile.ads.banner.BannerAdView
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.ImpressionData
import org.prebid.mobile.AdSize
import org.prebid.mobile.BannerAdUnit
import org.prebid.mobile.BannerParameters
import org.prebid.mobile.Signals
import org.prebid.mobile.addendum.AdViewUtils
import org.prebid.mobile.addendum.PbFindSizeError
import org.prebid.mobile.api.data.AdUnitFormat
import org.prebid.mobile.api.data.VideoPlacementType
import org.prebid.mobile.api.exceptions.AdException
import org.prebid.mobile.api.rendering.BannerView
import org.prebid.mobile.api.rendering.InterstitialAdUnit
import org.prebid.mobile.api.rendering.MultiAdLoader
import org.prebid.mobile.api.rendering.MultiAdLoader.AdPlatformSDK
import org.prebid.mobile.api.rendering.RewardedAdUnit
import org.prebid.mobile.api.rendering.listeners.BannerViewListener
import org.prebid.mobile.api.rendering.listeners.InterstitialAdUnitListener
import org.prebid.mobile.api.rendering.listeners.MultiAdLoaderListener
import org.prebid.mobile.api.rendering.listeners.RewardedAdUnitListener
import org.prebid.mobile.eventhandlers.AuctionBannerEventHandler
import org.prebid.mobile.eventhandlers.AuctionListener
import org.prebid.mobile.eventhandlers.GamBannerEventHandler
import org.prebid.mobile.eventhandlers.GamInterstitialEventHandler
import org.prebid.mobile.eventhandlers.GamRewardedEventHandler
import org.prebid.veondemo.R
import org.prebid.veondemo.databinding.ActivityMainBinding
import org.prebid.veondemo.network.Network
import java.util.EnumSet
import kotlin.math.roundToInt

enum class BannerFormat(val description: String) {
    AUCTION_SIMPLE_BANNER("Auction Simple Banner"),
    AUCTION_SIMPLE_BANNER_300_250("300x250"),
    SIMPLE_TEST_BANNER("Simple Test Banner"),
    SIMPLE_BANNER("Simple Banner"),
    INTERSTITIAL_BANNER("Interstitial Banner"),
    VIDEO_REWARDED("Rewarded Video"),
    VIDEO_IN_BANNER("Video Banner"),
    VIDEO_INTERSTITIAL("Video Interstitial"),

    GAM_SIMPLE_BANNER("GAM Simple Banner"),
    GAM_BANNER("GAM Banner"),
    GAM_INTERSTITIAL_BANNER("GAM Interstitial Banner"),
    GAM_REWARD_VIDEO("GAM Rewarded Video"),

    YANGO_BANNER("Yandex Banner"),

    MULTI_AD_BANNER("Multi Ad Banner")
}

class MainActivity : AppCompatActivity() {

    companion object {

        private val yangoNetworks = arrayListOf(
            Network( "demo-banner-yandex"),
            Network("demo-banner-admob"),
            Network("demo-banner-applovin"),
            Network( "demo-banner-chartboost")
        )
    }

    private var adBannerFormat: BannerFormat? = null
    private lateinit var binding: ActivityMainBinding
    private val adWrapperView: ViewGroup get() = binding.adLayout

    private var bannerAd: BannerAdView? = null
    private var currentAdUnitId: String? = null
    private var bannerAdSize: BannerAdSize? = null

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

            BannerFormat.VIDEO_IN_BANNER -> setupInBannerVideoBanner(
                configId = "toffee_bumper_ads_1920x1080v",
                adSize = AdSize(1920, 1080),
                adUnitId = "/23081467975/toffee_bangladesh/toffee_bumper_ads_1920x1080v"
            )

            BannerFormat.VIDEO_INTERSTITIAL -> setupInterstitialVideo(
                configId = "toffee_bumper_ads_1920x1080v",
                adSize = AdSize(1920, 1080),
                adUnitId = "/23081467975/toffee_bangladesh/toffee_bumper_ads_1920x1080v"
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

            BannerFormat.GAM_BANNER -> setupGamBanner(
                configId = "prebid-ita-banner-320-50",
                adSize = AdSize(358, 200),
                adUnitId = "/23081467975/beeline_uzbekistan_android/beeline_uz_android_universal_358x200_test2"
            )

            BannerFormat.GAM_INTERSTITIAL_BANNER -> setupGamInterstitialBanner(
                gamAdUnitId = "ca-app-pub-3940256099942544/1033173712",
                configId = "test_interstitial",
                adSize = AdSize(100, 100)
            )

            BannerFormat.GAM_REWARD_VIDEO ->
                setupGamRewardVideo(
                    gamAdUnitId = "/21952429235,23020124565/be_org.prebid.veondemo_app/be_org.prebid.veondemo_appopen",
                    configId = "prebid-ita-video-rewarded-320-480"
                )

            BannerFormat.YANGO_BANNER -> {
                configureBannerAdSize()
                setupYangoBanner(yangoNetworks[0].adUnitId)
            }

            BannerFormat.MULTI_AD_BANNER -> {
                setupMultiAdBanner(
                    adSize = AdSize(320, 50),
                    configId = "",//""beeline_uz_android_manual_veon_test_320x50",
                    gamAdUnitId = "/23081467975/beeline_uzbekistan_android/beeline_uz_android_manual_veon_test_320x50",
                    yandexAdUnitId = "sds",//yangoNetworks[0].adUnitId,
                    priorityOrder = listOf(AdPlatformSDK.PREBID, AdPlatformSDK.GAM, AdPlatformSDK.YANDEX),
                    autoRefreshDelay = 30
                )
            }
        }
    }

    private fun removeAllBannerSlots() {
        adWrapperView.removeAllViewsInLayout()
        binding.banner320x50.removeAllViewsInLayout()
        binding.banner300x250.removeAllViewsInLayout()
    }

    private fun configureBannerAdSize() {
        binding.mainContainer.post {
            binding.mainContainer.requestLayout()
        }

        binding.mainContainer.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                binding.mainContainer.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val screenHeight = resources.displayMetrics.run { heightPixels / density }.roundToInt()
                // Calculate the width of the ad, taking into account the padding in the ad container.
                val adWidthPixels = binding.mainContainer.width
                val adWidth = (adWidthPixels / resources.displayMetrics.density).roundToInt()
                val maxAdHeight = screenHeight / 3
                bannerAdSize = BannerAdSize.inlineSize(this@MainActivity, adWidth, maxAdHeight)
            }
        })
    }

    private fun setupYangoBanner(configId: String) {
        bannerAdSize = BannerAdSize.inlineSize(this@MainActivity, 300, 250)
        bannerAdSize?.let { bannerAdSize ->
            val selectedAdUnitId = configId
            if (currentAdUnitId != selectedAdUnitId) {
                destroyBanner()
                createYangoBanner(selectedAdUnitId, bannerAdSize)
            }
            val adRequest = AdRequest.Builder()
                //.setParameters(getRequestParameters())
                .setParameters(emptyMap())
                .build()
            bannerAd?.loadAd(adRequest)
        }
    }

    private fun createYangoBanner(adUnitId: String, bannerAdSize: BannerAdSize) {
        bannerAd = BannerAdView(this).apply {
            id = binding.banner.id
            setAdUnitId(adUnitId)
            currentAdUnitId = adUnitId
            setAdSize(bannerAdSize)
            setBannerAdEventListener(object : BannerAdEventListener {
                override fun onAdLoaded() {
                    // If this callback occurs after the activity is destroyed, you
                    // must call destroy and return or you may get a memory leak.
                    // Note `isDestroyed` is a method on Activity.
                    if (isDestroyed) {
                        bannerAd?.destroy()
                        return
                    }
                }

                override fun onAdFailedToLoad(adRequestError: AdRequestError) {
                    // Ad failed to load with AdRequestError.
                    // Attempting to load a new ad from the onAdFailedToLoad() method is strongly discouraged.
                }

                override fun onAdClicked() {
                    // Called when a click is recorded for an ad.
                }

                override fun onLeftApplication() {
                    // Called when user is about to leave application (e.g., to go to the browser), as a result of clicking on the ad.
                }

                override fun onReturnedToApplication() {
                    // Called when user returned to application after click.
                }

                override fun onImpression(impressionData: ImpressionData?) {
                    // Called when an impression is recorded for an ad.
                }
            })
        }
        val params = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        }
        adWrapperView.addView(bannerAd, params)
    }

    private fun destroyBanner() {
        bannerAd?.let {
            it.destroy()
            adWrapperView.removeAllViewsInLayout()
        }
        bannerAd = null
        currentAdUnitId = null
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

    private fun setupInBannerVideoBanner(configId: String, adSize: AdSize, adUnitId: String) {
        val eventHandler = GamBannerEventHandler(this, adUnitId, adSize)
        val adView = BannerView(this, configId, eventHandler)
        adView.setAutoRefreshDelay(30)

        // For Video
        adView.videoPlacementType = VideoPlacementType.IN_BANNER

        adWrapperView.addView(adView)
        adView.loadAd()
    }

    private fun setupInterstitialVideo(configId: String, adSize: AdSize, adUnitId: String) {
        val eventHandler = GamInterstitialEventHandler(this, adUnitId)
        val adUnit = InterstitialAdUnit(this, configId, EnumSet.of(AdUnitFormat.VIDEO), eventHandler)
        adUnit.setInterstitialAdUnitListener(object :
            InterstitialAdUnitListener {
            override fun onAdLoaded(adUnit: InterstitialAdUnit?) {
                adUnit?.show()
            }

            override fun onAdDisplayed(adUnit: InterstitialAdUnit?) {}
            override fun onAdFailed(adUnit: InterstitialAdUnit?, exception: AdException?) {}
            override fun onAdClicked(adUnit: InterstitialAdUnit?) {}
            override fun onAdClosed(adUnit: InterstitialAdUnit?) {}
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
            setAutoRefreshDelay(30)
            setBannerListener(defaultBannerListener())
            loadAd()
        }

        slot.addView(adUnit)
    }

    private fun setupGamSimpleBanner(configId: String, adSize: AdSize, adUnitId: String) {
        val eventHandler = GamBannerEventHandler(this, adUnitId, adSize)
        val bannerView = BannerView(this, configId, eventHandler)
        bannerView.setAutoRefreshDelay(30)
        adWrapperView.addView(bannerView)
        bannerView.loadAd()

        bannerView.setBannerListener(object : BannerViewListener {
            override fun onAdLoaded(bannerView: BannerView?) = showToast("onAdLoaded")
            override fun onAdDisplayed(bannerView: BannerView?) = showToast("onAdDisplayed")
            override fun onAdFailed(bannerView: BannerView?, exception: AdException?) = showToast("onAdFailed")
            override fun onAdClicked(bannerView: BannerView?) = showToast("onAdClicked")
            override fun onAdUrlClicked(url: String?) = showToast("onAdUrlClicked")
            override fun onAdClosed(bannerView: BannerView?) = showToast("onAdClosed")
        })
    }

    private fun setupMultiAdBanner(adSize: AdSize,
                                   configId: String,
                                   gamAdUnitId: String,
                                   yandexAdUnitId: String,
                                   autoRefreshDelay: Int) {

        val adLoader = MultiAdLoader(
            context = this,
            adSize = adSize,
            configId = configId,
            gamAdUnitId = gamAdUnitId,
            yandexAdUnitId = yandexAdUnitId,
            autoRefreshDelay = autoRefreshDelay
        )

        adLoader.setListener(object : MultiAdLoaderListener {
            override fun onAdLoaded(adView: View, sdk: AdPlatformSDK) {
               // adWrapperView.removeAllViews()
                adWrapperView.addView(adView)
                showToast("Ad loaded from: ${sdk.name}")
            }

            override fun onAdFailed(bannerView: BannerView?, error: String?, sdk: AdPlatformSDK?) {
                val errorMsg = error ?: "Unknown error"
                val sdkName = sdk?.name ?: "unknown SDK"
                showToast("Ad failed ($sdkName): $errorMsg")
            }

            override fun onAdClicked(bannerView: BannerView?, sdk: AdPlatformSDK) {
                showToast("Ad clicked (${sdk.name})")
            }

            override fun onLeftApplication(sdk: AdPlatformSDK) {
                showToast("Left app (${sdk.name})")
            }

            override fun onReturnedToApplication(sdk: AdPlatformSDK) {
                showToast("Returned to app (${sdk.name})")
            }

            override fun onImpression(impressionData: ImpressionData?, sdk: AdPlatformSDK) {
                showToast("Impression tracked (${sdk.name})")
            }

            override fun onAdUrlClicked(url: String?, sdk: AdPlatformSDK) {
                showToast("URL clicked (${sdk.name}): ${url ?: "no url"}")
            }

            override fun onAdClosed(bannerView: BannerView?, sdk: AdPlatformSDK) {
                showToast("Ad closed (${sdk.name})")
            }

            override fun onAdDisplayed(bannerView: BannerView?, sdk: AdPlatformSDK) {
                showToast("Ad displayed (${sdk.name})")
            }

            override fun onAdOpened(sdk: AdPlatformSDK) {
                showToast("Ad opened (${sdk.name})")
            }
        })

        adLoader.loadAd()
    }

    private fun setupGamBanner(configId: String, adSize: AdSize, adUnitId: String) {
        val adUnit = BannerAdUnit(configId, adSize.width, adSize.height).apply {
            bannerParameters = BannerParameters().apply {
                api = listOf(Signals.Api.MRAID_3, Signals.Api.OMID_1)
            }
            setAutoRefreshInterval(30)
        }

        val adView = AdManagerAdView(this)
        adView.adUnitId = adUnitId
        adView.setAdSizes(com.google.android.gms.ads.AdSize(320, 50))

        adView.adListener = object : AdListener() {
            override fun onAdClicked() = showToast("onAdClicked")
            override fun onAdClosed() = showToast("onAdClosed")
            override fun onAdFailedToLoad(adError: LoadAdError) = showToast("onAdFailedToLoad")
            override fun onAdImpression() = showToast("onAdImpression")
            override fun onAdOpened() = showToast("onAdOpened")
            override fun onAdLoaded() {
                showToast("onAdLoaded")
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
            }
        }

        val request = AdManagerAdRequest.Builder().build()
        adUnit.fetchDemand(request) { adView.loadAd(request) }
        adWrapperView.addView(adView)
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

