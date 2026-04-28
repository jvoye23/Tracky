package com.jvcs.tracky

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform