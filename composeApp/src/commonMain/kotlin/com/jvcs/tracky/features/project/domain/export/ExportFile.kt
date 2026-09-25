package com.jvcs.tracky.features.project.domain.export

/**
 * A finished export, ready to be written or shared: the name the user will see, its MIME type and
 * its content.
 *
 * [equals] and [hashCode] are overridden because a data class compares a [ByteArray] by identity,
 * which would make two identical exports unequal.
 */
data class ExportFile(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
) {

    override fun equals(other: Any?): Boolean =
        other is ExportFile &&
            fileName == other.fileName &&
            mimeType == other.mimeType &&
            bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = 31 * (31 * fileName.hashCode() + mimeType.hashCode()) + bytes.contentHashCode()
}
