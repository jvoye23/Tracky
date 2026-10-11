package com.jvcs.tracky.core.domain.purchases

/** One Pro plan on sale, as the store prices it for this customer. */
data class ProPackage(
    val identifier: String,
    val type: ProPackageType,
    val title: String,
    /** Already localised by the store, e.g. "€8.99". */
    val formattedPrice: String,
)

enum class ProPackageType {
    MONTHLY,
    ANNUAL,
    LIFETIME,

    /** A package type Tracky does not sell, kept so a dashboard change does not drop it silently. */
    OTHER,
}
