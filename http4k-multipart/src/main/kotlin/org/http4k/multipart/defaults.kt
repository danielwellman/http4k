package org.http4k.multipart

import java.nio.charset.Charset

object MultipartDefaults {
    internal const val DEFAULT_BUFSIZE = 4096

    /**
     * The Carriage Return ASCII character value.
     */
    internal const val CR: Byte = 0x0D

    /**
     * The Line Feed ASCII character value.
     */
    internal const val LF: Byte = 0x0A

    /**
     * The dash (-) ASCII character value.
     */
    internal const val DASH: Byte = 0x2D

    /**
     * The maximum length of all headers
     */
    internal const val HEADER_SIZE_MAX = 10 * 1024

    /**
     * A byte sequence that that follows a delimiter that will be
     * followed by an encapsulation (`CRLF`).
     */
    internal val FIELD_SEPARATOR = byteArrayOf(CR, LF)

    /**
     * A byte sequence that that follows a delimiter of the last
     * encapsulation in the stream (`--`).
     */
    internal val STREAM_TERMINATOR = byteArrayOf(DASH, DASH)
}

internal enum class MultipartStreamState {
    FindPrefix, FindBoundary, BoundaryFound, Eos, Header, Contents
}

internal fun prependBoundaryWithStreamTerminator(boundary: ByteArray): ByteArray {
    val actualBoundary = ByteArray(boundary.size + 2)
    System.arraycopy(MultipartDefaults.STREAM_TERMINATOR, 0, actualBoundary, 0, 2)
    System.arraycopy(boundary, 0, actualBoundary, 2, boundary.size)
    return actualBoundary
}

internal fun TokenBoundedInputStream.readStringUntilMatched(endOfToken: ByteArray, maxStringSizeInBytes: Int, encoding: Charset): String {
    // very inefficient search!
    val buffer = ByteArray(maxStringSizeInBytes)
    val bytes = getBytesUntil(endOfToken, buffer, encoding)
    return String(buffer, 0, bytes, encoding)
}
