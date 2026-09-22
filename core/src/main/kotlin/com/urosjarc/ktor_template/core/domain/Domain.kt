package com.urosjarc.ktor_template.core.domain

import com.urosjarc.ktor_template.core.types.Events
import com.urosjarc.ktor_template.core.types.Id

public interface Domain<T> {
    public val id: Id<T>
    public val events: Events
}
