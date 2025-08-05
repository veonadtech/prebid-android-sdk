package org.prebid.veondemo.cases

enum class IntegrationKind(
    val adServer: String
) {
    GAM_ORIGINAL("GAM (Original API)"),
    GAM_RENDERING("GAM (Rendering API)"),
    NO_AD_SERVER("In-App (No Ad Server)"),
}