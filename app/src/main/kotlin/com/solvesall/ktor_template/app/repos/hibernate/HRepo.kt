package com.solvesall.ktor_template.app.repos.hibernate

import com.solvesall.ktor_template.core.repos.Repo
import com.solvesall.ktor_template.core.types.Id
import org.hibernate.StatelessSession
import kotlin.uuid.toJavaUuid

public interface HTable<T> {
    public val domain: T

    public interface Companion<T> {
        public fun toRow(obj: T): HTable<T>
    }
}

internal abstract class HRepo<T, D>(val session: StatelessSession, val kclass: Class<T>) : Repo<D> {

    internal abstract fun toRow(obj: D): HTable<D>

    override fun upsert(obj: D) {
        val row = toRow(obj)
        session.upsert(row)
    }

    override fun upsert(objs: List<D>) {
        val rows = objs.map { toRow(it) }
        session.upsertMultiple(rows)
    }

    override fun insert(obj: D) {
        val row = toRow(obj)
        session.insert(row)
    }

    override fun insert(objs: List<D>) {
        val rows = objs.map { toRow(it) }
        session.insertMultiple(rows)
    }

    override fun select(): List<D> {
        val query = session.createSelectionQuery("FROM ${kclass.simpleName} T", kclass)
        val resultList = query.resultList
        val domain = resultList.map { (it as HTable<D>).domain }
        return domain
    }

    override fun select(id: Id<D>): D? {
        val entity = session.get(kclass, id.value.toJavaUuid()) as? HTable<D>
        return entity?.domain
    }

    override fun select(ids: List<Id<D>>): List<D> {
        val entities = session.getMultiple(kclass, ids.map { it.value.toJavaUuid() }) as List<HTable<D>>
        return entities.map { it.domain }
    }

    override fun update(obj: D) {
        val row = toRow(obj)
        session.update(row)
    }

    override fun update(objs: List<D>) {
        val rows = objs.map { toRow(it) }
        session.updateMultiple(rows)
    }

    override fun delete(obj: D) {
        val row = toRow(obj)
        session.delete(row)
    }

    override fun delete(objs: List<D>) {
        val rows = objs.map { toRow(it) }
        session.deleteMultiple(rows)
    }

    override fun deleteId(id: Id<D>) {
        session.createMutationQuery("DELETE FROM ${kclass.simpleName} t WHERE t.user.id = :id")
            .setParameter("id", id.value.toJavaUuid())
            .executeUpdate()
    }

    override fun deleteId(ids: List<Id<D>>) {
        session.createMutationQuery("DELETE FROM ${kclass.simpleName} t WHERE t.id IN :ids")
            .setParameter("ids", ids.map { it.value.toJavaUuid() })
            .executeUpdate()

    }

}
