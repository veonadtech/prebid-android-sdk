package org.prebid.mobile.logging;

import android.os.AsyncTask;

import org.json.JSONObject;
import org.prebid.mobile.LogUtil;
import org.prebid.mobile.rendering.networking.BaseNetworkTask;
import org.prebid.mobile.rendering.networking.ResponseHandler;
import org.prebid.mobile.rendering.utils.helpers.AppInfoManager;
import org.prebid.mobile.tasksmanager.TasksManager;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles sending logs to a remote server using TasksManager
 */
public class LogServerSender {
    private static final String TAG = LogServerSender.class.getSimpleName();
    private static final int MAX_QUEUE_SIZE = 100;
    private static final int BATCH_SIZE = 10;
    private static final long BATCH_DELAY_MS = 1000; // 1 second delay between batches

    private static LogServerSender instance;
    private final BlockingQueue<LogEntry> logQueue;
    private final AtomicBoolean isProcessing;
    private String serverUrl;
    private boolean enabled;
    private final TasksManager tasksManager;

    private LogServerSender() {
        this.logQueue = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);
        this.isProcessing = new AtomicBoolean(false);
        this.enabled = false;
        this.tasksManager = TasksManager.getInstance();
    }

    public static synchronized LogServerSender getInstance() {
        if (instance == null) {
            instance = new LogServerSender();
        }
        return instance;
    }

    /**
     * Configure the log server sender
     *
     * @param serverUrl URL of the log server endpoint
     * @param enabled   Whether to enable sending logs to server
     */
    public void configure(String serverUrl, boolean enabled) {
        this.serverUrl = serverUrl;
        this.enabled = enabled;
    }

    /**
     * Add a log entry to be sent to server
     */
    public void sendLog(int level, String tag, String message) {
        if (!enabled || serverUrl == null) {
            return;
        }

        LogEntry entry = new LogEntry(level, tag, message, System.currentTimeMillis());

        // Add to queue, remove oldest if queue is full
        if (!logQueue.offer(entry)) {
            logQueue.poll(); // Remove oldest
            logQueue.offer(entry); // Add new
        }

        // Process queue if not already processing
        if (isProcessing.compareAndSet(false, true)) {
            processLogQueue();
        }
    }

    private void processLogQueue() {
        // Process logs in background thread using TasksManager
        tasksManager.executeOnBackgroundThread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (!logQueue.isEmpty()) {
                        sendLogBatch();

                        // Wait between batches if more logs are pending
                        if (!logQueue.isEmpty()) {
                            try {
                                Thread.sleep(BATCH_DELAY_MS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    LogUtil.error(TAG, "Error processing log queue: " + e.getMessage());
                } finally {
                    isProcessing.set(false);
                }
            }
        });
    }

    private void sendLogBatch() {
        JSONObject logBatch = new JSONObject();
        try {
            JSONObject logs = new JSONObject();
            int count = 0;

            while (!logQueue.isEmpty() && count < BATCH_SIZE) {
                LogEntry entry = logQueue.poll();
                if (entry != null) {
                    logs.put(String.valueOf(count), entry.toJson());
                    count++;
                }
            }

            if (count > 0) {
                logBatch.put("logs", logs);
                logBatch.put("app_id", AppInfoManager.getPackageName());
                logBatch.put("timestamp", System.currentTimeMillis());

                sendToServer(logBatch.toString());
            }
        } catch (Exception e) {
            LogUtil.error(TAG, "Error creating log batch: " + e.getMessage());
        }
    }

    private void sendToServer(String jsonData) {
        BaseNetworkTask.GetUrlParams params = new BaseNetworkTask.GetUrlParams();
        params.url = serverUrl;
        params.requestType = "POST";
        params.queryParams = jsonData;
        params.userAgent = AppInfoManager.getUserAgent();
        params.name = "logsender";

        BaseNetworkTask networkTask = new BaseNetworkTask(new ResponseHandler() {
            @Override
            public void onResponse(BaseNetworkTask.GetUrlResult response) {
                LogUtil.debug(TAG, "Logs sent successfully to server");
            }

            @Override
            public void onError(String msg, long responseTime) {
                LogUtil.error(TAG, "Failed to send logs to server: " + msg);
            }

            @Override
            public void onErrorWithException(Exception e, long responseTime) {
                LogUtil.error(TAG, "Failed to send logs to server: " + e.getMessage());
            }
        });

        networkTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
    }

    /**
     * Clear all pending logs
     */
    public void clearLogs() {
        logQueue.clear();
    }

    /**
     * Get the number of pending logs
     */
    public int getPendingLogsCount() {
        return logQueue.size();
    }

    /**
     * Check if log server is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Get configured server URL
     */
    public String getServerUrl() {
        return serverUrl;
    }
}