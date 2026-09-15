package com.urosjarc.ktor_template.core.extend

import com.urosjarc.ktor_template.core.types.Id
import kotlin.uuid.Uuid


public fun <T> Uuid.ext_toId(): Id<T> = Id(this)
