package com.jvcs.tracky.core.domain.timer

/**
 * Whether an interval was opened somewhere other than here.
 *
 * One function rather than the comparison written out at each call site, because the null case is
 * the one that is easy to get backwards and expensive to get wrong. **Null means "this device"**:
 * every interval written before the column existed was opened here, and reading null as foreign
 * would make this device's own crashed timers unrecoverable — nothing would ever offer to reclaim
 * them. See [com.jvcs.tracky.core.domain.device.DeviceIdProvider].
 *
 * What hangs off the answer: a foreign timer is stopped through the server without banking its
 * duration locally, because the server's task row already carries that time.
 */
fun isForeignTimer(startedByDeviceId: String?, thisDeviceId: String): Boolean =
    startedByDeviceId != null && startedByDeviceId != thisDeviceId
