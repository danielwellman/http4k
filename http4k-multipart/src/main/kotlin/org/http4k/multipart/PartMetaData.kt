package org.http4k.multipart

import org.http4k.core.Headers

internal abstract class PartMetaData(val fieldName: String?, val type: PartType, val contentType: String?, val fileName: String?, val headers: Headers)
