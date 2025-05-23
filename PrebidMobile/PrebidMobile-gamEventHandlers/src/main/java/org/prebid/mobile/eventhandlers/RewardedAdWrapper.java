/*
 *    Copyright 2018-2021 Prebid.org, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package org.prebid.mobile.eventhandlers;

import static org.prebid.mobile.eventhandlers.global.Constants.APP_EVENT;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.admanager.AdManagerAdRequest;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

import org.prebid.mobile.LogUtil;
import org.prebid.mobile.eventhandlers.utils.GamUtils;
import org.prebid.mobile.logging.GamLogUtil;
import org.prebid.mobile.logging.GamStatus;
import org.prebid.mobile.rendering.bidding.data.bid.Bid;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/**
 * This class is responsible for wrapping usage of RewardedAd from GAM SDK.
 * To achieve safe integration between various GAM SDK versions we have to wrap all PublisherAdView method execution in try / catch.
 * This class instance should be created via newInstance method, which will catch any potential exception on RewardedAd / RewardedAdWrapper instance creation
 */
public class RewardedAdWrapper extends FullScreenContentCallback implements OnUserEarnedRewardListener {

    private static final String TAG = RewardedAdWrapper.class.getSimpleName();
    public static final String KEY_METADATA = "AdTitle";

    private final WeakReference<Context> contextWeakReference;
    private final GamAdEventListener listener;
    private final String adUnitId;

    private RewardedAd rewardedAd;

    private final RewardedAdLoadCallback rewardedAdLoadCallback = new RewardedAdLoadCallback() {
        @Override
        public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
            RewardedAdWrapper.this.rewardedAd = rewardedAd;
            RewardedAdWrapper.this.rewardedAd.setFullScreenContentCallback(RewardedAdWrapper.this);
            listener.onEvent(AdEvent.LOADED);
            GamLogUtil.info("Ad loaded", GamStatus.LOADED);

            if (metadataContainsAdEvent()) {
                listener.onEvent(AdEvent.APP_EVENT_RECEIVED);
            }
        }

        @Override
        public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
            rewardedAd = null;
            GamLogUtil.error("Ad failed to load " + loadAdError.getMessage());
            GamLogUtil.error("Ad failed to load " + loadAdError.getResponseInfo());
            notifyErrorListener(loadAdError.getCode());
        }
    };

    private RewardedAdWrapper(Context context, String gamAdUnitId, GamAdEventListener eventListener) {
        if (context == null) {
            throw new IllegalArgumentException("Context can't be null.");
        }

        listener = eventListener;
        contextWeakReference = new WeakReference<>(context);
        adUnitId = gamAdUnitId;
    }

    @Nullable
    static RewardedAdWrapper newInstance(Context context, String gamAdUnitId, GamAdEventListener eventListener) {
        try {
            return new RewardedAdWrapper(context, gamAdUnitId, eventListener);
        } catch (Throwable throwable) {
            GamLogUtil.error("Failed to create RewardedAdWrapper instance");
            LogUtil.error(TAG, Log.getStackTraceString(throwable));
        }
        return null;
    }

    //region ==================== GAM FullScreenContentCallback Implementation

    @Override
    public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
        rewardedAd = null;
        GamLogUtil.error("Ad failed to show fullscreen" + adError.getMessage());
        notifyErrorListener(adError.getCode());
    }

    @Override
    public void onAdShowedFullScreenContent() {
        listener.onEvent(AdEvent.DISPLAYED);
        GamLogUtil.info("Ad showed fullscreen", GamStatus.DISPLAYED);
    }

    @Override
    public void onAdDismissedFullScreenContent() {
        listener.onEvent(AdEvent.CLOSED);
        rewardedAd = null;
        GamLogUtil.info("Ad dismissed fullscreen", GamStatus.CLOSED);
    }

    @Override
    public void onUserEarnedReward(@NonNull RewardItem rewardItem) {
        GamLogUtil.info("User earned reward", GamStatus.CLOSED);
        listener.onEvent(AdEvent.REWARD_EARNED);
        listener.onEvent(AdEvent.CLOSED);
    }

    @Override
    public void onAdClicked() {
        listener.onEvent(AdEvent.CLICKED);
        GamLogUtil.info("Ad clicked", GamStatus.CLICKED);
    }

    @Override
    public void onAdImpression() {
        GamLogUtil.info("Ad impression", GamStatus.IMPRESSION);
    }

    //endregion ==================== GAM FullScreenContentCallback Implementation

    public void loadAd(Bid bid) {
        try {
            AdManagerAdRequest adRequest = new AdManagerAdRequest.Builder().build();

            if (bid != null) {
                Map<String, String> targetingMap = new HashMap<>(bid.getPrebid().getTargeting());
                GamUtils.handleGamCustomTargetingUpdate(adRequest, targetingMap);
            }

            RewardedAd.load(contextWeakReference.get(), adUnitId, adRequest, rewardedAdLoadCallback);
        } catch (Throwable throwable) {
            LogUtil.error(TAG, Log.getStackTraceString(throwable));
            GamLogUtil.error("Load ad error: " + throwable.getMessage());
        }
    }

    public boolean isLoaded() {
        try {
            return rewardedAd != null;
        } catch (Throwable throwable) {
            LogUtil.error(TAG, Log.getStackTraceString(throwable));
        }

        return false;
    }

    public void show(Activity activity) {
        if (rewardedAd == null) {
            LogUtil.error(TAG, "show: Failed! Rewarded ad is null.");
            return;
        }

        try {
            rewardedAd.show(activity, this);
        } catch (Throwable throwable) {
            LogUtil.error(TAG, Log.getStackTraceString(throwable));
            GamLogUtil.error("Show ad error: " + throwable.getMessage());
        }
    }

    public Object getRewardItem() {
        return rewardedAd != null ? rewardedAd.getRewardItem() : null;
    }

    private void notifyErrorListener(int errorCode) {
        final AdEvent adEvent = AdEvent.FAILED;
        adEvent.setErrorCode(errorCode);

        listener.onEvent(adEvent);
    }

    private boolean metadataContainsAdEvent() {
        try {
            if (rewardedAd == null) {
                LogUtil.debug(TAG, "metadataContainsAdEvent: Failed to process. RewardedAd is null.");
                return false;
            }

            final Bundle adMetadata = rewardedAd.getAdMetadata();
            return APP_EVENT.equals(adMetadata.getString(KEY_METADATA));
        } catch (Throwable throwable) {
            LogUtil.error(TAG, Log.getStackTraceString(throwable));
            GamLogUtil.error("metadataContainsAdEvent error: " + throwable.getMessage());
        }
        return false;
    }
}
