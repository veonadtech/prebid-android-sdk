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

package org.prebid.mobile.rendering.networking.parameters;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.telephony.TelephonyManager;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.mobile.reflection.sdk.ManagersResolverReflection;
import org.prebid.mobile.rendering.models.openrtb.BidRequest;
import org.prebid.mobile.rendering.sdk.ManagersResolver;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowTelephonyManager;

@RunWith(RobolectricTestRunner.class)
public class NetworkParameterBuilderTest {

    private final String NETWORK_CARRIER = "carrier";

    private ConnectivityManager connectivityManager;

    @Before
    public void setUp() throws Exception {
        Activity robolectricActivity = Robolectric.buildActivity(Activity.class).create().get();
        shadowOf(robolectricActivity).grantPermissions(Manifest.permission.ACCESS_NETWORK_STATE);
        ShadowTelephonyManager shadowTelephonyManager = shadowOf(
                (TelephonyManager) robolectricActivity.getSystemService(Context.TELEPHONY_SERVICE));

        shadowTelephonyManager.setNetworkOperatorName(NETWORK_CARRIER);
        shadowTelephonyManager.setNetworkOperator(NETWORK_CARRIER);

        connectivityManager = mock(ConnectivityManager.class);

        Context contextWithMockedConnectivity = new ContextWrapper(robolectricActivity) {
            @Override
            public Object getSystemService(String name) {
                if (Context.CONNECTIVITY_SERVICE.equals(name)) {
                    return connectivityManager;
                }
                return super.getSystemService(name);
            }

            @Override
            public Context getApplicationContext() {
                return this;
            }
        };

        ManagersResolver resolver = ManagersResolver.getInstance();
        ManagersResolverReflection.resetManagers(resolver);
        resolver.prepare(contextWithMockedConnectivity);
    }

    private NetworkCapabilities givenActiveNetwork() {
        Network network = mock(Network.class);
        NetworkCapabilities capabilities = mock(NetworkCapabilities.class);

        when(connectivityManager.getActiveNetwork()).thenReturn(network);
        when(connectivityManager.getNetworkCapabilities(network)).thenReturn(capabilities);
        when(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).thenReturn(true);

        return capabilities;
    }

    @Test
    public void whenTestAppendBuilderParametersAndWifi_WiFiType() throws JSONException {
        NetworkCapabilities capabilities = givenActiveNetwork();
        when(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(false);

        BidRequest expectedBidRequest = new BidRequest();
        expectedBidRequest.getDevice().mccmnc = "car-rier";
        expectedBidRequest.getDevice().carrier = NETWORK_CARRIER;
        expectedBidRequest.getDevice().connectiontype = NetworkParameterBuilder.CONNECTION_TYPE_WIFI;

        NetworkParameterBuilder builder = new NetworkParameterBuilder();
        AdRequestInput adRequestInput = new AdRequestInput();
        builder.appendBuilderParameters(adRequestInput);
        assertEquals(expectedBidRequest.getJsonObject().toString(),
                adRequestInput.getBidRequest().getJsonObject().toString());
    }

    @Test
    public void whenTestAppendBuilderParametersAndCell_CellType() throws JSONException {
        NetworkCapabilities capabilities = givenActiveNetwork();
        when(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(true);

        BidRequest expectedBidRequest = new BidRequest();
        expectedBidRequest.getDevice().mccmnc = "car-rier";
        expectedBidRequest.getDevice().carrier = NETWORK_CARRIER;
        expectedBidRequest.getDevice().connectiontype = NetworkParameterBuilder.CONNECTION_TYPE_CELL_UNKNOWN_G;

        NetworkParameterBuilder builder = new NetworkParameterBuilder();
        AdRequestInput adRequestInput = new AdRequestInput();
        builder.appendBuilderParameters(adRequestInput);
        assertEquals(expectedBidRequest.getJsonObject().toString(),
                adRequestInput.getBidRequest().getJsonObject().toString());
    }
}