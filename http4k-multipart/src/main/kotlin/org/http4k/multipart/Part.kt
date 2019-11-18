package org.http4k.multipart

import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.file.FileSystemException

enum class PartType {
    File, Field
}
internal sealed class Part(fieldName: String?, type: PartType, contentType: String?, fileName: String?, headers: Map<String, String>, val length: Int) : PartMetaData(fieldName, type, contentType, fileName, headers), Closeable {

    abstract val newInputStream: InputStream

    abstract val bytes: ByteArray

    class DiskBacked(part: PartMetaData, private val theFile: File) : Part(part.fieldName, part.type, part.contentType, part.fileName, part.headers, theFile.length().toInt()) {
        override val newInputStream: InputStream
            get() = FileInputStream(theFile)

        override val bytes
            get() = throw IllegalStateException("Cannot get bytes from a DiskBacked Part")

        override fun close() {
            if (!theFile.delete()) throw FileSystemException("Failed to delete file")
        }
    }

    class InMemory(original: PartMetaData,
                   override val bytes: ByteArray /* not immutable*/,
                   internal val encoding: Charset)
        : Part(original.fieldName, original.type, original.contentType, original.fileName, original.headers, bytes.size) {

        override val newInputStream: InputStream
            get() = ByteArrayInputStream(bytes)

        override fun close() {
            // do nothing
        }
    }

}
