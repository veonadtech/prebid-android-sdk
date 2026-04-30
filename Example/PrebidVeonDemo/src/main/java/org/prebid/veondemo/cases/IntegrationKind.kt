package org.prebid.veondemo.cases

enum class IntegrationKind(
    val adServer: String
) {
    GAM_ORIGINAL_WITHOUT_PREBID("GAM (Original API)"),
    GAM_ORIGINAL("GAM (Original API throw Prebid)"),
    GAM_RENDERING("GAM (Rendering API)"),
    NO_AD_SERVER("In-App (No Ad Server)"),
    MULTI_AD_SERVER("Multi Ad"),
}