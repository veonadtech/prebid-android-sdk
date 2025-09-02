package org.prebid.mobile.rendering.sdk

import android.os.AsyncTask
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.prebid.mobile.PrebidMobile
import org.prebid.mobile.api.data.SdkType
import org.prebid.mobile.rendering.networking.BaseNetworkTask
import org.prebid.mobile.rendering.networking.BaseNetworkTask.GetUrlResult
import org.prebid.mobile.rendering.networking.ResponseHandler
import org.prebid.mobile.rendering.utils.helpers.AppInfoManager

class SdkConfigLoader {

    companion object Companion {
        private const val MAX_RETRY_COUNT = 3
        private const val RETRY_DELAY_MS = 1000L
    }

    private var configRequestAsyncTask: AsyncTask<*, *, *>? = null
    private var responseHandler: SdkConfigResponseHandler? = null
    private var retryCount = 0
    private var configUrl: String? = null

    interface SdkConfigResponseHandler {
        fun onSdkConfigReceived(sdkConfig: SdkConfig)
        fun onError(error: String)
    }

    fun loadSdkConfig(configUrl: String, handler: SdkConfigResponseHandler) {
        this.configUrl = configUrl
        cancelTask()
        retryCount = 0
        responseHandler = handler
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

    private fun parseConfigResponse(json: String): SdkConfig {
        val jsonObject = JSONObject(json)
        val isActive: Boolean = jsonObject.getBoolean("isActive")

        val level = Level.valueOf(jsonObject.getString("level"))

        val sdkTypeJson = jsonObject.getJSONArray("sdkType")

        val sdkTypeList: List<SdkType> = List(sdkTypeJson.length()) { index ->
            SdkType.valueOf(sdkTypeJson.getString(index))
        }

        val priorityJson = jsonObject.getJSONArray("priority")

        val priorityList: List<SdkType> = List(priorityJson.length()) { index ->
            SdkType.valueOf(priorityJson.getString(index))
        }

        return SdkConfig(isActive, sdkTypeList, level, priorityList)
    }

    private fun notifySuccess(sdks: SdkConfig) {
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