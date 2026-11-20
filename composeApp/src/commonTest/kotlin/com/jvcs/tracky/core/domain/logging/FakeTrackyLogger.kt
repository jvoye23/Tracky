package com.jvcs.tracky.core.domain.logging

/** Drops everything: for tests that only need something to satisfy a [TrackyLogger] parameter. */
class FakeTrackyLogger : TrackyLogger {

    override fun debug(message: String) = Unit

    override fun info(message: String) = Unit

    override fun warn(message: String) = Unit

    override fun error(message: String, throwable: Throwable?) = Unit
}
