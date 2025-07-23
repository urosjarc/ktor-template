package com.solvesall.ktor_template.app.repos.hibernate

import com.solvesall.ktor_template.core.repos.Repo
import com.solvesall.ktor_template.core.types.Id
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.EntityTransaction
import org.apache.logging.log4j.kotlin.logger
import org.hibernate.annotations.processing.CheckHQL
import kotlin.uuid.toJavaUuid

public interface HTable<T> {
    public val domain: T
}

@CheckHQL
internal abstract class HRepo<T, D>(
    val emf: EntityManagerFactory,
    private val kclass: Class<T>
): Repo<D> {

    internal abstract fun toRow(obj: D): HTable<D>

    internal inline fun <T> withTransaction(crossinline block: (EntityManager) -> T): T? {
        val em = emf.createEntityManager()
        val tx: EntityTransaction = em.transaction

        return try {
            tx.begin()
            val result = block(em)
            tx.commit()
            result
        } catch (e: Exception) {
            logger.error("Transaction failed", e)
            if (tx.isActive) {
                tx.rollback()
            }
            null
        } finally {
            em.close()
        }
    }

    override fun create(obj: D): D? {
        return withTransaction { em ->
            val row = toRow(obj)
            em.persist(row)
            row.domain
        }
    }

    override fun select(): List<D> {
        return withTransaction { em ->
            val query = em.createQuery("SELECT T FROM ${kclass.simpleName} T", kclass)
            query.resultList.map {
                it as HTable<D>
            }.map { it.domain }
        } ?: emptyList()
    }

    override fun select(id: Id<D>): D? {
        return withTransaction { em ->
            val entity = em.find(kclass, id.value.toJavaUuid()) as? HTable<D>
            entity?.domain
        }
    }

    override fun update(obj: D): D? {
        return withTransaction { em ->
            val row = toRow(obj)
            val merged = em.merge(row) as HTable<D>
            merged.domain
        }
    }

    override fun delete(id: Id<D>): D? {
        return withTransaction { em ->
            val entity = em.find(kclass, id.value.toJavaUuid()) as? HTable<D>
            if (entity != null) {
                val domain = entity.domain
                em.remove(entity)
                domain
            } else {
                null
            }
        }
    }

}
