package com.jvcs.tracky.core.domain.device

import kotlin.time.Instant

class FakeInstallTimeProvider(var installedAt: Instant = Instant.fromEpochMilliseconds(0)) : InstallTimeProvider {

    override fun installedAt(): Instant = installedAt
}
