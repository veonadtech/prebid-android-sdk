# CHANGELOG

# 0.4.1
* Fetch changes from prebid SDK

# 0.3.2
## Changed
* Enables video player click for rewarded ads with clickthrough URL

# 0.3.1
## Changed
* GAM ad is requested even if the Prebid SDK is not initialized

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
* minSdkVersion upgraded to 21
* Media3 migrated to ExoPlayer

# 0.2.1
## Fixed
* Fixed null safety for configId in BannerView

# 0.2.0
## Added
* Added useExternalBrowser option (allows opening links in an external browser instead of WebView)


# 0.1.2
## Fixed
* Added necessary files for starting unit tests. Changed test of SDK initialization.
*  Set browser-like User-Agent for redirect request

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

## 0.0.7-yango
# Added
* Synced all changes from open-source Prebid Mobile

## 0.0.7
# Added
* Synced all changes from open-source Prebid Mobile

# Changed
* Demo app optimized for better testing

## 0.0.6-yango
# Added
* Yandex integration (waterfall with SDK priority list)

## 0.0.6
# Changed
* Track impression disabled for Auction Banner

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