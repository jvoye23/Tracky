package com.jvcs.tracky.features.project.domain.export

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private const val MAX_TITLE_LENGTH = 60
private const val FALLBACK_TITLE = "project"

/**
 * The name every export of a project is shared under, whatever its format:
 * `<sanitized title>_<yyyy-MM-dd>.<extension>`, dated in the user's [timeZone].
 */
internal fun exportFileName(
    title: String,
    exportedAt: Instant,
    timeZone: TimeZone,
    extension: String,
): String = "${title.toFileNameStem()}_${exportedAt.toLocalDateTime(timeZone).date}.$extension"

/**
 * Anything but letters, digits, '-' and '_' — which covers every character a file system or a share
 * target might reject — collapses into a single '_'. A loop rather than a regex: Kotlin/Native's
 * engine handles '-' inside a character class differently from the JVM's.
 */
private fun String.toFileNameStem(): String =
    buildString {
        for (char in this@toFileNameStem) {
            when {
                char.isLetterOrDigit() || char == '-' || char == '_' -> append(char)
                !endsWith('_') -> append('_')
            }
        }
    }.trim('_')
        .take(MAX_TITLE_LENGTH)
        .ifEmpty { FALLBACK_TITLE }
