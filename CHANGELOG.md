# CHANGELOG

# 0.4.3
## Changed
* Interstitial container size is now derived from the bid response (`bid.w`/`bid.h`) instead of 
* always filling the screen, capped so it never exceeds the display's size

# 0.4.2
## Added
* Ad-request telemetry: GAM, Yandex and Prebid ad requests now emit a `REQUESTED`
(`SdkAdStatus.REQUESTED`) log event, complementing the existing post-response events. Prebid auction
requests are logged from `BidLoader` (banner and interstitial, including auto-refresh; rewarded is
excluded); GAM and Yandex requests are logged from the legacy and Next-Gen banner/interstitial
multi-ad loaders, and GAM requests are also logged from the GAM banner/interstitial event handlers

# 0.4.1
## Fixed
* Update BannerView to log the specific exception message instead of a hardcoded string

# 0.4.0
## Added
* Multi-loader support for the Next-Gen GMA SDK: `MultiBannerLoaderNextGenGam` and
`MultiInterstitialAdLoaderNextGenGam` race Prebid, GAM (Next-Gen GMA SDK) and Yandex by the
configured priority order, mirroring the existing legacy GAM multi-loaders
## Changed
* Renamed the multi-ad loaders to specify the backing SDK: `MultiBannerLoader` →
`MultiBannerLoaderLegacyGam`, `MultiInterstitialAdLoader` → `MultiInterstitialAdLoaderLegacyGam`,
`MultiBannerLoaderNextGen` → `MultiBannerLoaderNextGenGam`, `MultiInterstitialAdLoaderNextGen` →
`MultiInterstitialAdLoaderNextGenGam`
* Renamed the Next-Gen demo app package to `org.prebid.mobile.prebidveonnextgendemo` and the app
name to "Prebid Veon Next Gen SDK Demo"

# 0.3.2
## Added
* Unit tests for `MultiBannerLoader` and `MultiInterstitialAdLoader` (67 tests covering priority order, fallback, lazy Prebid, listener routing, single-fire guards, and lifecycle)
## Changed
* `MultiBannerLoader` opened for testability: class is now `open` and the Yandex banner-size resolution is exposed via a `protected open` helper (no behavior change for existing callers)

# 0.3.1
## Changed
* Yandex and GAM ads are requested even if the Prebid SDK is not initialized

# 0.3.0
## Fixed
* Make NativeDataAsset len optional like iOS
* hb_cache_id_local is not added to targetingKeywords if adObject is null
* Fix bar layout params is null 
* Fix Adm native wrapper parsing
* Exception during looking for cache
* Fix Unit tests
## Changed
* Send ifa_type for IFA 
* Resume refreshing for Mediation banner 
* Readable exceptions and useless logs
* ORTB config for ad unit level (Aligns with iOS implementation)
* Reusable rendering API banner (removes the destruction of Prebid WebView when it is detached 
from the screen. So now the Prebid banner can be used in the RecyclerView and can be reused 
many times to show the advertisement faster.)
* minSdkVersion upgraded to 23
* GAM SDK upgraded to 25.1.0

# 0.2.1
## Fixed
* Fixed null safety for configId in BannerView
## Changed
* The new version of SDK (without the Yandex partners' repositories) is renamed to Yandex SDK
* Yandex SDK version bumped to 7.18.5

# 0.2.0
## Added
* Added useExternalBrowser option (allows opening links in an external browser instead of WebView)

# 0.1.2.2
## Fixed
* Set browser-like User-Agent for redirect request

# 0.1.2.1
## Fixed
* Yango SDK version downgraded to 7.9

# 0.1.2
## Fixed
* Added necessary files for starting unit tests. Changed test of SDK initialization.
*  Set browser-like User-Agent for redirect request
* Yango SDK version downgraded

# 0.1.1
## Changed
* hotfix: SDK version name changed

# 0.1.0
## Changed
* SdkLog race condition bug fixed

# 0.0.9
## Changed
* GAM version upgraded
* Resume auto refresh implemented

# 0.0.8
## Changed
* Second fragment separator in URL fixed

# 0.0.7.9
## Changed
* Device IP bug fixed

# 0.0.7.8
## Changed
* Redirect bug fixed
* Interstitial Rendering method bug fixed

## 0.0.7.7
# Migration
* Conflict ListenableFuture with CameraX fixed
* Fixed bug with SDK Log
* Fixed bug with Guava

## 0.0.7.6
# Migration
* ExoPlayer migrated to Media3
* targetSdkVersion upgraded to 35

## 0.0.7.5
# Added
* Added logging for Prebid and Yango events
* Added config URL for loading SDK priority

## 0.0.7.4
# Added
* Added SDK priority list

## 0.0.7.3
# Fixed
* Fixed bug with changing ver com.google.guava:listenablefuture

## 0.0.7.2
# Added
* Added MultiBanner and MultiInterstitial methods (waterfall)

## 0.0.7
# Added
* Synced all changes from open-source Prebid Mobile

# Changed
* Demo app optimized for better testing

## 0.0.6
# Changed
* Track impression disabled for Auction Banner
# Added
* Yandex integration (waterfall with SDK priority list)

# Added
* Logging Prebid events

## 0.0.5.1
# Changed
* Track manual impression disabled
* Fixed publishing artifacts

# Added
* AAID added
* Logging for GAM events

* Initial release