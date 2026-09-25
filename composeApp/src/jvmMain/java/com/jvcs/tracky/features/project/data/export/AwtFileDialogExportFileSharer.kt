package com.jvcs.tracky.features.project.data.export

import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.export.ExportError
import com.jvcs.tracky.features.project.domain.export.ExportFile
import com.jvcs.tracky.features.project.domain.export.ExportFileSharer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.IOException

/**
 * Desktop has no share sheet, so "sharing" is a native save dialog pre-filled with the export's
 * name. Suspends until the dialog closes; cancelling it is a success that writes nothing.
 */
class AwtFileDialogExportFileSharer(private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) :
    ExportFileSharer {

    override suspend fun share(file: ExportFile): EmptyResult<ExportError> {
        val target = withContext(Dispatchers.Swing) { chooseTarget(file.fileName) } ?: return Result.Success(Unit)
        return writeExport(target, file.bytes, ioDispatcher)
    }

    /** The file the user picked, or null when they cancelled. Blocks the EDT while modal, as AWT expects. */
    private fun chooseTarget(fileName: String): File? {
        val dialog = FileDialog(null as Frame?, DIALOG_TITLE, FileDialog.SAVE).apply { file = fileName }
        try {
            dialog.isVisible = true
            val directory = dialog.directory ?: return null
            val name = dialog.file ?: return null
            return File(directory, name)
        } finally {
            dialog.dispose()
        }
    }

    private companion object {
        const val DIALOG_TITLE = "Save export"
    }
}

/** Writes [bytes] to [target], mapping any I/O failure to [ExportError.WRITE_FAILED]. */
internal suspend fun writeExport(
    target: File,
    bytes: ByteArray,
    ioDispatcher: CoroutineDispatcher,
): EmptyResult<ExportError> =
    withContext(ioDispatcher) {
        try {
            target.writeBytes(bytes)
            Result.Success(Unit)
        } catch (_: IOException) {
            Result.Error(ExportError.WRITE_FAILED)
        } catch (_: SecurityException) {
            Result.Error(ExportError.WRITE_FAILED)
        }
    }
