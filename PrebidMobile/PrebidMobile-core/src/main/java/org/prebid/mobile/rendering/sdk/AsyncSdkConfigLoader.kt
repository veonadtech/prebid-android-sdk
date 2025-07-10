package org.prebid.mobile.rendering.sdk

import android.os.AsyncTask
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.prebid.mobile.api.rendering.MultiAdLoader.AdPlatformSDK
import org.prebid.mobile.rendering.networking.BaseNetworkTask
import org.prebid.mobile.rendering.networking.BaseNetworkTask.GetUrlResult
import org.prebid.mobile.rendering.networking.ResponseHandler
import org.prebid.mobile.rendering.utils.helpers.AppInfoManager
import java.lang.ref.WeakReference

class AsyncSdkConfigLoader {

    companion object {
        private const val CONFIG_URL = "https://dcdn.veonadx.com/sdk/sdk_config_test.json"
        private const val MAX_RETRY_COUNT = 3
        private const val RETRY_DELAY_MS = 1000L
    }

    private var configRequestAsyncTask: AsyncTask<*, *, *>? = null
    private lateinit var weakHandler: WeakReference<SdkConfigResponseHandler>
    private var retryCount = 0

    interface SdkConfigResponseHandler {
        fun onSdkConfigReceived(sdks: List<AdPlatformSDK>)
        fun onError(error: String)
    }

    fun loadSdkConfig(handler: SdkConfigResponseHandler) {
        cancelTask()
        retryCount = 0
        weakHandler = WeakReference(handler)
        executeRequest()
    }

    private fun executeRequest() {
        cancelTask()

        val params = BaseNetworkTask.GetUrlParams().apply {
            url = CONFIG_URL
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

    private fun parseConfigResponse(json: String): List<AdPlatformSDK> {
        val jsonObject = JSONObject(json)
        val priorityArray = jsonObject.getJSONArray("priority")

        return List(priorityArray.length()) { index ->
            AdPlatformSDK.valueOf(priorityArray.getString(index))
        }
    }

    private fun notifySuccess(sdks: List<AdPlatformSDK>) {
        Handler(Looper.getMainLooper()).post {
            weakHandler.get()?.onSdkConfigReceived(sdks)
        }
    }

    private fun notifyError(error: String) {
        Handler(Looper.getMainLooper()).post {
            weakHandler.get()?.onError(error)
        }
    }

    fun cancelTask() {
        configRequestAsyncTask?.cancel(true)
        configRequestAsyncTask = null
    }
}