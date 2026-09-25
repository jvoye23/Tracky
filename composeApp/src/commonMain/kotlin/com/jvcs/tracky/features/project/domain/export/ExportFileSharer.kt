package com.jvcs.tracky.features.project.domain.export

import com.jvcs.tracky.core.domain.util.EmptyResult

/**
 * Hands a finished [ExportFile] to the user through the platform's own share UI.
 *
 * When [share] returns differs per platform, because each UI reports different things:
 * - Android: once the system chooser has been launched. The chooser runs in another task and
 *   reports nothing back, so the app cannot know whether the user picked a target.
 * - iOS: once the share sheet has been dismissed, whether the user shared or cancelled.
 * - Desktop: once the save dialog closes. Cancelling it is a success that writes nothing.
 *
 * A dismissed or cancelled share is never an error - only failing to write the file
 * ([ExportError.WRITE_FAILED]) or to open the UI ([ExportError.SHARE_FAILED]) is.
 */
interface ExportFileSharer {

    suspend fun share(file: ExportFile): EmptyResult<ExportError>
}
