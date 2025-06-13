package org.prebid.mobile.logging

import android.util.Log
import androidx.annotation.Size
import org.prebid.mobile.api.data.AdFormat

object GamLogUtil {
    private const val GAM_TAG = "GAM"

    const val NONE = -1
    const val VERBOSE = Log.VERBOSE // 2
    const val DEBUG = Log.DEBUG // 3
    const val INFO = Log.INFO // 4
    const val WARN = Log.WARN // 5
    const val ERROR = Log.ERROR // 6
    const val ASSERT = Log.ASSERT // 7

    @JvmStatic
    var logLevel: Int = 0

    /**
     * Configure log server sending
     *
     * @param serverUrl URL of the log server endpoint
     * @param enabled   Whether to enable sending logs to server
     */
    @JvmStatic
    fun configureLogServer(serverUrl: String?, enabled: Boolean) {
        LogServerSender.getInstance().configure(serverUrl, enabled)
    }

    /**
     * Prints a message with INFO priority and default GAM_TAG
     */
    @JvmStatic
    fun info(message: String, gamStatus: GamStatus, adFormat: AdFormat, adUnitId: String) {
        info(GAM_TAG, message, gamStatus, adFormat, adUnitId)
    }

    /**
     * Prints a message with ERROR priority and default GAM_TAG
     */
    @JvmStatic
    fun error(message: String, adFormat: AdFormat, adUnitId: String) {
        error(GAM_TAG, message, adFormat, adUnitId)
    }

    /**
     * Prints a message with INFO priority.
     */
    @JvmStatic
    fun info(@Size(max = 23) tag: String, msg: String, gamStatus: GamStatus, adFormat: AdFormat, adUnitId: String) {
        log(INFO, tag, msg, gamStatus, adFormat, adUnitId)
    }

    /**
     * Prints a message with ERROR priority.
     */
    @JvmStatic
    fun error(@Size(max = 23) tag: String, msg: String, adFormat: AdFormat, adUnitId: String) {
        log(ERROR, tag, msg, GamStatus.FAILED, adFormat = adFormat, adUnitId)
    }

    /**
     * Prints a message with ERROR priority and exception.
     */
    @JvmStatic
    fun error(tag: String?, message: String?, throwable: Throwable) {
        if (tag == null || message == null) {
            return
        }
        Log.e(getTagWithBase(tag), message, throwable)
    }

    /**
     * Prints information with set priority. Every tag
     */
    private fun log(messagePriority: Int, tag: String?, message: String?, status: GamStatus, adFormat: AdFormat, adUnitId: String) {
        if (tag.isNullOrBlank() || message.isNullOrBlank()) return
        val finalTag = getTagWithBase(tag)
        Log.println(messagePriority, finalTag, message)

        // Send to server
        LogServerSender.getInstance().sendLog(
            status = status,
            message = message,
            adFormat = adFormat,
            adUnitId = adUnitId
        )
    }

    /**
     * Helper method to add Prebid tag to logging messages.
     */
    private fun getTagWithBase(tag: String): String {
        val fullTag = if (tag.startsWith(GAM_TAG)) tag else "$GAM_TAG$tag"
        return if (fullTag.length > 23) fullTag.take(22) else fullTag
    }
}