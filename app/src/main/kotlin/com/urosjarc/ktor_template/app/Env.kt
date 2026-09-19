package com.urosjarc.ktor_template.app


internal object Env {
    val PID = ProcessHandle.current().pid()
    val DB_DRIVER = System.getenv("DB_DRIVER")
    val DB_TYPE= System.getenv("DB_TYPE")
    val DB_USER = System.getenv("DB_USER")
    val DB_PASSWORD = System.getenv("DB_PASSWORD")
    val DB_DOMAIN = System.getenv("DB_DOMAIN")
    val DB_PORT = System.getenv("DB_PORT")
    val DB_NAME = System.getenv("DB_NAME")
    val KEYSET_JWT: String = System.getenv("KEYSET_JWT")
    val JWT_AUDIENCE: String = System.getenv("JWT_AUDIENCE")
}
