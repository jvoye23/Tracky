package com.jvcs.tracky.features.project.domain.project

/** Hands out the first-project claim once, like the persisted store. */
class FakeFirstProjectStore(private var isClaimed: Boolean = false) : FirstProjectStore {

    override suspend fun claimFirst(): Boolean {
        val claimed = !isClaimed
        isClaimed = true
        return claimed
    }
}
