package com.jvcs.androidapp

import org.junit.runner.Description
import org.junit.runner.manipulation.Filter

/**
 * Leaves out every `*E2ETest`: they drive the live backend and a real mailbox, so a coverage run
 * without network or credentials would hang on them. Named in the runner's `filter` argument,
 * which the build sets unless `-Pe2e` asks for them.
 */
class SkipE2eTests : Filter() {
    override fun shouldRun(description: Description) = description.className?.endsWith("E2ETest") != true

    override fun describe() = "skip *E2ETest"
}
