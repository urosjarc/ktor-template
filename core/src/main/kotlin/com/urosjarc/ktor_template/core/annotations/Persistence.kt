package com.urosjarc.ktor_template.core.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class Persistence(val schema: String, val table: String)
