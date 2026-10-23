package org.prebid.mobile.rendering.views.webview.mraid;

import org.junit.Test;
import org.prebid.mobile.rendering.mraid.methods.network.RedirectUrlListener;
import org.prebid.mobile.rendering.networking.BaseNetworkTask;

import static org.mockito.Mockito.*;

public class OriginalUrlResponseCallBackTest {

    @Test
    public void missingResultOrUrlNotifiesFailure() {
        RedirectUrlListener listener = mock(RedirectUrlListener.class);
        OriginalUrlResponseCallBack callback = new OriginalUrlResponseCallBack(listener);
        BaseNetworkTask.GetUrlResult result = new BaseNetworkTask.GetUrlResult();

        callback.onResponse(null);
        callback.onResponse(result);
        result.originalUrl = "";
        callback.onResponse(result);

        verify(listener, times(3)).onFailed();
        verify(listener, never()).onSuccess(anyString(), anyString());
    }

    @Test
    public void missingExceptionNotifiesFailure() {
        RedirectUrlListener listener = mock(RedirectUrlListener.class);

        new OriginalUrlResponseCallBack(listener).onErrorWithException(null, 0);

        verify(listener).onFailed();
    }
}
