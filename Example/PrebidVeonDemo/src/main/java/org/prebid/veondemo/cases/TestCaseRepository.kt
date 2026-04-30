package org.prebid.veondemo.cases

import org.prebid.veondemo.R
import org.prebid.veondemo.activities.ads.gam.gamoriginal.GamOriginalBanner320x50
import org.prebid.veondemo.activities.ads.gam.original.GamOriginalApiDisplayBanner300x250Activity
import org.prebid.veondemo.activities.ads.gam.original.GamOriginalApiDisplayBanner320x50Activity
import org.prebid.veondemo.activities.ads.gam.original.GamOriginalApiDisplayBannerMultiSizeActivity
import org.prebid.veondemo.activities.ads.gam.original.GamOriginalApiDisplayInterstitialActivity
import org.prebid.veondemo.activities.ads.gam.original.GamOriginalApiInStreamActivity
import org.prebid.veondemo.activities.ads.gam.original.GamOriginalApiMultiformatBannerActivity
import org.prebid.veondemo.activities.ads.gam.original.GamOriginalApiMultiformatBannerVideoNativeInAppActivity
import org.prebid.veondemo.activities.ads.gam.original.GamOriginalApiMultiformatBannerVideoNativeStylesActivity
import org.prebid.veondemo.activities.ads.gam.original.GamOriginalApiMultiformatInterstitialActivity
import org.prebid.veondemo.activities.ads.gam.original.GamOriginalApiNativeInAppActivity
import org.prebid.veondemo.activities.ads.gam.original.GamOriginalApiNativeStylesActivity
import org.prebid.veondemo.activities.ads.gam.original.GamOriginalApiVideoBannerActivity
import org.prebid.veondemo.activities.ads.gam.original.GamOriginalApiVideoInterstitialActivity
import org.prebid.veondemo.activities.ads.gam.original.GamOriginalApiVideoRewardedActivity
import org.prebid.veondemo.activities.ads.gam.rendering.GamRenderingApiDisplayBanner320x50Activity
import org.prebid.veondemo.activities.ads.gam.rendering.GamRenderingApiDisplayInterstitialActivity
import org.prebid.veondemo.activities.ads.gam.rendering.GamRenderingApiNativeActivity
import org.prebid.veondemo.activities.ads.gam.rendering.GamRenderingApiVideoBannerActivity
import org.prebid.veondemo.activities.ads.gam.rendering.GamRenderingApiVideoInterstitialActivity
import org.prebid.veondemo.activities.ads.gam.rendering.GamRenderingApiVideoRewardedActivity
import org.prebid.veondemo.activities.ads.inapp.InAppDisplayBanner320x50Activity
import org.prebid.veondemo.activities.ads.inapp.InAppDisplayBannerMraidExpandActivity
import org.prebid.veondemo.activities.ads.inapp.InAppDisplayBannerMraidResizeActivity
import org.prebid.veondemo.activities.ads.inapp.InAppDisplayBannerMraidResizeWithErrorsActivity
import org.prebid.veondemo.activities.ads.inapp.InAppDisplayBannerMultiSizeActivity
import org.prebid.veondemo.activities.ads.inapp.InAppDisplayInterstitialActivity
import org.prebid.veondemo.activities.ads.inapp.InAppNativeActivity
import org.prebid.veondemo.activities.ads.inapp.InAppVideoBannerActivity
import org.prebid.veondemo.activities.ads.inapp.InAppVideoInterstitialActivity
import org.prebid.veondemo.activities.ads.inapp.InAppVideoInterstitialMultiFormatActivity
import org.prebid.veondemo.activities.ads.inapp.InAppVideoInterstitialWithEndCardActivity
import org.prebid.veondemo.activities.ads.inapp.InAppVideoRewardedActivity
import org.prebid.veondemo.activities.ads.multi.MultiBannerActivity
import org.prebid.veondemo.activities.ads.multi.MultiInterstitialAdActivity

object TestCaseRepository {

    lateinit var lastTestCase: TestCase

