package com.jvcs.tracky

data class MainState(
    val isLoggedIn: Boolean = false,
    val isCheckingAuth: Boolean = true,
    val username: String? = null,
    val email: String? = null
)

