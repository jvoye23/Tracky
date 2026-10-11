package com.jvcs.tracky.core.domain.device

/**
 * A fixed device id. [THIS_DEVICE] and [OTHER_DEVICE] name the two cases every timer test cares
 * about, so assertions read as "started here" versus "started on the user's other phone".
 */
internal class FakeDeviceIdProvider(private val id: String = THIS_DEVICE) : DeviceIdProvider {

    override suspend fun deviceId(): String = id

    companion object {
        const val THIS_DEVICE = "11111111-1111-4111-8111-111111111111"
        const val OTHER_DEVICE = "22222222-2222-4222-8222-222222222222"
    }
}
