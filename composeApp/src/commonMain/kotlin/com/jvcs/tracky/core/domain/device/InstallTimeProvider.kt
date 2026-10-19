package com.jvcs.tracky.core.domain.device

import kotlin.time.Instant

/** When this app was installed on this device, as the platform records it. A reinstall starts over. */
interface InstallTimeProvider {

    fun installedAt(): Instant
}
