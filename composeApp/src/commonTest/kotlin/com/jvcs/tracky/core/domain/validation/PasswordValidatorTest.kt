package com.jvcs.tracky.core.domain.validation

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.jvcs.tracky.features.auth.domain.PasswordValidator
import kotlin.test.Test

class PasswordValidatorTest {

    @Test
    fun validPassword() {
        val result = PasswordValidator.validate("Test@123")
        assertThat(result.isValidPassword).isTrue()
        assertThat(result.hasMinLength).isTrue()
        assertThat(result.hasDigit).isTrue()
        assertThat(result.hasUppercase).isTrue()
        assertThat(result.hasLowercase).isTrue()
        assertThat(result.hasSpecialChar).isTrue()
    }

    @Test
    fun tooShort() {
        val result = PasswordValidator.validate("Te@1abc")
        assertThat(result.hasMinLength).isFalse()
        assertThat(result.isValidPassword).isFalse()
    }

    @Test
    fun noDigit() {
        val result = PasswordValidator.validate("Test@abcd")
        assertThat(result.hasMinLength).isTrue()
        assertThat(result.hasDigit).isFalse()
        assertThat(result.isValidPassword).isFalse()
    }

    @Test
    fun noUppercase() {
        val result = PasswordValidator.validate("test@1234")
        assertThat(result.hasUppercase).isFalse()
        assertThat(result.isValidPassword).isFalse()
    }

    @Test
    fun noLowercase() {
        val result = PasswordValidator.validate("TEST@1234")
        assertThat(result.hasLowercase).isFalse()
        assertThat(result.isValidPassword).isFalse()
    }

    @Test
    fun noSpecialChar() {
        val result = PasswordValidator.validate("Testabcd1")
        assertThat(result.hasSpecialChar).isFalse()
        assertThat(result.isValidPassword).isFalse()
    }

    @Test
    fun emptyString() {
        val result = PasswordValidator.validate("")
        assertThat(result.hasMinLength).isFalse()
        assertThat(result.hasDigit).isFalse()
        assertThat(result.hasUppercase).isFalse()
        assertThat(result.hasLowercase).isFalse()
        assertThat(result.hasSpecialChar).isFalse()
        assertThat(result.isValidPassword).isFalse()
    }

    @Test
    fun exactMinLength() {
        val result = PasswordValidator.validate("Te@1abcd")
        assertThat(result.hasMinLength).isTrue()
        assertThat(result.isValidPassword).isTrue()
    }
}
