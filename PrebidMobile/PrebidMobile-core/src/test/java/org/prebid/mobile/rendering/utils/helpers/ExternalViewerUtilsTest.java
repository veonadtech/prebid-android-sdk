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

package org.prebid.mobile.rendering.utils.helpers;

import android.content.Context;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;
import org.prebid.mobile.PrebidMobile;
import org.prebid.mobile.rendering.listeners.OnBrowserActionResultListener;
import org.prebid.mobile.rendering.utils.url.ActionNotResolvedException;
import org.prebid.mobile.rendering.views.browser.AdBrowserActivity;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 21)
public class ExternalViewerUtilsTest {

    @Mock
    private Context mockContext;
    @Mock
    private OnBrowserActionResultListener mockResultListener;
    private boolean originalUseExternalBrowser;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        originalUseExternalBrowser = PrebidMobile.useExternalBrowser;
        PrebidMobile.useExternalBrowser = false;
    }

    @After
    public void tearDown() {
        PrebidMobile.useExternalBrowser = originalUseExternalBrowser;
    }

    @Test
    public void whenIsActivityCallableAndContextOrIntentNull_ReturnFalse() {
        assertFalse(ExternalViewerUtils.isActivityCallable(null, null));
        assertFalse(ExternalViewerUtils.isActivityCallable(mockContext, null));
        assertFalse(ExternalViewerUtils.isActivityCallable(null, mock(Intent.class)));
    }

    @Test
    public void whenIsActivityCallableAndQueryIntentActivitiesEmpty_ReturnFalse() {
        PackageManager mockManager = mock(PackageManager.class);
        when(mockManager.queryIntentActivities(any(Intent.class), anyInt())).thenReturn(Collections.emptyList());
        when(mockContext.getPackageManager()).thenReturn(mockManager);
        assertFalse(ExternalViewerUtils.isActivityCallable(mockContext, mock(Intent.class)));
    }

    @Test
    public void whenIsActivityCallableAndQueryIntentActivitiesNotEmpty_ReturnTrue() {
        PackageManager mockManager = mock(PackageManager.class);
        List<ResolveInfo> mockList = Collections.singletonList(mock(ResolveInfo.class));
        when(mockManager.queryIntentActivities(any(Intent.class), anyInt())).thenReturn(mockList);
        when(mockContext.getPackageManager()).thenReturn(mockManager);
        assertTrue(ExternalViewerUtils.isActivityCallable(mockContext, mock(Intent.class)));
    }

    @Test
    public void whenStartExternalVideoPlayerAndUrlNull_DoNothing() {
        ExternalViewerUtils.startExternalVideoPlayer(mockContext, null);
        verifyNoInteractions(mockContext);
    }

    @Test
    public void whenStartExternalVideoPlayerAndUrlNotNull_StartActivity() {
        ExternalViewerUtils.startExternalVideoPlayer(mockContext, "https://url");
        verify(mockContext).startActivity(any(Intent.class));
    }

    @Test
    public void whenExternalVideoPlayerMissing_DoNotThrow() {
        doThrow(new ActivityNotFoundException()).when(mockContext).startActivity(any(Intent.class));

        ExternalViewerUtils.startExternalVideoPlayer(mockContext, "https://example.com/video.mp4");

        verify(mockContext).startActivity(any(Intent.class));
    }

    @Test
    public void whenLaunchApplicationUrl_StartActivity() throws ActionNotResolvedException {
        PackageManager mockManager = mock(PackageManager.class);
        List<ResolveInfo> mockList = Collections.singletonList(mock(ResolveInfo.class));
        when(mockManager.queryIntentActivities(any(Intent.class), anyInt())).thenReturn(mockList);
        when(mockContext.getPackageManager()).thenReturn(mockManager);
        ExternalViewerUtils.launchApplicationUrl(mockContext, Uri.parse("test"));
        verify(mockContext).startActivity(any(Intent.class));
    }

    @Test
    public void whenStartBrowserAndUseExternalBrowserFalseAndActivityCallable_NotifyInternalBrowserSuccess() {
        PackageManager mockManager = mock(PackageManager.class);
        List<ResolveInfo> mockList = Collections.singletonList(mock(ResolveInfo.class));
        when(mockManager.queryIntentActivities(any(Intent.class), anyInt())).thenReturn(mockList);
        when(mockContext.getPackageManager()).thenReturn(mockManager);

        ExternalViewerUtils.startBrowser(mockContext, "url", true, mockResultListener);
        verify(mockContext).startActivity(any(Intent.class));
        verify(mockResultListener).onSuccess(OnBrowserActionResultListener.BrowserActionResult.INTERNAL_BROWSER);
    }

    @Test
    public void whenStartBrowserAndUseExternalBrowserFalse_NotifyInternalBrowserSuccess() {
        PackageManager mockManager = mock(PackageManager.class);
        List<ResolveInfo> mockList = Collections.singletonList(mock(ResolveInfo.class));
        when(mockManager.queryIntentActivities(any(Intent.class), anyInt())).thenReturn(mockList);
        when(mockContext.getPackageManager()).thenReturn(mockManager);

        ExternalViewerUtils.startBrowser(mockContext, "url", true, mockResultListener);
        verify(mockContext).startActivity(any(Intent.class));
        verify(mockResultListener).onSuccess(OnBrowserActionResultListener.BrowserActionResult.INTERNAL_BROWSER);
    }

    @Test
    @Config(sdk = 30)
    public void whenExternalBrowserStarts_NotifyExternalSuccessWithoutQueryingPackages() {
        PrebidMobile.useExternalBrowser = true;

        ExternalViewerUtils.startBrowser(mockContext, "https://example.com", 42, true, mockResultListener);

        ArgumentCaptor<Intent> capture = ArgumentCaptor.forClass(Intent.class);
        verify(mockContext).startActivity(capture.capture());
        assertTrue(Intent.ACTION_VIEW.equals(capture.getValue().getAction()));
        assertTrue(capture.getValue().getFlags() == Intent.FLAG_ACTIVITY_NEW_TASK);
        verify(mockContext, never()).getPackageManager();
        verify(mockResultListener).onSuccess(OnBrowserActionResultListener.BrowserActionResult.EXTERNAL_BROWSER);
        verify(mockResultListener, never()).onFailure();
    }

    @Test
    public void whenExternalBrowserMissing_DoNotOpenFallbackAndReportFailure() {
        PrebidMobile.useExternalBrowser = true;
        doThrow(new ActivityNotFoundException()).when(mockContext).startActivity(any(Intent.class));

        ExternalViewerUtils.startBrowser(mockContext, "https://example.com", 42, true, mockResultListener);

        ArgumentCaptor<Intent> capture = ArgumentCaptor.forClass(Intent.class);
        verify(mockContext).startActivity(capture.capture());
        assertTrue(Intent.ACTION_VIEW.equals(capture.getValue().getAction()));
        verify(mockResultListener).onFailure();
        verify(mockResultListener, never()).onSuccess(any());
    }

    @Test
    public void whenInternalBrowserMissing_DoNotOpenFallbackAndReportFailure() {
        doThrow(new ActivityNotFoundException()).when(mockContext).startActivity(any(Intent.class));

        ExternalViewerUtils.startBrowser(mockContext, "https://example.com", true, mockResultListener);

        ArgumentCaptor<Intent> capture = ArgumentCaptor.forClass(Intent.class);
        verify(mockContext).startActivity(capture.capture());
        assertTrue(capture.getValue().getComponent().getClassName().equals(AdBrowserActivity.class.getName()));
        verify(mockResultListener).onFailure();
        verify(mockResultListener, never()).onSuccess(any());
    }

    @Test
    public void whenContextMissing_ReportFailure() {
        ExternalViewerUtils.startBrowser(null, "https://example.com", true, mockResultListener);

        verify(mockResultListener).onFailure();
        verify(mockResultListener, never()).onSuccess(any());
    }
}
