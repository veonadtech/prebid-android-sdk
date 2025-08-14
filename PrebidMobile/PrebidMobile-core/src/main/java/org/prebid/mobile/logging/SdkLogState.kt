package org.prebid.mobile.logging

data class SdkLogState(
    val isActive: Boolean,
    val sdkType: List<SdkType>,
    val level: List<Level>
)

enum class Level {
    WARN, ERROR, INFO, ASSERT, VERBOSE
}
