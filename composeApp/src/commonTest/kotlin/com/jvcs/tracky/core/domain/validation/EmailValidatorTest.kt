package com.jvcs.tracky.core.domain.validation

import com.jvcs.tracky.features.auth.domain.EmailValidator
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmailValidatorTest {

    @Test
    fun validEmail() {
        assertTrue(EmailValidator.validate("user@example.com"))
    }

    @Test
    fun validEmailWithSubdomain() {
        assertTrue(EmailValidator.validate("user@mail.example.com"))
    }

    @Test
    fun validEmailWithPlus() {
        assertTrue(EmailValidator.validate("user+tag@example.com"))
    }

    @Test
    fun emptyStringIsInvalid() {
        assertFalse(EmailValidator.validate(""))
    }

    @Test
    fun missingAtSymbol() {
        assertFalse(EmailValidator.validate("userexample.com"))
    }

    @Test
    fun missingDomain() {
        assertFalse(EmailValidator.validate("user@"))
    }

    @Test
    fun missingTld() {
        assertFalse(EmailValidator.validate("user@example"))
    }

    @Test
    fun singleCharTld() {
        assertFalse(EmailValidator.validate("user@example.c"))
    }

    @Test
    fun missingLocalPart() {
        assertFalse(EmailValidator.validate("@example.com"))
    }

    @Test
    fun spacesInEmail() {
        assertFalse(EmailValidator.validate("user @example.com"))
    }
}