    fun getList() = arrayListOf(
        /* GAM Original API without Prebid */
        TestCase(
            R.string.gam_original_display_banner_320x50_without_prebid,
            AdFormat.DISPLAY_BANNER,
            IntegrationKind.GAM_ORIGINAL_WITHOUT_PREBID,
            GamOriginalBanner320x50 ::class.java
        ),
        /* GAM Original API */
        TestCase(
            R.string.gam_original_display_banner_320x50,
            AdFormat.DISPLAY_BANNER,
            IntegrationKind.GAM_ORIGINAL,
            GamOriginalApiDisplayBanner320x50Activity::class.java,
        ),
        TestCase(
            R.string.gam_original_display_banner_300x250,
            AdFormat.DISPLAY_BANNER,
            IntegrationKind.GAM_ORIGINAL,
            GamOriginalApiDisplayBanner300x250Activity::class.java,
        ),
        TestCase(
            R.string.gam_original_display_banner_multi_size,
            AdFormat.DISPLAY_BANNER,
            IntegrationKind.GAM_ORIGINAL,
            GamOriginalApiDisplayBannerMultiSizeActivity::class.java,
        ),
        TestCase(
            R.string.gam_original_video_banner,
            AdFormat.VIDEO_BANNER,
            IntegrationKind.GAM_ORIGINAL,
            GamOriginalApiVideoBannerActivity::class.java,
        ),
        TestCase(
            R.string.gam_original_multiformat_banner,
            AdFormat.MULTIFORMAT,
            IntegrationKind.GAM_ORIGINAL,
            GamOriginalApiMultiformatBannerActivity::class.java,
        ),
        TestCase(
            R.string.gam_original_multiformat_banner_video_native_in_app,
            AdFormat.MULTIFORMAT,
            IntegrationKind.GAM_ORIGINAL,
            GamOriginalApiMultiformatBannerVideoNativeInAppActivity::class.java,
        ),
        TestCase(
            R.string.gam_original_multiformat_banner_video_native_styles,
            AdFormat.MULTIFORMAT,
            IntegrationKind.GAM_ORIGINAL,
            GamOriginalApiMultiformatBannerVideoNativeStylesActivity::class.java,
        ),
        TestCase(
            R.string.gam_original_display_interstitial,
            AdFormat.DISPLAY_INTERSTITIAL,
            IntegrationKind.GAM_ORIGINAL,
            GamOriginalApiDisplayInterstitialActivity::class.java,
        ),
        TestCase(
            R.string.gam_original_video_interstitial,
            AdFormat.VIDEO_INTERSTITIAL,
            IntegrationKind.GAM_ORIGINAL,
            GamOriginalApiVideoInterstitialActivity::class.java,
        ),
        TestCase(
            R.string.gam_original_multiformat_interstitial,
            AdFormat.MULTIFORMAT,
            IntegrationKind.GAM_ORIGINAL,
            GamOriginalApiMultiformatInterstitialActivity::class.java,
        ),
        TestCase(
            R.string.gam_original_video_rewarded,
            AdFormat.VIDEO_REWARDED,
            IntegrationKind.GAM_ORIGINAL,
            GamOriginalApiVideoRewardedActivity::class.java,
        ),
        TestCase(
            R.string.gam_original_video_in_stream,
            AdFormat.IN_STREAM_VIDEO,
            IntegrationKind.GAM_ORIGINAL,
            GamOriginalApiInStreamActivity::class.java,
        ),
        TestCase(
            R.string.gam_original_native_in_app,
            AdFormat.NATIVE,
            IntegrationKind.GAM_ORIGINAL,
            GamOriginalApiNativeInAppActivity::class.java,
        ),
        TestCase(
            R.string.gam_original_native_styles,
            AdFormat.NATIVE,
            IntegrationKind.GAM_ORIGINAL,
            GamOriginalApiNativeStylesActivity::class.java,
        ),

        /* GAM Rendering API */
        TestCase(
            R.string.gam_rendering_display_banner_320x50,
            AdFormat.DISPLAY_BANNER,
            IntegrationKind.GAM_RENDERING,
            GamRenderingApiDisplayBanner320x50Activity::class.java,
        ),
        TestCase(
            R.string.gam_rendering_video_banner,
            AdFormat.VIDEO_BANNER,
            IntegrationKind.GAM_RENDERING,
            GamRenderingApiVideoBannerActivity::class.java,
        ),
        TestCase(
            R.string.gam_rendering_display_interstitial,
            AdFormat.DISPLAY_INTERSTITIAL,
            IntegrationKind.GAM_RENDERING,
            GamRenderingApiDisplayInterstitialActivity::class.java,
        ),
        TestCase(
            R.string.gam_rendering_video_interstitial,
            AdFormat.VIDEO_INTERSTITIAL,
            IntegrationKind.GAM_RENDERING,
            GamRenderingApiVideoInterstitialActivity::class.java,
        ),
        TestCase(
            R.string.gam_rendering_video_rewarded,
            AdFormat.VIDEO_REWARDED,
            IntegrationKind.GAM_RENDERING,
            GamRenderingApiVideoRewardedActivity::class.java,
        ),
        TestCase(
            R.string.gam_rendering_native,
            AdFormat.NATIVE,
            IntegrationKind.GAM_RENDERING,
            GamRenderingApiNativeActivity::class.java,
        ),

        /* In-App (no ad server) */
        TestCase(
            R.string.in_app_display_banner_320x50,
            AdFormat.DISPLAY_BANNER,
            IntegrationKind.NO_AD_SERVER,
            InAppDisplayBanner320x50Activity::class.java,
        ),
        TestCase(
            R.string.in_app_display_banner_multi_size,
            AdFormat.DISPLAY_BANNER,
            IntegrationKind.NO_AD_SERVER,
            InAppDisplayBannerMultiSizeActivity::class.java,
        ),
        TestCase(
            R.string.in_app_display_banner_mraid_resize,
            AdFormat.DISPLAY_BANNER,
            IntegrationKind.NO_AD_SERVER,
            InAppDisplayBannerMraidResizeActivity::class.java,
        ),
        TestCase(
            R.string.in_app_display_banner_mraid_expand,
            AdFormat.DISPLAY_BANNER,
            IntegrationKind.NO_AD_SERVER,
            InAppDisplayBannerMraidExpandActivity::class.java,
        ),
        TestCase(
            R.string.in_app_display_banner_mraid_resize_errors,
            AdFormat.DISPLAY_BANNER,
            IntegrationKind.NO_AD_SERVER,
            InAppDisplayBannerMraidResizeWithErrorsActivity::class.java,
        ),
        TestCase(
            R.string.in_app_video_banner,
            AdFormat.VIDEO_BANNER,
            IntegrationKind.NO_AD_SERVER,
            InAppVideoBannerActivity::class.java,
        ),
        TestCase(
            R.string.in_app_display_interstitial,
            AdFormat.DISPLAY_INTERSTITIAL,
            IntegrationKind.NO_AD_SERVER,
            InAppDisplayInterstitialActivity::class.java,
        ),
        TestCase(
            R.string.in_app_video_interstitial,
            AdFormat.VIDEO_INTERSTITIAL,
            IntegrationKind.NO_AD_SERVER,
            InAppVideoInterstitialActivity::class.java,
        ),
        TestCase(
            R.string.in_app_video_interstitial_end_card,
            AdFormat.VIDEO_INTERSTITIAL,
            IntegrationKind.NO_AD_SERVER,
            InAppVideoInterstitialWithEndCardActivity::class.java,
        ),
        TestCase(
            R.string.in_app_video_interstitial_multi_format,
            AdFormat.MULTIFORMAT,
            IntegrationKind.NO_AD_SERVER,
            InAppVideoInterstitialMultiFormatActivity::class.java,
        ),
        TestCase(
            R.string.in_app_video_rewarded,
            AdFormat.VIDEO_REWARDED,
            IntegrationKind.NO_AD_SERVER,
            InAppVideoRewardedActivity::class.java,
        ),
        TestCase(
            R.string.in_app_native,
            AdFormat.NATIVE,
            IntegrationKind.NO_AD_SERVER,
            InAppNativeActivity::class.java,
        ),

        /* Multi ad */
        TestCase(
            R.string.multi_ad,
            AdFormat.DISPLAY_BANNER,
            IntegrationKind.MULTI_AD_SERVER,
            MultiBannerActivity::class.java
        ),
        TestCase(
            R.string.multi_ad_interstitial,
            AdFormat.DISPLAY_INTERSTITIAL,
            IntegrationKind.MULTI_AD_SERVER,
            MultiInterstitialAdActivity::class.java
        )
    )

}