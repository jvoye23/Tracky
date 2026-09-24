package com.jvcs.tracky.designsystem.util

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

sealed interface UiText {

    data class DynamicString(val value: String) : UiText

    class Resource(val id: StringResource, val args: Array<Any> = arrayOf()) : UiText {

        // Hand-written because an Array field makes a data class fall back to identity equality,
        // which would make two UiTexts for the same string resource compare unequal.
        override fun equals(other: Any?): Boolean =
            this === other || (other is Resource && id == other.id && args.contentEquals(other.args))

        override fun hashCode(): Int = 31 * id.hashCode() + args.contentHashCode()

        override fun toString(): String = "Resource(id=$id, args=${args.contentToString()})"
    }

    @Composable
    // Compose resources take format args only as varargs, and UiText stores them as an array.
    @Suppress("SpreadOperator")
    fun asString(): String =
        when (this) {
            is DynamicString -> {
                value
            }

            is Resource -> {
                stringResource(
                    resource = id,
                    *args,
                )
            }
        }

    // Compose resources take format args only as varargs, and UiText stores them as an array.
    @Suppress("SpreadOperator")
    suspend fun asStringAsync(): String =
        when (this) {
            is DynamicString -> {
                value
            }

            is Resource -> {
                getString(
                    resource = id,
                    *args,
                )
            }
        }
}
