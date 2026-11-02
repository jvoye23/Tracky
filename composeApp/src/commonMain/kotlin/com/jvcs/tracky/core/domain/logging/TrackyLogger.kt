package com.jvcs.tracky.core.domain.logging

interface TrackyLogger {

    fun debug(message: String)

    fun info(message: String)

    fun warn(message: String)

    fun error(message: String, throwable: Throwable? = null)
}
