package org.http4k.multipart

import org.apache.commons.fileupload.util.ParameterParser
import org.http4k.multipart.MultipartDefaults.DEFAULT_BUFSIZE
import org.http4k.multipart.MultipartDefaults.FIELD_SEPARATOR
import org.http4k.multipart.MultipartDefaults.HEADER_SIZE_MAX
import org.http4k.multipart.MultipartDefaults.STREAM_TERMINATOR
import org.http4k.multipart.MultipartStreamState.BoundaryFound
import org.http4k.multipart.MultipartStreamState.Contents
import org.http4k.multipart.MultipartStreamState.Eos
import org.http4k.multipart.MultipartStreamState.FindBoundary
import org.http4k.multipart.MultipartStreamState.FindPrefix
import org.http4k.multipart.MultipartStreamState.Header
import org.http4k.multipart.PartType.Field
import org.http4k.multipart.PartType.File
import java.io.IOException
import java.io.InputStream
import java.lang.String.CASE_INSENSITIVE_ORDER
import java.nio.charset.Charset
import java.util.NoSuchElementException
import java.util.TreeMap

/**
 * [RFC 1867](http://www.ietf.org/rfc/rfc1867.txt)
 */
internal class StreamingMultipartFormParts private constructor(inBoundary: ByteArray, private val encoding: Charset, private val inputStream: TokenBoundedInputStream) : Iterable<StreamingPart> {
    private val iterator = StreamingMultipartFormPartIterator()

    private var boundary = prependBoundaryWithStreamTerminator(inBoundary)
    private var boundaryWithPrefix = addPrefixToBoundary(boundary)
    private var state = FindBoundary
    // yes yes, I should use a stack or something for this
    private var mixedName: String? = null
    private var oldBoundary = inBoundary
    private var oldBoundaryWithPrefix = boundaryWithPrefix

    override fun iterator() = iterator

    private fun addPrefixToBoundary(boundary: ByteArray?): ByteArray =
        // in apache they just use BOUNDARY_PREFIX
        ByteArray(boundary!!.size + FIELD_SEPARATOR.size).apply {
            System.arraycopy(boundary, 0, this, 2, boundary.size)
            System.arraycopy(FIELD_SEPARATOR, 0, this, 0, FIELD_SEPARATOR.size)
        }

    private fun findBoundary() {
        if (state == FindPrefix) {
            if (!inputStream.matchInStream(FIELD_SEPARATOR)) throw TokenNotFoundException("Boundary must be proceeded by field separator, but didn't find it")
            state = FindBoundary
        }

        if (state == FindBoundary && !inputStream.matchInStream(boundary)) throw TokenNotFoundException("Boundary not found <<" + String(boundary, encoding) + ">>")

        state = BoundaryFound
        if (inputStream.matchInStream(STREAM_TERMINATOR)) {
            if (!inputStream.matchInStream(FIELD_SEPARATOR)) throw TokenNotFoundException("Stream terminator must be followed by field separator, but didn't find it")
            when {
                mixedName != null -> {
                    boundary = oldBoundary
                    boundaryWithPrefix = oldBoundaryWithPrefix
                    mixedName = null

                    state = FindBoundary
                    findBoundary()
                }
                else -> state = Eos
            }
        } else {
            state = if (!inputStream.matchInStream(FIELD_SEPARATOR)) throw TokenNotFoundException("Boundary must be followed by field separator, but didn't find it")
            else Header
        }
    }

    private fun parseNextPart(): StreamingPart? = findBoundary().run {
        if (state == Header) parsePart() else null
    }

    private fun parsePart(): StreamingPart? {
        val headers = parseHeaderLines()

        val contentType = headers["Content-Type"]
        return if (contentType != null && contentType.startsWith("multipart/mixed")) {
            val contentDisposition = ParameterParser().parse(headers["Content-Disposition"])
            val contentTypeParams = ParameterParser().parse(contentType)

            mixedName = contentDisposition["name"].trim()

            oldBoundary = boundary
            oldBoundaryWithPrefix = boundaryWithPrefix
            boundary = (String(STREAM_TERMINATOR, encoding) + contentTypeParams["boundary"].trim()!!).toByteArray(encoding)
            boundaryWithPrefix = addPrefixToBoundary(boundary)

            state = FindBoundary

            parseNextPart()
        } else {
            val contentDisposition = ParameterParser().parse(headers["Content-Disposition"])
            val fieldName = (if (contentDisposition.containsKey("attachment")) mixedName else contentDisposition["name"].trim())
                ?: throw ParseError("no name for part")

            StreamingPart(
                fieldName,
                if(contentDisposition.containsKey("filename")) File else Field,
                contentType,
                filenameFromMap(contentDisposition),
                BoundedInputStream(),
                headers.toList())
        }
    }

    private fun filenameFromMap(contentDisposition: Map<String, String>): String? = if (contentDisposition.containsKey("filename")) (contentDisposition["filename"]
        ?: "").trim() else null

    private fun String?.trim(): String? = this?.trim { it <= ' ' }

