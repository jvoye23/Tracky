package com.jvcs.tracky.core.data.purchases

import com.jvcs.tracky.core.domain.purchases.ProPackage
import com.jvcs.tracky.core.domain.purchases.ProPackageType
import com.revenuecat.purchases.kmp.models.Package
import com.revenuecat.purchases.kmp.models.PackageType

fun Package.toProPackage(): ProPackage =
    ProPackage(
        identifier = identifier,
        type = packageType.toProPackageType(),
        title = storeProduct.title,
        formattedPrice = storeProduct.price.formatted,
    )

fun PackageType.toProPackageType(): ProPackageType =
    when (this) {
        PackageType.MONTHLY -> ProPackageType.MONTHLY
        PackageType.ANNUAL -> ProPackageType.ANNUAL
        PackageType.LIFETIME -> ProPackageType.LIFETIME
        else -> ProPackageType.OTHER
    }
