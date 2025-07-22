package com.solvesall.ktor_template.core.repos

import com.solvesall.ktor_template.core.types.Id

public interface Repo<T> {
    public fun create(obj: T): T?
    public fun select(): List<T>
    public fun select(id: Id<T>): T?
    public fun update(obj: T): T?
    public fun delete(id: Id<T>): T?
}
