package com.jvcs.tracky.features.project.data.export

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.jvcs.tracky.composeapp.R
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.export.ExportError
import com.jvcs.tracky.features.project.domain.export.ExportFile
import com.jvcs.tracky.features.project.domain.export.ExportFileSharer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Writes the export into `cache/exports/` and opens the system chooser on it through a
 * FileProvider content URI, granting the chosen app read access to that one file only.
 *
 * Returns as soon as the chooser is launched: it runs in its own task and reports nothing back.
 */
class FileProviderExportFileSharer(context: Context, private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) :
    ExportFileSharer {

    private val appContext = context.applicationContext

    override suspend fun share(file: ExportFile): EmptyResult<ExportError> {
        val written = writeToCache(file) ?: return Result.Error(ExportError.WRITE_FAILED)
        return withContext(Dispatchers.Main) {
            try {
                appContext.startActivity(chooserFor(file, written))
                Result.Success(Unit)
            } catch (_: ActivityNotFoundException) {
                Result.Error(ExportError.SHARE_FAILED)
            } catch (_: IllegalArgumentException) {
                // FileProvider rejects a file outside its configured paths.
                Result.Error(ExportError.SHARE_FAILED)
            }
        }
    }

    /**
     * Only the latest export is kept: the previous ones have been handed off already, and the
     * receiving app's read grant does not outlive its task, so nothing still needs them.
     */
    private suspend fun writeToCache(file: ExportFile): File? =
        withContext(ioDispatcher) {
            try {
                val dir = File(appContext.cacheDir, EXPORTS_DIR)
                dir.listFiles()?.forEach(File::delete)
                if (!dir.isDirectory && !dir.mkdirs()) return@withContext null
                // .name drops any path segments, so a file name can never escape the exports dir.
                File(dir, File(file.fileName).name).apply { writeBytes(file.bytes) }
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            }
        }

    private fun chooserFor(file: ExportFile, written: File): Intent {
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}$AUTHORITY_SUFFIX", written)
        val send =
            Intent(Intent.ACTION_SEND)
                .setType(file.mimeType)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // The ClipData carries the grant through the chooser to whichever app the user picks.
        send.clipData = ClipData.newRawUri(file.fileName, uri)
        return Intent
            .createChooser(send, appContext.getString(R.string.export_share_chooser_title))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private companion object {
        const val EXPORTS_DIR = "exports"

        /** Must match the provider's authorities in the androidMain manifest. */
        const val AUTHORITY_SUFFIX = ".exportfileprovider"
    }
}
