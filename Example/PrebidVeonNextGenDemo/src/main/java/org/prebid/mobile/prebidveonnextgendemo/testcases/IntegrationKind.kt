package org.prebid.mobile.prebidveonnextgendemo.testcases

enum class IntegrationKind(
    val adServer: String
) {
    ORIGINAL("Original API"),
    RENDERING("Rendering API"),
    MULTI("Multi SDK");
}