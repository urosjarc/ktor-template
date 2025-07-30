package com.solvesall.ktor_template.core.types

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@JvmInline
@Serializable
public value class Id<T>(public val value: Uuid = Uuid.random()) {
	override fun toString(): String = this.value.toString()
}

public fun <T> String.toId(): Id<T> = Id(Uuid.parse(this))
