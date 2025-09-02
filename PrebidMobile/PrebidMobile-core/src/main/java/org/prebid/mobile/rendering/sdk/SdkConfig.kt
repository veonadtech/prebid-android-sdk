package org.prebid.mobile.rendering.sdk

import org.prebid.mobile.api.data.SdkType

data class SdkConfig(
    val isActive: Boolean,
    val sdkType: List<SdkType>,
    val level: Level,
    val priority: List<SdkType>,
)

enum class Level {
    WARN, ERROR, INFO, ASSERT, VERBOSE, NONE, DEBUG
}
