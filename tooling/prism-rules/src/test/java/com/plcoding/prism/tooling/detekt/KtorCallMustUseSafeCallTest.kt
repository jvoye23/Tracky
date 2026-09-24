package com.plcoding.prism.tooling.detekt

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import dev.detekt.api.Config
import dev.detekt.test.TestConfig
import dev.detekt.test.lint
import org.junit.jupiter.api.Test

class KtorCallMustUseSafeCallTest {
    private val rule = KtorCallMustUseSafeCall(Config.empty)

    @Test
    fun `reports a raw verb on the client`() {
        assertThat(rule.lint("suspend fun f() { httpClient.get { url(\"x\") } }")).hasSize(1)
    }

    @Test
    fun `does not report a verb inside safeCall`() {
        assertThat(rule.lint("suspend fun f() = safeCall { httpClient.get { url(\"x\") } }")).isEmpty()
    }

    @Test
    fun `honors a configured block wrapper`() {
        val configured = KtorCallMustUseSafeCall(TestConfig("safeWrappers" to listOf("safeResponse")))

        assertThat(configured.lint("suspend fun f() = safeResponse { httpClient.get { url(\"x\") } }")).isEmpty()
    }

    @Test
    fun `a configured overload argument marks the project's safe verb`() {
        val configured = KtorCallMustUseSafeCall(TestConfig("safeOverloadArgument" to "route"))
        val snippet = "suspend fun f() = httpClient.post<Req, Res>(route = \"/api\", body = req)"

        assertThat(configured.lint(snippet)).isEmpty()
        assertThat(rule.lint(snippet)).hasSize(1)
    }

    @Test
    fun `the overload argument does not excuse a raw builder call`() {
        val configured = KtorCallMustUseSafeCall(TestConfig("safeOverloadArgument" to "route"))

        assertThat(configured.lint("suspend fun f() { httpClient.put { url(\"x\") } }")).hasSize(1)
    }
}
