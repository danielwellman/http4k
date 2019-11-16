package org.http4k.core

import java.util.UUID

object MultipartDefaults {
    val MULTIPART_BOUNDARY = UUID.randomUUID().toString()
    const val DEFAULT_DISK_THRESHOLD = 1000 * 1024
}
