package com.urosjarc.ktor_template.app.libs.pagination.setup

import org.jetbrains.exposed.v1.core.EnumerationNameColumnType
import org.jetbrains.exposed.v1.core.IColumnType

public enum class PaginationType {
    ENUM, STRING, INTEGER, DECIMAL, BOOLEAN, UUID, DATE, DATETIME, BINARY, ENCRYPTED;

    public companion object {
        public fun from(columnType: IColumnType<*>): PaginationType {
            val sqlType = columnType.sqlType().uppercase().trim()

            return when {
                sqlType.startsWith("VARCHAR") || sqlType.startsWith("CHAR") || sqlType.startsWith("TEXT") || sqlType.startsWith("CLOB") -> STRING

                sqlType.startsWith("TINYINT") || sqlType.startsWith("SMALLINT") || sqlType.startsWith("INTEGER") || sqlType.startsWith("INT") || sqlType.startsWith(
                    "BIGINT"
                ) -> INTEGER

                sqlType.startsWith("REAL") || sqlType.startsWith("DOUBLE") || sqlType.startsWith("FLOAT") || sqlType.startsWith("DECIMAL") || sqlType.startsWith(
                    "NUMERIC"
                ) -> DECIMAL

                sqlType.startsWith("BINARY") || sqlType.startsWith("VARBINARY") || sqlType.startsWith("BYTEA") || sqlType.startsWith("BLOB") -> BINARY

                sqlType.startsWith("TIMESTAMP") || sqlType.startsWith("DATETIME") -> DATETIME

                sqlType.startsWith("BOOLEAN") || sqlType.startsWith("BOOL") -> BOOLEAN

                sqlType.startsWith("UUID") -> UUID
                sqlType.startsWith("DATE") -> DATE

                columnType is EnumerationNameColumnType<*> -> ENUM

                else -> error(
                    "Cannot infer pagination type from SQL type: " + columnType.sqlType()
                )
            }
        }
    }
}
