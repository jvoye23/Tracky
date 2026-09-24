package com.jvcs.tracky.features.auth.domain

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test

class EmailValidatorTest {

    @Test
    fun validEmail() {
        assertThat(EmailValidator.validate("user@example.com")).isTrue()
    }

    @Test
    fun validEmailWithSubdomain() {
        assertThat(EmailValidator.validate("user@mail.example.com")).isTrue()
    }

    @Test
    fun validEmailWithPlus() {
        assertThat(EmailValidator.validate("user+tag@example.com")).isTrue()
    }

    @Test
    fun emptyStringIsInvalid() {
        assertThat(EmailValidator.validate("")).isFalse()
    }

    @Test
    fun missingAtSymbol() {
        assertThat(EmailValidator.validate("userexample.com")).isFalse()
    }

    @Test
    fun missingDomain() {
        assertThat(EmailValidator.validate("user@")).isFalse()
    }

    @Test
    fun missingTld() {
        assertThat(EmailValidator.validate("user@example")).isFalse()
    }

    @Test
    fun singleCharTld() {
        assertThat(EmailValidator.validate("user@example.c")).isFalse()
    }

    @Test
    fun missingLocalPart() {
        assertThat(EmailValidator.validate("@example.com")).isFalse()
    }

    @Test
    fun spacesInEmail() {
        assertThat(EmailValidator.validate("user @example.com")).isFalse()
    }
}
