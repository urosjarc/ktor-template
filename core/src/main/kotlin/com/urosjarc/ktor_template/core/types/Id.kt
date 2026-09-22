package com.urosjarc.ktor_template.core.types

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@JvmInline
@Serializable
public value class Id<T>(public val uuid: Uuid = Uuid.generateV7()) {
	override fun toString(): String = this.uuid.toString()
}

public fun <T> String.toId(): Id<T> = Id(Uuid.parse(this))
