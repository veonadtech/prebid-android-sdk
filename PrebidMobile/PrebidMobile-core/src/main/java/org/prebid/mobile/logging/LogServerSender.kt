package org.prebid.mobile.logging

import android.os.AsyncTask
import org.json.JSONObject
import org.prebid.mobile.LogUtil
import org.prebid.mobile.PrebidMobile
import org.prebid.mobile.rendering.networking.BaseNetworkTask
import org.prebid.mobile.rendering.networking.ResponseHandler
import org.prebid.mobile.rendering.sdk.ManagersResolver
import org.prebid.mobile.rendering.sdk.deviceData.managers.DeviceInfoManager
import org.prebid.mobile.rendering.utils.helpers.AppInfoManager
import org.prebid.mobile.tasksmanager.TasksManager
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handles sending logs to a remote server using TasksManager
 */
class LogServerSender private constructor() {

    companion object {
        private val TAG = LogServerSender::class.java.simpleName
        private const val MAX_QUEUE_SIZE = 100
        private const val BATCH_SIZE = 10
        private const val BATCH_DELAY_MS = 1000L // 1 second delay between batches

        @Volatile
        private var instance: LogServerSender? = null

        @JvmStatic
        @Synchronized
        fun getInstance(): LogServerSender {
            return instance ?: LogServerSender().also { instance = it }
        }
    }

    private val logQueue: BlockingQueue<LogEntry> = LinkedBlockingQueue(MAX_QUEUE_SIZE)
    private val isProcessing = AtomicBoolean(false)
    private var serverUrl: String? = null
    private var enabled = false
    private val tasksManager = TasksManager.getInstance()

    /**
     * Configure the log server sender
     *
     * @param serverUrl URL of the log server endpoint
     * @param enabled   Whether to enable sending logs to server
     */
    fun configure(serverUrl: String?, enabled: Boolean) {
        this.serverUrl = serverUrl
        this.enabled = enabled
    }

    /**
     * Add a log entry to be sent to server
     */
    fun sendLog(level: Int, tag: String, message: String) {
        if (!enabled || serverUrl == null) {
            return
        }

        val device = AppInfoManager.getDeviceName()
        val accountId = PrebidMobile.getPrebidServerAccountId()


        val entry = LogEntry(
            level = level,
            message = message,
            tag = tag,
            timestamp = System.currentTimeMillis(),
            accountId = accountId,
            device = device
        )

        // Add to queue, remove oldest if queue is full
        if (!logQueue.offer(entry)) {
            logQueue.poll() // Remove oldest
            logQueue.offer(entry) // Add new
        }

        // Process queue if not already processing
        if (isProcessing.compareAndSet(false, true)) {
            processLogQueue()
        }
    }

    private fun processLogQueue() {
        // Process logs in background thread using TasksManager
        tasksManager.executeOnBackgroundThread {
            try {
                while (logQueue.isNotEmpty()) {
                    sendLogBatch()

                    // Wait between batches if more logs are pending
                    if (logQueue.isNotEmpty()) {
                        try {
                            Thread.sleep(BATCH_DELAY_MS)
                        } catch (e: InterruptedException) {
                            Thread.currentThread().interrupt()
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                LogUtil.error(TAG, "Error processing log queue: ${e.message}")
            } finally {
                isProcessing.set(false)
            }
        }
    }

    private fun sendLogBatch() {
        val logBatch = JSONObject()
        try {
            val logs = JSONObject()
            var count = 0

            while (logQueue.isNotEmpty() && count < BATCH_SIZE) {
                val entry = logQueue.poll()
                entry?.let {
                    logs.put(count.toString(), it.toJson())
                    count++
                }
            }

            if (count > 0) {
                logBatch.put("logs", logs)
                logBatch.put("app_id", AppInfoManager.getPackageName())
                logBatch.put("timestamp", System.currentTimeMillis())

                sendToServer(logBatch.toString())
            }
        } catch (e: Exception) {
            LogUtil.error(TAG, "Error creating log batch: ${e.message}")
        }
    }

    private fun sendToServer(jsonData: String) {
        val params = BaseNetworkTask.GetUrlParams().apply {
            url = serverUrl
            requestType = "POST"
            queryParams = jsonData
            userAgent = AppInfoManager.getUserAgent()
            name = "logsender"
        }

        val networkTask = BaseNetworkTask(object : ResponseHandler {
            override fun onResponse(response: BaseNetworkTask.GetUrlResult) {
                LogUtil.debug(TAG, "Logs sent successfully to server")
            }

            override fun onError(msg: String, responseTime: Long) {
                LogUtil.error(TAG, "Failed to send logs to server: $msg")
            }

            override fun onErrorWithException(e: Exception, responseTime: Long) {
                LogUtil.error(TAG, "Failed to send logs to server: ${e.message}")
            }
        })

        networkTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params)
    }

    /**
     * Clear all pending logs
     */
    fun clearLogs() {
        logQueue.clear()
    }

    /**
     * Get the number of pending logs
     */
    fun getPendingLogsCount(): Int = logQueue.size

    /**
     * Check if log server is enabled
     */
    fun isEnabled(): Boolean = enabled

    /**
     * Get configured server URL
     */
    fun getServerUrl(): String? = serverUrl
}