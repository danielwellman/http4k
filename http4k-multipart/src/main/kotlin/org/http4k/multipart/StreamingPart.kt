package org.http4k.multipart

import java.io.InputStream

internal class StreamingPart(fieldName: String, type: PartType, contentType: String?, fileName: String?, val inputStream: InputStream, headers: Map<String, String>)
    : PartMetaData(fieldName, type, contentType, fileName, headers) {

    val contentsAsString get() = inputStream.use { it.reader().readText() }
}
