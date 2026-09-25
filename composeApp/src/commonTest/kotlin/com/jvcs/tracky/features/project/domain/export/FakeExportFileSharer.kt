package com.jvcs.tracky.features.project.domain.export

import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result

/**
 * Records every file it is asked to share and answers with [result] - success unless a test sets
 * an [ExportError] to make the share fail.
 */
class FakeExportFileSharer(var result: EmptyResult<ExportError> = Result.Success(Unit)) : ExportFileSharer {

    val sharedFiles = mutableListOf<ExportFile>()

    override suspend fun share(file: ExportFile): EmptyResult<ExportError> {
        sharedFiles += file
        return result
    }
}
