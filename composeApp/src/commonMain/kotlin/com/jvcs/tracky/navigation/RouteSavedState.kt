package com.jvcs.tracky.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

// Registration lives next to the route declarations in Route.kt on purpose: a route added
// there without a matching subclass() entry here restores as a SerializationException rather
// than failing at compile time.

val routeSavedStateConfiguration: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Route.AuthRoute.Login::class, Route.AuthRoute.Login.serializer())
            subclass(Route.AuthRoute.Register::class, Route.AuthRoute.Register.serializer())
            subclass(Route.AuthRoute.RegisterSuccess::class, Route.AuthRoute.RegisterSuccess.serializer())
            subclass(Route.AuthRoute.EmailVerification::class, Route.AuthRoute.EmailVerification.serializer())
            subclass(Route.AuthRoute.ForgotPassword::class, Route.AuthRoute.ForgotPassword.serializer())
            subclass(Route.AuthRoute.ResetPassword::class, Route.AuthRoute.ResetPassword.serializer())
            subclass(Route.ProjectRoute.ProjectOverview::class, Route.ProjectRoute.ProjectOverview.serializer())
            subclass(Route.ProjectRoute.ProjectArchive::class, Route.ProjectRoute.ProjectArchive.serializer())
            subclass(Route.ProjectRoute.ProjectArchiveDetail::class, Route.ProjectRoute.ProjectArchiveDetail.serializer())
            subclass(Route.ProjectRoute.ProjectTrash::class, Route.ProjectRoute.ProjectTrash.serializer())
            subclass(Route.ProjectRoute.ProjectDetail::class, Route.ProjectRoute.ProjectDetail.serializer())
            subclass(Route.ProjectRoute.EditTextNavKey::class, Route.ProjectRoute.EditTextNavKey.serializer())
            subclass(Route.ProjectRoute.TaskDetail::class, Route.ProjectRoute.TaskDetail.serializer())
        }
    }
}
