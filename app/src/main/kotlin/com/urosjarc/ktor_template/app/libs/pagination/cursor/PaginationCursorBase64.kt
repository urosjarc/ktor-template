package com.urosjarc.ktor_template.app.libs.pagination.cursor

import com.urosjarc.ktor_template.core.services.JsonService
import java.util.Base64.getUrlDecoder
import java.util.Base64.getUrlEncoder

public object PaginationCursorBase64 : PaginationCursorCodec {
    override fun encode(cursor: PaginationCursor): String = getUrlEncoder().withoutPadding().encodeToString(
        JsonService.module.encodeToString(
            PaginationCursor(
                queryHash = cursor.queryHash,
                sort = cursor.sort,
                values = cursor.values,
            )
        ).encodeToByteArray()
    )

    override fun decode(value: String): PaginationCursor {
        val decoded = try {
            String(getUrlDecoder().decode(value), Charsets.UTF_8)
        } catch (cause: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid pagination cursor", cause)
        }
        val payload = try {
            JsonService.module.decodeFromString<PaginationCursor>(decoded)
        } catch (cause: Exception) {
            throw IllegalArgumentException("Invalid pagination cursor", cause)
        }

        return PaginationCursor(
            queryHash = payload.queryHash,
            sort = payload.sort,
            values = payload.values,
        )
    }
}
