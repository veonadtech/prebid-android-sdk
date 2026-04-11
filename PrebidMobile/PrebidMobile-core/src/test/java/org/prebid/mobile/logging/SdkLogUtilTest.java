/*
 *    Copyright 2018-2025 Prebid.org, Inc.
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

package org.prebid.mobile.logging;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.mobile.PrebidMobile;
import org.prebid.mobile.api.data.AdFormat;
import org.prebid.mobile.api.data.SdkType;
import org.prebid.mobile.reflection.Reflection;
import org.prebid.mobile.rendering.sdk.Level;
import org.prebid.mobile.rendering.sdk.SdkConfig;
import org.prebid.mobile.testutils.BaseSetup;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = BaseSetup.testSDK)
public class SdkLogUtilTest extends BaseSetup {

    @Before
    public void setupTest() {
        // Reset SDK config before each test
        Reflection.setStaticVariableTo(PrebidMobile.class, "sdkConfig", null);
    }

    /**
     * Test that error() method doesn't throw NPE when getSdkLogState() returns null
     * This was the original bug reported in version 0.0.7.6 and earlier
     */
    @Test
    public void testErrorWithNullSdkLogState_shouldNotThrowNPE() {
        // Given: SDK config is null
        PrebidMobile.setSdkLogState(null);

        // When: Calling error method
        try {
            SdkLogUtil.error(
                "TestMessage",
                AdFormat.BANNER,
                "test-ad-unit-id",
                SdkType.GAM
            );
            // Then: No exception should be thrown
            assertTrue("Method should complete without exception", true);
        } catch (NullPointerException e) {
            fail("Should not throw NullPointerException when sdkConfig is null: " + e.getMessage());
        }
    }

    /**
     * Test that info() method doesn't throw NPE when getSdkLogState() returns null
     */
    @Test
    public void testInfoWithNullSdkLogState_shouldNotThrowNPE() {
        // Given: SDK config is null
        PrebidMobile.setSdkLogState(null);

        // When: Calling info method
        try {
            SdkLogUtil.info(
                "TestMessage",
                SdkAdStatus.DISPLAYED,
                AdFormat.BANNER,
                "test-ad-unit-id",
                SdkType.GAM
            );
            // Then: No exception should be thrown
            assertTrue("Method should complete without exception", true);
        } catch (NullPointerException e) {
            fail("Should not throw NullPointerException when sdkConfig is null: " + e.getMessage());
        }
    }

    /**
     * Test that the method works correctly when sdkConfig is properly initialized
     */
    @Test
    public void testErrorWithValidSdkLogState_shouldNotThrowException() {
        // Given: SDK config is properly initialized
        List<SdkType> sdkTypes = Arrays.asList(SdkType.GAM, SdkType.PREBID);
        SdkConfig validConfig = new SdkConfig(
            true,
            sdkTypes,
            Level.ERROR,
            sdkTypes
        );
        PrebidMobile.setSdkLogState(validConfig);

        // When: Calling error method
        try {
            SdkLogUtil.error(
                "TestMessage",
                AdFormat.BANNER,
                "test-ad-unit-id",
                SdkType.GAM
            );
            // Then: No exception should be thrown
            assertTrue("Method should complete without exception", true);
        } catch (Exception e) {
            fail("Should not throw any exception with valid config: " + e.getMessage());
        }
    }

    /**
     * Test the race condition scenario where getSdkLogState() could become null
     * between multiple calls in the same method
     */
    @Test
    public void testRaceConditionScenario_shouldHandleGracefully() {
        // Given: SDK config is initially valid
        List<SdkType> sdkTypes = Arrays.asList(SdkType.GAM);
        SdkConfig validConfig = new SdkConfig(
            true,
            sdkTypes,
            Level.ERROR,
            sdkTypes
        );
        PrebidMobile.setSdkLogState(validConfig);

        // When: We simulate a scenario where config could become null
        // (In real scenario, this could happen from another thread)
        try {
            SdkLogUtil.error(
                "TestMessage",
                AdFormat.BANNER,
                "test-ad-unit-id",
                SdkType.GAM
            );

            // Simulate config being set to null (as if from another thread)
            PrebidMobile.setSdkLogState(null);

            // Call again with null config
            SdkLogUtil.error(
                "AnotherTestMessage",
                AdFormat.BANNER,
                "test-ad-unit-id",
                SdkType.GAM
            );

            // Then: Both calls should complete without exception
            assertTrue("Methods should handle config changes gracefully", true);
        } catch (NullPointerException e) {
            fail("Should handle race condition gracefully without NPE: " + e.getMessage());
        }
    }

    /**
     * Test with blank tag
     */
    @Test
    public void testErrorWithBlankTag_shouldReturnEarly() {
        // Given: Valid SDK config
        List<SdkType> sdkTypes = Arrays.asList(SdkType.GAM);
        SdkConfig validConfig = new SdkConfig(
            true,
            sdkTypes,
            Level.ERROR,
            sdkTypes
        );
        PrebidMobile.setSdkLogState(validConfig);

        // When: Calling with blank tag
        try {
            SdkLogUtil.error(
                    "",   // blank tag
                    "AnotherTestMessage",
                    AdFormat.BANNER,
                    "test-ad-unit-id",
                    SdkType.GAM
            );
            // Then: Should return early without exception
            assertTrue("Method should handle blank tag gracefully", true);
        } catch (Exception e) {
            fail("Should handle blank tag without exception: " + e.getMessage());
        }
    }

    /**
     * Test with null message
     */
    @Test
    public void testErrorWithNullMessage_shouldReturnEarly() {
        // Given: Valid SDK config
        List<SdkType> sdkTypes = Arrays.asList(SdkType.GAM);
        SdkConfig validConfig = new SdkConfig(
                true,
                sdkTypes,
                Level.ERROR,
                sdkTypes
        );
        PrebidMobile.setSdkLogState(validConfig);

        // When: Calling with null message (tag defaults to GAM internally)
        try {
            SdkLogUtil.error(
                    null,  // null message
                    AdFormat.BANNER,
                    "test-ad-unit-id",
                    SdkType.GAM
            );
            // Then: Should return early without exception
            assertTrue("Method should handle null message gracefully", true);
        } catch (Exception e) {
            fail("Should handle null message without exception: " + e.getMessage());
        }
    }

    /**
     * Test with blank message
     */
    @Test
    public void testErrorWithBlankMessage_shouldReturnEarly() {
        // Given: Valid SDK config
        List<SdkType> sdkTypes = Arrays.asList(SdkType.GAM);
        SdkConfig validConfig = new SdkConfig(
            true,
            sdkTypes,
            Level.ERROR,
            sdkTypes
        );
        PrebidMobile.setSdkLogState(validConfig);

        // When: Calling with blank message
        try {
            SdkLogUtil.error(
                "   ",  // blank message
                AdFormat.BANNER,
                "test-ad-unit-id",
                SdkType.GAM
            );
            // Then: Should return early without exception
            assertTrue("Method should handle blank message gracefully", true);
        } catch (Exception e) {
            fail("Should handle blank message without exception: " + e.getMessage());
        }
    }
}
