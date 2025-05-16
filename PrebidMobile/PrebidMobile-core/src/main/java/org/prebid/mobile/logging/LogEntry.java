package org.prebid.mobile.logging;

import org.json.JSONObject;
import org.prebid.mobile.LogUtil;

/**
 * Represents a single log entry to be sent to server
 */
public class LogEntry {
    private final int level;
    private final String tag;
    private final String message;
    private final long timestamp;

    public LogEntry(int level, String tag, String message, long timestamp) {
        this.level = level;
        this.tag = tag;
        this.message = message;
        this.timestamp = timestamp;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("level", getLevelString(level));
            json.put("tag", tag);
            json.put("message", message);
            json.put("timestamp", timestamp);
        } catch (Exception e) {
            LogUtil.error("LogEntry", "Error converting to JSON: " + e.getMessage());
        }
        return json;
    }

    private String getLevelString(int level) {
        return switch (level) {
            case LogUtil.VERBOSE -> "VERBOSE";
            case LogUtil.DEBUG -> "DEBUG";
            case LogUtil.INFO -> "INFO";
            case LogUtil.WARN -> "WARN";
            case LogUtil.ERROR -> "ERROR";
            case LogUtil.ASSERT -> "ASSERT";
            default -> "UNKNOWN";
        };
    }
}