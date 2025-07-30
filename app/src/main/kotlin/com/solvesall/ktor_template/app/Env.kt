package com.solvesall.ktor_template.app


internal object Env {
    val PID = ProcessHandle.current().pid()
    val DB_DRIVER = System.getenv("DB_DRIVER")
    val DB_URL = System.getenv("DB_URL")
    val DB_PASSWORD = System.getenv("DB_PASSWORD")
    val DB_USER = System.getenv("DB_USER")
    val DB_NAME = System.getenv("DB_NAME")
    val KEYSET_JWT: String = System.getenv("KEYSET_JWT")
    val JWT_AUDIENCE: String = System.getenv("JWT_AUDIENCE")
}