    private fun parseHeaderLines(): Map<String, String?> {
        if (Header != state) throw IllegalStateException("Expected state $Header but got $state")

        val result = TreeMap<String, String?>(CASE_INSENSITIVE_ORDER)
        var previousHeaderName: String? = null
        val maxByteIndexForHeader = inputStream.currentByteIndex() + HEADER_SIZE_MAX
        while (inputStream.currentByteIndex() < maxByteIndexForHeader) {
            val header = inputStream.readStringUntilMatched(FIELD_SEPARATOR, (maxByteIndexForHeader - inputStream.currentByteIndex()).toInt(), encoding)
            when {
                header == "" -> {
                    state = Contents
                    return result
                }
                header.matches("\\s+.*".toRegex()) -> result[previousHeaderName!!] = result[previousHeaderName] + "; " + header.trim { it <= ' ' }
                else -> {
                    val index = header.indexOf(":")
                    if (index < 0) throw ParseError("Header didn't include a colon <<$header>>")
                    else {
                        previousHeaderName = header.substring(0, index).trim { it <= ' ' }
                        result[previousHeaderName] = header.substring(index + 1).trim { it <= ' ' }
                    }
                }
            }
        }
        throw TokenNotFoundException("Didn't find end of Header section within $HEADER_SIZE_MAX bytes")
    }

    inner class StreamingMultipartFormPartIterator : Iterator<StreamingPart> {
        private var nextIsKnown = false
        private var currentPart: StreamingPart? = null

        override fun hasNext(): Boolean {
            if (!nextIsKnown) {
                nextIsKnown = true

                if (state == Contents) {
                    currentPart!!.inputStream.close()
                }

                currentPart = safelyParseNextPart()
            }

            return !isEndOfStream()
        }

        /**
         * Returns the next element in the iteration.
         *
         * @return the next element in the iteration
         * @throws NoSuchElementException if the iteration has no more elements
         * @throws ParseError             if there was a problem parsing the stream
         */
        override fun next(): StreamingPart {
            if (nextIsKnown) {
                if (isEndOfStream()) throw NoSuchElementException("No more parts in this MultipartForm")
                nextIsKnown = false
            } else {

                if (state == Contents) currentPart!!.inputStream.close()

                currentPart = safelyParseNextPart()
                if (isEndOfStream()) throw NoSuchElementException("No more parts in this MultipartForm")
            }
            return currentPart!!
        }

        private fun safelyParseNextPart(): StreamingPart? =
            try {
                parseNextPart()
            } catch (e: IOException) {
                nextIsKnown = true
                currentPart = null
                throw ParseError(e)
            }

        private fun isEndOfStream() = currentPart == null
    }

    private inner class BoundedInputStream : InputStream() {

        private var endOfStream = false
        private var closed = false

        override fun read(): Int = if (closed) throw AlreadyClosedException() else if (endOfStream) -1 else readNextByte()

        private fun readNextByte(): Int {
            val result = inputStream.readByteFromStreamUnlessTokenMatched(boundaryWithPrefix)
            return when (result) {
                -1 -> {
                    state = FindPrefix
                    endOfStream = true
                    -1
                }
                -2 -> {
                    state = BoundaryFound
                    endOfStream = true
                    -1 // inputStream.read(byte b[], int off, int len) checks for exactly -1
                }
                else -> result
            }
        }

        override fun close() {
            closed = true
            if (!endOfStream) {
                try {
                    while (readNextByte() >= 0) {
                        // drop unwanted bytes :(
                    }
                } catch (e: IOException) {
                    endOfStream = true
                    throw ParseError(e)
                }

            }
        }
    }

    companion object {
        /**
         * Uses the `boundary` to parse the `encoding` coded `inputStream`,
         * returning an `Iterable` of `StreamingPart`s.
         * <br></br>
         * You need to look after closing the inputStream yourself.
         * <br></br>
         * The Iterable can throw a number of `ParseError` Exceptions that you should look
         * out for. Sorry :( They will only happen if something goes wrong with the parsing, which
         * it shouldn't ;)
         *
         * @param boundary    byte array defining the boundary between parts, usually found in the
         * Content-Type header of an HTTP request
         * @param inputStream of the body of an HTTP request - will be parsed as `multipart/form-data`
         * @param encoding    of the body of the HTTP request
         * @return an `Iterable<StreamingPart>` that you can for() through to get each part
         * @throws IOException
         */
        fun parse(boundary: ByteArray, inputStream: InputStream, encoding: Charset): Iterable<StreamingPart> =
            StreamingMultipartFormParts(boundary, encoding, TokenBoundedInputStream(inputStream, DEFAULT_BUFSIZE))

        fun parse(boundary: ByteArray, inputStream: InputStream, encoding: Charset, maxStreamLength: Int): Iterable<StreamingPart> =
            StreamingMultipartFormParts(boundary, encoding, TokenBoundedInputStream(inputStream, DEFAULT_BUFSIZE, maxStreamLength))
    }
}
