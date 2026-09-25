package com.jvcs.tracky.features.project.domain.export

import com.jvcs.tracky.core.domain.util.Error

/** Why exporting a project (as JSON or PDF) and handing it to the user failed. */
enum class ExportError : Error {
    /** The project to export no longer exists locally. */
    PROJECT_NOT_FOUND,

    /** The project could not be encoded into the export format. */
    SERIALIZATION,

    /** The encoded export could not be written to storage. */
    WRITE_FAILED,

    /** The PDF could not be drawn from the project report. */
    RENDER_FAILED,

    /** The platform share sheet could not be opened for the written file. */
    SHARE_FAILED,
}
