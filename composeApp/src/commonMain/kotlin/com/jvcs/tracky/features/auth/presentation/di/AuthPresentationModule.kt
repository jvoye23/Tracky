package com.jvcs.tracky.features.auth.presentation.di

import com.jvcs.tracky.features.auth.presentation.emailverification.EmailVerificationViewModel
import com.jvcs.tracky.features.auth.presentation.forgotpassword.ForgotPasswordViewModel
import com.jvcs.tracky.features.auth.presentation.login.LoginViewModel
import com.jvcs.tracky.features.auth.presentation.register.RegisterViewModel
import com.jvcs.tracky.features.auth.presentation.registersuccess.RegisterSuccessViewModel
import com.jvcs.tracky.features.auth.presentation.resetpassword.ResetPasswordViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val authPresentationModule =
    module {
        viewModelOf(::LoginViewModel)
        viewModelOf(::RegisterViewModel)
        viewModelOf(::RegisterSuccessViewModel)
        viewModelOf(::EmailVerificationViewModel)
        viewModelOf(::ForgotPasswordViewModel)
        viewModelOf(::ResetPasswordViewModel)
    }
