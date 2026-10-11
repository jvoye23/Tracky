package com.jvcs.tracky.core.data.purchases

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jvcs.tracky.core.domain.purchases.ProPackage
import com.jvcs.tracky.core.domain.purchases.ProPackageType
import com.revenuecat.purchases.kmp.models.Package
import com.revenuecat.purchases.kmp.models.PackageType
import com.revenuecat.purchases.kmp.models.Price
import com.revenuecat.purchases.kmp.models.StoreProduct
import kotlin.test.Test

/** Tracky sells three plans; every other RevenueCat package type collapses into OTHER. */
class ProPackageMapperTest {

    @Test
    fun aPackageBecomesThePlanThePaywallShows() {
        val revenueCatPackage = FakePackage("\$rc_annual", PackageType.ANNUAL, FakeStoreProduct("Pro Yearly", "€29.99"))

        assertThat(revenueCatPackage.toProPackage())
            .isEqualTo(ProPackage("\$rc_annual", ProPackageType.ANNUAL, "Pro Yearly", "€29.99"))
    }

    @Test
    fun theThreeTrackyPlansKeepTheirType() {
        assertThat(PackageType.MONTHLY.toProPackageType()).isEqualTo(ProPackageType.MONTHLY)
        assertThat(PackageType.ANNUAL.toProPackageType()).isEqualTo(ProPackageType.ANNUAL)
        assertThat(PackageType.LIFETIME.toProPackageType()).isEqualTo(ProPackageType.LIFETIME)
    }

    @Test
    fun everyOtherPackageTypeIsOther() {
        val others =
            listOf(
                PackageType.UNKNOWN,
                PackageType.CUSTOM,
                PackageType.SIX_MONTH,
                PackageType.THREE_MONTH,
                PackageType.TWO_MONTH,
                PackageType.WEEKLY,
            )

        others.forEach { type ->
            assertThat(type.toProPackageType()).isEqualTo(ProPackageType.OTHER)
        }
    }
}

/** Only what the mapper reads; touching anything else fails the test loudly. */
private class FakePackage(
    override val identifier: String,
    override val packageType: PackageType,
    override val storeProduct: StoreProduct,
) : Package {
    override val presentedOfferingContext get() = unused()
    override val webCheckoutUrl get() = unused()
}

private class FakeStoreProduct(override val title: String, formattedPrice: String) : StoreProduct {
    override val price = Price(formatted = formattedPrice, amountMicros = 29_990_000, currencyCode = "EUR")
    override val id get() = unused()
    override val type get() = unused()
    override val category get() = unused()
    override val localizedDescription get() = unused()
    override val period get() = unused()
    override val subscriptionOptions get() = unused()
    override val defaultOption get() = unused()
    override val discounts get() = unused()
    override val introductoryDiscount get() = unused()
    override val purchasingData get() = unused()
    override val presentedOfferingContext get() = unused()
    override val pricePerWeek get() = unused()
    override val pricePerMonth get() = unused()
    override val pricePerYear get() = unused()
}

private fun unused(): Nothing = error("not read by the mapper")
