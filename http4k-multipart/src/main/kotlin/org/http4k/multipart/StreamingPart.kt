package org.http4k.multipart

import org.http4k.core.Headers
import java.io.InputStream

internal class StreamingPart(fieldName: String, type: PartType, contentType: String?, fileName: String?, val inputStream: InputStream, headers: Headers)
    : PartMetaData(fieldName, type, contentType, fileName, headers) {

    val contentsAsString get() = inputStream.use { it.reader().readText() }
}
