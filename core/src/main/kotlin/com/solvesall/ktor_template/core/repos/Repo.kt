package com.solvesall.ktor_template.core.repos

import com.solvesall.ktor_template.core.types.Id

public interface Repo<T> {
    public fun upsert(obj: T)
    public fun insert(obj: T)
    public fun select(): List<T>
    public fun select(id: Id<T>): T?
    public fun update(obj: T)
    public fun delete(obj: T)
}
