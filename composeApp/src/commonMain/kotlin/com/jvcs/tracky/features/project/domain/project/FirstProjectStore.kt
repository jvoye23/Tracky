package com.jvcs.tracky.features.project.domain.project

/**
 * Remembers that this installation has created its first project. Logout does not reset it: it
 * deletes every project, but the first one was still created long before.
 */
interface FirstProjectStore {

    /** True for exactly one call over the life of the installation, and false for every call after it. */
    suspend fun claimFirst(): Boolean
}
