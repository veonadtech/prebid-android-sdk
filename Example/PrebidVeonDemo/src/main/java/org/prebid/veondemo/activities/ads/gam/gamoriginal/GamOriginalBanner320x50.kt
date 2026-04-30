package org.prebid.veondemo.activities.ads.gam.gamoriginal

import android.os.Bundle
import android.view.ViewGroup
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView
import org.prebid.mobile.BannerAdUnit
import org.prebid.mobile.addendum.AdViewUtils
import org.prebid.mobile.addendum.PbFindSizeError
import org.prebid.veondemo.activities.BaseAdActivity

class GamOriginalBanner320x50 : BaseAdActivity() {

    companion object {
        const val AD_UNIT_ID = "/21775744923/example/fixed-size-banner"
        const val WIDTH = 320
        const val HEIGHT = 50
    }

    private var adUnit: BannerAdUnit? = null
    private var adView: AdManagerAdView? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createAd()
    }

    private fun createAd() {
        val adView = AdManagerAdView(this)
        adView.adUnitId = AD_UNIT_ID
        adView.setAdSizes(AdSize(WIDTH, HEIGHT))
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                super.onAdLoaded()
                AdViewUtils.findPrebidCreativeSize(adView, object : AdViewUtils.PbFindSizeListener {
                    override fun success(width: Int, height: Int) {
                        adView.setAdSizes(AdSize(width, height))
                    }

                    override fun failure(error: PbFindSizeError) {}
                })

            }
        }
        adWrapperView.addView(adView)

        val request = AdManagerAdRequest.Builder().build()
        adView.loadAd(request)
    }

    /**
     * Optional. Sets additional parameters.
     */
    private fun setOpenRtbConfig() {
        adUnit?.impOrtbConfig = """
            {
              "bidfloor": 0.01,
              "banner": {
                "battr": [1,2,3,4]
              }
            }
        """
    }


    override fun onDestroy() {
        super.onDestroy()
        adUnit?.destroy()

        val parentView = adView?.parent
        if (parentView is ViewGroup) {
            parentView.removeView(adView)
        }

        // Destroy the banner ad resources.
        adView?.destroy()

        // Drop reference to the banner ad.
        adView = null
    }

}