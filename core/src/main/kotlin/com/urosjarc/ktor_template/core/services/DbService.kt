package com.urosjarc.ktor_template.core.services

import com.urosjarc.ktor_template.core.repos.TaskRepo
import com.urosjarc.ktor_template.core.repos.UserRepo
import com.urosjarc.ktor_template.core.types.TimeZoned
import kotlin.time.Duration

public interface DbService {
    public data class TransactionStat(
        val index: Long,
        val duration: Duration?,
        val created: TimeZoned,
        val error: Boolean,
    )

    public class Repos(
        public val user: UserRepo,
        public val task: TaskRepo,
    )

    public interface Transaction {
        public val repos: Repos
        public suspend fun commit()
        public suspend fun rollback()
    }

    public suspend fun <T> transaction(name: String, block: suspend (Transaction) -> T): T

    public val stats: Map<String, List<TransactionStat>>
}
