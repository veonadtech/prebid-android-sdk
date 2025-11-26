package org.prebid.mobile.rendering.networking;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;

import org.prebid.mobile.rendering.networking.BaseNetworkTask.GetUrlParams;
import org.prebid.mobile.rendering.networking.BaseNetworkTask.GetUrlResult;
import org.prebid.mobile.rendering.utils.helpers.AppInfoManager;

public class NetworkHelper {

    public interface PublicIpCallback {
        void onIpReady(String ip);
        void onError(String error);
    }

    private static final String[] IP_SERVICES = {
            "https://api.ipify.org",
            "https://checkip.amazonaws.com",
            "https://icanhazip.com",
            "https://ifconfig.me/ip"
    };

    private static int currentIndex = 0;
    private static AsyncTask<?, ?, ?> currentTask = null;

    public static void fetchPublicIP(PublicIpCallback callback) {
        currentIndex = 0;
        performNextRequest(callback);
    }

    private static void performNextRequest(PublicIpCallback callback) {
        if (currentIndex >= IP_SERVICES.length) {
            callback.onError("All IP services failed");
            return;
        }

        String url = IP_SERVICES[currentIndex];
        currentIndex++;

        GetUrlParams params = new GetUrlParams();
        params.url = url;
        params.requestType = "GET";
        params.name = "public_ip";
        params.userAgent = AppInfoManager.getUserAgent();

        BaseNetworkTask task = new BaseNetworkTask(new ResponseHandler() {

            @Override
            public void onResponse(GetUrlResult response) {
                if (response.isOkStatusCode() && response.responseString != null) {
                    String ip = response.responseString.trim();

                    new Handler(Looper.getMainLooper()).post(() ->
                            callback.onIpReady(ip)
                    );
                } else {
                    performNextRequest(callback);
                }
            }

            @Override
            public void onError(String msg, long responseTime) {
                performNextRequest(callback);
            }

            @Override
            public void onErrorWithException(Exception e, long responseTime) {
                performNextRequest(callback);
            }
        });

        currentTask = task.executeOnExecutor(
                AsyncTask.THREAD_POOL_EXECUTOR,
                params
        );
    }

}
