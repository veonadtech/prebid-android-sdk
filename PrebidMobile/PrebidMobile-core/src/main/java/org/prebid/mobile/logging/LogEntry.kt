package org.prebid.mobile.logging

import org.json.JSONObject
import org.prebid.mobile.LogUtil

/**
 * Represents a single log entry to be sent to server
 */
data class LogEntry(
    val level: Int,
    val message: String,
    val tag: String,
    val timestamp: Long,
    val accountId: String,
    val device: String
//    val status: String
) {

    fun toJson(): JSONObject {
        val json = JSONObject()
        try {
            json.put("level", getLevelString(level))
            json.put("message", message)
            json.put("tag", tag)
            json.put("timestamp", timestamp)
            json.put("accountId", accountId)
            json.put("device", device)
//            json.put("status", status)
        } catch (e: Exception) {
            LogUtil.error("LogEntry", "Error converting to JSON: ${e.message}")
        }
        return json
    }

    private fun getLevelString(level: Int): String = when (level) {
        GamLogUtil.VERBOSE -> "VERBOSE"
        GamLogUtil.DEBUG -> "DEBUG"
        GamLogUtil.INFO -> "INFO"
        GamLogUtil.WARN -> "WARN"
        GamLogUtil.ERROR -> "ERROR"
        GamLogUtil.ASSERT -> "ASSERT"
        else -> "UNKNOWN"
    }
}