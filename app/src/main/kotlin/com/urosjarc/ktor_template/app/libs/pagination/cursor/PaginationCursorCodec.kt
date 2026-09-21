package com.urosjarc.ktor_template.app.libs.pagination.cursor

public interface PaginationCursorCodec {
    public fun encode(cursor: PaginationCursor): String
    public fun decode(value: String): PaginationCursor
}
