package com.jvcs.tracky.core.data.device

import android.content.Context
import android.content.pm.PackageManager
import com.jvcs.tracky.core.domain.device.InstallTimeProvider
import kotlin.time.Instant

/** The package manager's first install time; an update keeps it, a reinstall resets it. */
class AndroidInstallTimeProvider(private val context: Context) : InstallTimeProvider {

    override fun installedAt(): Instant {
        val packageInfo =
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        return Instant.fromEpochMilliseconds(packageInfo.firstInstallTime)
    }
}
