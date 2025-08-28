package org.prebid.mobile.rendering.sdk

import android.os.AsyncTask
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.prebid.mobile.api.data.SdkType
import org.prebid.mobile.rendering.networking.BaseNetworkTask
import org.prebid.mobile.rendering.networking.BaseNetworkTask.GetUrlResult
import org.prebid.mobile.rendering.networking.ResponseHandler
import org.prebid.mobile.rendering.utils.helpers.AppInfoManager

class AsyncSdkConfigLoader {

    companion object {
        private const val MAX_RETRY_COUNT = 3
        private const val RETRY_DELAY_MS = 1000L
    }

    private var configRequestAsyncTask: AsyncTask<*, *, *>? = null
    private var responseHandler: SdkConfigResponseHandler? = null
    private var configUrl: String? = null
    private var retryCount = 0

    interface SdkConfigResponseHandler {
        fun onSdkConfigReceived(sdks: List<SdkType>)
        fun onError(error: String)
    }

    fun loadSdkConfig(configUrl: String?, handler: SdkConfigResponseHandler) {
        cancelTask()
        retryCount = 0
        responseHandler = handler
        this.configUrl = configUrl

        if (configUrl == null) {
            handleRetryOrError("Config URL cannot be null")
            return
        }

        executeRequest()
    }

    private fun executeRequest() {
        cancelTask()

        val params = BaseNetworkTask.GetUrlParams().apply {
            url = configUrl
            requestType = "GET"
            name = "sdkconfig"
            userAgent = AppInfoManager.getUserAgent()
        }

        val networkTask = BaseNetworkTask(object : ResponseHandler {
            override fun onResponse(response: GetUrlResult) {
                if (response.isOkStatusCode && !response.responseString.isNullOrEmpty()) {
                    try {
                        val sdks = parseConfigResponse(response.responseString)
                        notifySuccess(sdks)
                    } catch (e: Exception) {
                        handleRetryOrError(e.message ?: "Failed to parse config")
                    }
                } else {
                    handleRetryOrError("Invalid response from server (code: ${response.statusCode})")
                }
            }

            override fun onError(msg: String, responseTime: Long) {
                handleRetryOrError(msg)
            }

            override fun onErrorWithException(e: Exception, responseTime: Long) {
                handleRetryOrError(e.message ?: "Unknown error")
            }
        })

        configRequestAsyncTask = networkTask.executeOnExecutor(
            AsyncTask.THREAD_POOL_EXECUTOR,
            params
        )
    }

    private fun handleRetryOrError(error: String) {
        if (retryCount < MAX_RETRY_COUNT) {
            retryCount++
            Handler(Looper.getMainLooper()).postDelayed({
                executeRequest()
            }, RETRY_DELAY_MS)
        } else {
            notifyError(error)
        }
    }

    private fun parseConfigResponse(json: String): List<SdkType> {
        val jsonObject = JSONObject(json)
        val priorityArray = jsonObject.getJSONArray("priority")

        return List(priorityArray.length()) { index ->
            SdkType.valueOf(priorityArray.getString(index))
        }
    }

    private fun notifySuccess(sdks: List<SdkType>) {
        Handler(Looper.getMainLooper()).post {
            responseHandler?.onSdkConfigReceived(sdks)
        }
    }

    private fun notifyError(error: String) {
        Handler(Looper.getMainLooper()).post {
            responseHandler?.onError(error)
        }
    }

    fun cancelTask() {
        configRequestAsyncTask?.cancel(true)
        configRequestAsyncTask = null
    }
}
