package com.jvcs.tracky.features.project.data.export

import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.platformIoDispatcher
import com.jvcs.tracky.features.project.domain.export.ExportError
import com.jvcs.tracky.features.project.domain.export.ExportFile
import com.jvcs.tracky.features.project.domain.export.ExportFileSharer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithBytes
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController
import kotlin.coroutines.resume

/**
 * Writes the export into `tmp/exports/` and presents the system share sheet on it from the
 * top-most view controller.
 *
 * Suspends until the sheet is dismissed - shared or cancelled alike - so a caller's
 * "exporting" state covers the whole time the sheet is on screen.
 */
@OptIn(ExperimentalForeignApi::class)
class UiActivityExportFileSharer : ExportFileSharer {

    override suspend fun share(file: ExportFile): EmptyResult<ExportError> {
        val url =
            withContext(platformIoDispatcher) { writeToTemp(file) } ?: return Result.Error(ExportError.WRITE_FAILED)
        return withContext(Dispatchers.Main) { present(url) }
    }

    /** Only the latest export is kept; earlier ones were handed off when their sheet closed. */
    private fun writeToTemp(file: ExportFile): NSURL? {
        val fileManager = NSFileManager.defaultManager
        val dir = NSTemporaryDirectory().trimEnd('/') + "/$EXPORTS_DIR"
        fileManager.removeItemAtPath(dir, error = null)
        val created =
            fileManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
        if (!created) return null

        // substringAfterLast drops any path segments, so a file name can never escape the dir.
        val path = "$dir/${file.fileName.substringAfterLast('/')}"
        return if (file.bytes.toNSData().writeToFile(path, atomically = true)) NSURL.fileURLWithPath(path) else null
    }

    private suspend fun present(url: NSURL): EmptyResult<ExportError> {
        val presenter = topViewController() ?: return Result.Error(ExportError.SHARE_FAILED)
        return suspendCancellableCoroutine { continuation ->
            val sheet = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
            sheet.completionWithItemsHandler = { _, _, _, _ ->
                if (continuation.isActive) continuation.resume(Result.Success(Unit))
            }
            // iPad shows the sheet as a popover, which crashes without an anchor.
            sheet.popoverPresentationController?.let { popover ->
                popover.sourceView = presenter.view
                popover.sourceRect =
                    presenter.view.bounds.useContents { CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0) }
            }
            presenter.presentViewController(sheet, animated = true, completion = null)
        }
    }

    private fun topViewController(): UIViewController? {
        val keyWindow =
            UIApplication.sharedApplication.connectedScenes
                .filterIsInstance<UIWindowScene>()
                .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
                .firstOrNull { it.isKeyWindow() }
        var top = keyWindow?.rootViewController
        while (top?.presentedViewController != null) top = top.presentedViewController
        return top
    }

    private fun ByteArray.toNSData(): NSData =
        if (isEmpty()) {
            NSData()
        } else {
            usePinned { NSData.dataWithBytes(it.addressOf(0), size.toULong()) }
        }

    private companion object {
        const val EXPORTS_DIR = "exports"
    }
}
