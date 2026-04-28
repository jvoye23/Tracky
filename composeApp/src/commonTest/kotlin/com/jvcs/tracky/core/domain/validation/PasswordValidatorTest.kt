package com.jvcs.tracky.core.domain.validation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasswordValidatorTest {

    @Test
    fun validPassword() {
        val result = PasswordValidator.validate("Test@123")
        assertTrue(result.isValidPassword)
        assertTrue(result.hasMinLength)
        assertTrue(result.hasDigit)
        assertTrue(result.hasUppercase)
        assertTrue(result.hasLowercase)
        assertTrue(result.hasSpecialChar)
    }

    @Test
    fun tooShort() {
        val result = PasswordValidator.validate("Te@1abc")
        assertFalse(result.hasMinLength)
        assertFalse(result.isValidPassword)
    }

    @Test
    fun noDigit() {
        val result = PasswordValidator.validate("Test@abcd")
        assertTrue(result.hasMinLength)
        assertFalse(result.hasDigit)
        assertFalse(result.isValidPassword)
    }

    @Test
    fun noUppercase() {
        val result = PasswordValidator.validate("test@1234")
        assertFalse(result.hasUppercase)
        assertFalse(result.isValidPassword)
    }

    @Test
    fun noLowercase() {
        val result = PasswordValidator.validate("TEST@1234")
        assertFalse(result.hasLowercase)
        assertFalse(result.isValidPassword)
    }

    @Test
    fun noSpecialChar() {
        val result = PasswordValidator.validate("Testabcd1")
        assertFalse(result.hasSpecialChar)
        assertFalse(result.isValidPassword)
    }

    @Test
    fun emptyString() {
        val result = PasswordValidator.validate("")
        assertFalse(result.hasMinLength)
        assertFalse(result.hasDigit)
        assertFalse(result.hasUppercase)
        assertFalse(result.hasLowercase)
        assertFalse(result.hasSpecialChar)
        assertFalse(result.isValidPassword)
    }

    @Test
    fun exactMinLength() {
        val result = PasswordValidator.validate("Te@1abcd")
        assertTrue(result.hasMinLength)
        assertTrue(result.isValidPassword)
    }
}
