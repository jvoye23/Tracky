package com.jvcs.tracky.core.data.device

import com.jvcs.tracky.core.domain.device.InstallTimeProvider
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileCreationDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.Foundation.timeIntervalSince1970
import kotlin.time.Instant

/**
 * iOS has no install-time API. The app's Documents folder is created on install, survives updates and
 * goes with the app, so its creation date stands in for one.
 */
@OptIn(ExperimentalForeignApi::class)
class IosInstallTimeProvider : InstallTimeProvider {

    override fun installedAt(): Instant {
        val fileManager = NSFileManager.defaultManager
        val documentDirectory =
            fileManager.URLForDirectory(
                directory = NSDocumentDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = false,
                error = null,
            )
        val path = requireNotNull(documentDirectory?.path) { "no documents directory" }

        val createdAt = fileManager.attributesOfItemAtPath(path, error = null)?.get(NSFileCreationDate) as? NSDate
        requireNotNull(createdAt) { "no creation date for the documents directory" }
        return Instant.fromEpochMilliseconds((createdAt.timeIntervalSince1970 * MILLIS_PER_SECOND).toLong())
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000
    }
}
