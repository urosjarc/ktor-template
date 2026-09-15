package com.urosjarc.ktor_template.core.repos

import com.urosjarc.ktor_template.core.types.Id
import com.urosjarc.ktor_template.core.types.Pagination
import kotlinx.serialization.json.JsonObject

public interface Repo<T> {
    public val tableName: String
    public fun decode(json: JsonObject): T

    public suspend fun createTable()
    public suspend fun count(): Long

    public suspend fun upsert(obj: T)
    public suspend fun upsert(objs: List<T>)

    public suspend fun insert(obj: T)
    public suspend fun insert(objs: List<T>)

    public suspend fun select(): List<T>
    public suspend fun select(id: Id<T>): T?
    public suspend fun select(ids: List<Id<T>>): List<T>
    public suspend fun select(pagination: Pagination): Pagination.Page<JsonObject>

    public suspend fun update(obj: T)
    public suspend fun update(objs: List<T>)

    public suspend fun delete(obj: T)
    public suspend fun delete(objs: List<T>)
    public suspend fun deleteIds(ids: List<Id<T>>)
    public suspend fun deleteId(id: Id<T>)

    public suspend fun deleteAll()
}
