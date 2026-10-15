package com.jvcs.tracky.core.domain.purchases

/**
 * Whether the user may use the Pro features on this device: more than five projects, subtasks,
 * export, archive and pin (Requirements/basic_free_user_flow.md).
 */
interface ProStatusProvider {

    /** True when the store or the server says Pro. Unknown counts as free: the paywall offers Restore. */
    suspend fun isPro(): Boolean
}
