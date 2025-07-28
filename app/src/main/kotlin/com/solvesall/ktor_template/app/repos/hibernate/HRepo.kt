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

    override fun insert(obj: D) {
        val row = toRow(obj)
        session.insert(row)
    }

    override fun select(): List<D> {
        val query = session.createQuery("SELECT T FROM ${kclass.simpleName} T", kclass)
        val resultList = query.resultList
        val domain = resultList.map { (it as HTable<D>).domain }
        return domain
    }

    override fun select(id: Id<D>): D? {
        val entity = session.get(kclass, id.value.toJavaUuid()) as? HTable<D>
        return entity?.domain
    }

    override fun update(obj: D) {
        val row = toRow(obj)
        session.update(row)
    }

    override fun delete(obj: D) {
        val row = toRow(obj)
        session.delete(row)
    }

}
