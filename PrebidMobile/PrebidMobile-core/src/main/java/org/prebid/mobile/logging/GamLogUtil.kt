package org.prebid.mobile.logging

import android.util.Log
import androidx.annotation.Size

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
     * Prints a message with VERBOSE priority and default GAM_TAG
     */
    @JvmStatic
    fun verbose(message: String) {
        verbose(GAM_TAG, message)
    }

    /**
     * Prints a message with DEBUG priority and default GAM_TAG
     */
    @JvmStatic
    fun debug(message: String) {
        debug(GAM_TAG, message)
    }

    /**
     * Prints a message with INFO priority and default GAM_TAG
     */
    @JvmStatic
    fun info(message: String) {
        info(GAM_TAG, message)
    }

    /**
     * Prints a message with WARNING priority and default GAM_TAG
     */
    @JvmStatic
    fun warning(message: String) {
        warning(GAM_TAG, message)
    }

    /**
     * Prints a message with ERROR priority and default GAM_TAG
     */
    @JvmStatic
    fun error(message: String) {
        error(GAM_TAG, message)
    }

    /**
     * Prints a message with VERBOSE priority.
     */
    @JvmStatic
    fun verbose(@Size(max = 23) tag: String, msg: String) {
        print(VERBOSE, tag, msg)
    }

    /**
     * Prints a message with DEBUG priority.
     */
    @JvmStatic
    fun debug(@Size(max = 23) tag: String, msg: String) {
        print(DEBUG, tag, msg)
    }

    /**
     * Prints a message with INFO priority.
     */
    @JvmStatic
    fun info(@Size(max = 23) tag: String, msg: String) {
        print(INFO, tag, msg)
    }

    /**
     * Prints a message with WARN priority.
     */
    @JvmStatic
    fun warning(@Size(max = 23) tag: String, msg: String) {
        print(WARN, tag, msg)
    }

    /**
     * Prints a message with ERROR priority.
     */
    @JvmStatic
    fun error(@Size(max = 23) tag: String, msg: String) {
        print(ERROR, tag, msg)
    }

    /**
     * Prints a message with ASSERT priority.
     */
    @JvmStatic
    fun wtf(@Size(max = 23) tag: String, msg: String) {
        print(ASSERT, tag, msg)
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
    private fun print(messagePriority: Int, tag: String?, message: String?) {
        if (tag == null || message == null) {
            return
        }
        val finalTag = getTagWithBase(tag)
        Log.println(messagePriority, finalTag, message)

        // Send to server
        LogServerSender.getInstance().sendLog(messagePriority, finalTag, message)
    }

    /**
     * Helper method to add Prebid tag to logging messages.
     */
    private fun getTagWithBase(tag: String): String {
        val result = StringBuilder()

        val prefix = "GAM"
        if (tag.startsWith(prefix)) {
            result.append(tag)
        } else {
            result.append(prefix).append(tag)
        }

        return if (result.length > 23) {
            result.substring(0, 22)
        } else {
            result.toString()
        }
    }
}