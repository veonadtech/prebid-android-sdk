package org.prebid.mobile.logging

import org.json.JSONObject
import org.prebid.mobile.LogUtil

/**
 * Represents a single log entry to be sent to server
 */
data class LogEntry(
    val status: GamStatus,
    val message: String,
    val timestamp: String,
    val accountId: String,
    val appVersion: String,
    val adId: String,
    val os: String,
) {

    fun toJson(): JSONObject {
        val json = JSONObject()
        try {
            json.put("status", status.name)
            json.put("message", message)
            json.put("time", timestamp)
            json.put("accountId", accountId)
            json.put("appVersion", appVersion)
            json.put("os", os)
        } catch (e: Exception) {
            LogUtil.error("LogEntry", "Error converting to JSON: ${e.message}")
        }
        return json
    }

}