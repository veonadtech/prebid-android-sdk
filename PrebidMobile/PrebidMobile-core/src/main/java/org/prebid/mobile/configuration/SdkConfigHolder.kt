package org.prebid.mobile.configuration

import org.prebid.mobile.api.data.SdkType

object SdkConfigHolder {
    @JvmField
    var priorityOrderSDK: List<SdkType> = listOf(SdkType.PREBID, SdkType.YANDEX, SdkType.GAM)
}
