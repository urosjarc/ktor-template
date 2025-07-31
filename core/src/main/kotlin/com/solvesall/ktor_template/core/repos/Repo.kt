package com.solvesall.ktor_template.core.repos

import com.solvesall.ktor_template.core.types.Id

public interface Repo<T> {
    public fun count(): Long

    public fun upsert(obj: T)
    public fun upsert(objs: List<T>)

    public fun insert(obj: T)
    public fun insert(objs: List<T>)

    public fun select(): List<T>
    public fun select(id: Id<T>): T?
    public fun select(ids: List<Id<T>>): List<T>

    public fun update(obj: T)
    public fun update(objs: List<T>)

    public fun delete(obj: T)
    public fun delete(objs: List<T>)

    public fun deleteId(ids: List<Id<T>>)
    public fun deleteId(id: Id<T>)

    public fun deleteAll()
}
