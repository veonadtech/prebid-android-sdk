package org.prebid.mobile.rendering.sdk;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;

import org.prebid.mobile.LogUtil;
import org.prebid.mobile.LogUtil.PrebidLogger;
import org.prebid.mobile.PrebidMobile;
import org.prebid.mobile.api.rendering.PrebidRenderer;
import org.prebid.mobile.configuration.SdkConfigHolder;
import org.prebid.mobile.logging.SdkLogUtil;
import org.prebid.mobile.rendering.listeners.SdkInitializationListener;
import org.prebid.mobile.rendering.session.manager.OmAdSessionManager;
import org.prebid.mobile.rendering.utils.helpers.AdvertisingIdManager;
import org.prebid.mobile.rendering.utils.helpers.AppInfoManager;
import org.prebid.mobile.tasksmanager.TasksManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class SdkInitializer {

    private static final String TAG = SdkInitializer.class.getSimpleName();
    private static TasksManager tasksManager = TasksManager.getInstance();

    public static void init(
            @Nullable Context context,
            @Nullable String configURL,
            @Nullable SdkInitializationListener listener
    ) {
        if (PrebidMobile.isSdkInitialized() || InitializationNotifier.isInitializationInProgress()) {
            return;
        }

        InitializationNotifier initializationNotifier = new InitializationNotifier(listener);

        Context applicationContext = getApplicationContext(context);
        if (applicationContext == null) {
            initializationNotifier.initializationFailed("Context must be not null!");
            return;
        }

        LogUtil.debug(TAG, "Initializing Prebid SDK");
        PrebidContextHolder.setContext(applicationContext);

        if (PrebidMobile.getLogLevel() != null) {
            LogUtil.setLogLevel(PrebidMobile.getLogLevel()
                                            .getValue());
        }

        PrebidLogger customLogger = PrebidMobile.getCustomLogger();
        if (customLogger != null) {
            LogUtil.setLogger(customLogger);
        }

        tasksManager.executeOnBackgroundThread(() -> {
            try {
                AdvertisingIdClient.Info adInfo = AdvertisingIdClient.getAdvertisingIdInfo(applicationContext);
                String aaid = adInfo.getId();
                PrebidMobile.setAAID(aaid);
            } catch (Exception e) {
                LogUtil.error(TAG, "Error getting AAID: " + e.getMessage());
            }
        });

        try {
            PrebidMobile.registerPluginRenderer(new PrebidRenderer());

            new SdkConfigLoader().loadSdkConfig(configURL, new SdkConfigLoader.SdkConfigResponseHandler() {

                @Override
                public void onSdkConfigReceived(@NonNull SdkConfig sdkConfig) {
                    PrebidMobile.setSdkLogState(sdkConfig);
                    SdkConfigHolder.priorityOrderSDK = sdkConfig.getPriority();
                    SdkLogUtil.configureLogServer("https://gamsdklog.veonadx.com/api/v1/log", sdkConfig.isActive());
                }

                @Override
                public void onError(@NonNull String error) {
                    LogUtil.debug(TAG, "Error loading SDK log state: " + error);
                }
            });

            AppInfoManager.init(applicationContext);

            OmAdSessionManager.activateOmSdk(context);

            ManagersResolver.getInstance()
                            .prepare(applicationContext);

            JSLibraryManager.getInstance(applicationContext)
                            .checkIfScriptsDownloadedAndStartDownloadingIfNot();
        } catch (Throwable throwable) {
            initializationNotifier.initializationFailed("Exception during initialization: " + throwable.getMessage() + "\n" + Log.getStackTraceString(throwable));
            return;
        }

        new Thread(() -> runBackgroundTasks(
                initializationNotifier,
                Executors.newFixedThreadPool(2))
        ).start();
    }

    @VisibleForTesting
    public static void runBackgroundTasks(
            InitializationNotifier initializationNotifier,
            ExecutorService executor
    ) {
        try {
            Future<String> statusRequesterResult = null;
            if (!PrebidMobile.shouldDisableStatusCheck()) {
                statusRequesterResult = executor.submit(new StatusRequester());
            } else {
                LogUtil.debug(TAG, "Prebid SDK initialization skipping status check");
            }
            executor.execute(new UserConsentFetcherTask());
            executor.execute(new UserAgentFetcherTask());
            executor.execute(AdvertisingIdManager::initAdvertisingId);
            executor.shutdown();

            boolean terminatedByTimeout = !executor.awaitTermination(10, TimeUnit.SECONDS);
            if (terminatedByTimeout) {
                initializationNotifier.initializationFailed("Terminated by timeout.");
                return;
            }

            String statusRequesterError = statusRequesterResult != null ? statusRequesterResult.get() : null;
            initializationNotifier.initializationCompleted(statusRequesterError);
        } catch (Exception exception) {
            initializationNotifier.initializationFailed("Exception during initialization: " + Log.getStackTraceString(exception));
        }
    }

    @Nullable
    private static Context getApplicationContext(
            @Nullable Context context
    ) {
        if (context instanceof Application) {
            return context;
        } else if (context != null) {
            return context.getApplicationContext();
        }
        return null;
    }

    protected static class UserConsentFetcherTask implements Runnable {

        @Override
        public void run() {
            ManagersResolver.getInstance()
                            .getUserConsentManager()
                            .initConsentValues();
        }

    }

}
