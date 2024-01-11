package org.prebid.mobile.eventhandlers;

import android.view.View;

import org.prebid.mobile.api.rendering.listeners.BannerViewListener;

public interface AuctionListener {
    // winning prebid server
    void onPRBWin(float price);
    // winning GAM
    void onGAMWin(View view);
}
