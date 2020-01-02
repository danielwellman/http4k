package org.http4k.multipart

import org.http4k.core.MultipartDefaults.MULTIPART_BOUNDARY
import org.http4k.core.Parameters
import org.http4k.multipart.MultipartDefaults.FIELD_SEPARATOR
import org.http4k.multipart.MultipartDefaults.STREAM_TERMINATOR
import java.io.InputStream
import java.io.SequenceInputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.Collections

internal class MultipartFormBuilder(inBoundary: ByteArray, private val encoding: Charset = Charset.defaultCharset()) {
    private val boundary = ArrayDeque<ByteArray>()

    private val waitingToStream = mutableListOf<InputStream>()

    constructor(boundary: String = MULTIPART_BOUNDARY) : this(boundary.toByteArray(StandardCharsets.UTF_8), StandardCharsets.UTF_8)

    init {
        boundary.push(prependBoundaryWithStreamTerminator(inBoundary))
    }

    fun stream(): InputStream {
        add(boundary.peek())
        add(STREAM_TERMINATOR)
        add(FIELD_SEPARATOR)

        return SequenceInputStream(Collections.enumeration(waitingToStream))
    }

    fun field(name: String, value: String, headers: Parameters): MultipartFormBuilder = apply {
        part(value, listOf("Content-Disposition" to """form-data; name="$name"""") + headers)
    }

    private fun appendHeader(headerName: String, headerValue: String?) {
        val headerLine = "$headerName: ${headerValue.orEmpty()}"

        add(headerLine.toByteArray(encoding))
        add(FIELD_SEPARATOR)
    }

    fun part(contents: String, headers: Parameters) =
        part(contents.byteInputStream(encoding), headers)

    fun part(contents: InputStream, headers: Parameters) = apply {
        add(boundary.peek())
        add(FIELD_SEPARATOR)
        if (headers.isNotEmpty()) {
            headers.toList().forEach { (first, second) -> appendHeader(first, second) }
            add(FIELD_SEPARATOR)
        }
        waitingToStream.add(contents)
        add(FIELD_SEPARATOR)
    }

    private fun add(bytes: ByteArray) {
        waitingToStream.add(bytes.inputStream())
    }

    fun startMultipart(multipartFieldName: String, subpartBoundary: String): MultipartFormBuilder = apply {
        add(boundary.peek())
        add(FIELD_SEPARATOR)
        appendHeader("Content-Disposition", """form-data; name="$multipartFieldName"""")
        appendHeader("Content-Type", """multipart/mixed; boundary="$subpartBoundary"""")
        add(FIELD_SEPARATOR)
        boundary.push((String(STREAM_TERMINATOR, encoding) + subpartBoundary).toByteArray(encoding))
    }

    fun attachment(fileName: String, contentType: String, contents: String,
                   headers: Parameters) =
        part(contents,
            listOf(
                "Content-Disposition" to """attachment; filename="$fileName"""",
                "Content-Type" to contentType) + headers
        )

    fun file(fieldName: String, filename: String, contentType: String, contents: InputStream,
             headers: Parameters) =
        part(contents,
            listOf(
                "Content-Disposition" to """form-data; name="$fieldName"; filename="$filename"""",
                "Content-Type" to contentType) + headers
        )

    fun endMultipart(): MultipartFormBuilder = apply {
        add(boundary.pop())
        add(STREAM_TERMINATOR)
        add(FIELD_SEPARATOR)
    }
}
