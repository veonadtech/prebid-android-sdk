package org.prebid.mobile.rendering.utils.url

import android.os.Build
import android.webkit.WebView
import org.prebid.mobile.LogUtil

/**
 * Utility class for generating browser-like User-Agent strings.
 */
object BrowserUserAgentGenerator {
    private const val TAG = "UserAgentGenerator"

    // Chrome versions mapping (updated February 2026)
    private const val CHROME_ANDROID_16 = "133.0.6943.137"
    private const val CHROME_ANDROID_15 = "130.0.6723.86"
    private const val CHROME_ANDROID_14 = "126.0.6478.122"
    private const val CHROME_ANDROID_13 = "119.0.6045.66"
    private const val CHROME_ANDROID_12 = "108.0.5359.128"
    private const val CHROME_ANDROID_11 = "95.0.4638.74"
    private const val CHROME_ANDROID_10 = "89.0.4389.105"
    private const val CHROME_ANDROID_9 = "81.0.4044.138"
    private const val CHROME_ANDROID_8 = "71.0.3578.99"
    private const val CHROME_ANDROID_7 = "61.0.3163.98"
    private const val CHROME_ANDROID_6 = "51.0.2704.81"
    private const val CHROME_ANDROID_5 = "44.0.2403.119"

    private const val DEFAULT_ANDROID_VERSION = "10"
    private const val DEFAULT_DEVICE_MODEL = "Mobile"
    private const val DEFAULT_BUILD_ID = "KOT49H"
    private const val DEFAULT_CHROME_VERSION = CHROME_ANDROID_10

    private const val FALLBACK_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10; Mobile; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/89.0.4389.105 Mobile Safari/537.36"

    /**
     * Generates browser-like User-Agent using WebView version if available.
     *
     * @return Realistic WebView User-Agent string
     */
    @JvmStatic
    fun generateBrowserUserAgent(): String {
        return try {
            val androidVersion = getAndroidVersion()
            val deviceModel = getDeviceModel()
            val buildId = getBuildId()

            val chromeVersion = getActualChromeVersion() ?: getChromeVersionForAndroid(androidVersion).also {
                LogUtil.debug(TAG, "Using Chrome version mapping for Android $androidVersion: $it")
            }.alsoIfNotNull {
                LogUtil.debug(TAG, "Using actual Chrome version from system: $it")
            }

            "Mozilla/5.0 (Linux; Android $androidVersion; $deviceModel Build/$buildId; wv) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Version/4.0 " +
                    "Chrome/$chromeVersion Mobile Safari/537.36"
        } catch (e: Exception) {
            LogUtil.error(TAG, "Error generating User-Agent: ${e.message}")
            FALLBACK_USER_AGENT
        }
    }

    /**
     * Quick method to get browser User-Agent in one line.
     *
     * @return Realistic WebView User-Agent string
     */
    @JvmStatic
    fun getBrowserUserAgent(): String = generateBrowserUserAgent()

    /**
     * Attempts to get actual Chrome/WebView version from system (Android 8.0+).
     *
     * @return Chrome version string or null if not available
     */
    @JvmStatic
    fun getActualChromeVersion(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return try {
                WebView.getCurrentWebViewPackage()?.versionName?.takeIf { it.isNotEmpty() }
            } catch (e: Exception) {
                LogUtil.debug(TAG, "Could not get WebView package version: ${e.message}")
                null
            }
        }
        return null
    }

    /**
     * Safely gets Android version.
     *
     * @return Android version string or default value
     */
    @JvmStatic
    fun getAndroidVersion(): String {
        return try {
            Build.VERSION.RELEASE?.takeIf { it.isNotEmpty() } ?: DEFAULT_ANDROID_VERSION
        } catch (_: Exception) {
            DEFAULT_ANDROID_VERSION
        }
    }

    /**
     * Safely gets device model with whitespace cleaned.
     *
     * @return Cleaned device model or default value
     */
    @JvmStatic
    fun getDeviceModel(): String {
        return try {
            Build.MODEL?.takeIf { it.isNotEmpty() }
                ?.replace(" ", "_")
                ?.replace("\t", "_")
                ?.replace("\n", "_")
                ?.replace("\r", "_")
                ?: DEFAULT_DEVICE_MODEL
        } catch (_: Exception) {
            DEFAULT_DEVICE_MODEL
        }
    }

    /**
     * Safely gets Build ID.
     *
     * @return Build ID string or default value
     */
    @JvmStatic
    fun getBuildId(): String {
        return try {
            Build.ID?.takeIf { it.isNotEmpty() } ?: DEFAULT_BUILD_ID
        } catch (_: Exception) {
            DEFAULT_BUILD_ID
        }
    }

    /**
     * Returns Chrome version corresponding to Android version.
     *
     * @param androidVersion Android version string
     * @return Chrome version string
     */
    @JvmStatic
    fun getChromeVersionForAndroid(androidVersion: String): String {
        return try {
            val parts = androidVersion.split(".")
            if (parts.isNotEmpty()) {
                val majorVersion = try {
                    parts[0].toInt()
                } catch (_: NumberFormatException) {
                    return when {
                        androidVersion.contains("16") -> CHROME_ANDROID_16
                        androidVersion.contains("15") -> CHROME_ANDROID_15
                        androidVersion.contains("14") -> CHROME_ANDROID_14
                        androidVersion.contains("13") -> CHROME_ANDROID_13
                        androidVersion.contains("12") -> CHROME_ANDROID_12
                        androidVersion.contains("11") -> CHROME_ANDROID_11
                        androidVersion.contains("10") -> CHROME_ANDROID_10
                        androidVersion.contains("9") -> CHROME_ANDROID_9
                        androidVersion.contains("8") -> CHROME_ANDROID_8
                        androidVersion.contains("7") -> CHROME_ANDROID_7
                        androidVersion.contains("6") -> CHROME_ANDROID_6
                        androidVersion.contains("5") -> CHROME_ANDROID_5
                        else -> DEFAULT_CHROME_VERSION
                    }
                }

                return when (majorVersion) {
                    16 -> CHROME_ANDROID_16
                    15 -> CHROME_ANDROID_15
                    14 -> CHROME_ANDROID_14
                    13 -> CHROME_ANDROID_13
                    12 -> CHROME_ANDROID_12
                    11 -> CHROME_ANDROID_11
                    10 -> CHROME_ANDROID_10
                    9 -> CHROME_ANDROID_9
                    8 -> CHROME_ANDROID_8
                    7 -> CHROME_ANDROID_7
                    6 -> CHROME_ANDROID_6
                    5 -> CHROME_ANDROID_5
                    else -> if (majorVersion > 16) CHROME_ANDROID_16 else CHROME_ANDROID_5
                }
            }
            DEFAULT_CHROME_VERSION
        } catch (_: Exception) {
            LogUtil.debug(TAG, "Failed to map Android version: $androidVersion")
            DEFAULT_CHROME_VERSION
        }
    }

    /**
     * Utility extension function for conditional logging.
     */
    private inline fun <T> T?.alsoIfNotNull(block: (T) -> Unit): T? {
        if (this != null) {
            block(this)
        }
        return this
    }
}
