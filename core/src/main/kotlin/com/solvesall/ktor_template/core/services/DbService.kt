package com.solvesall.ktor_template.core.services

import com.solvesall.ktor_template.core.repos.UserRepo
import com.solvesall.ktor_template.core.types.TimeZoned
import kotlin.time.Duration

public interface DbService {
	public data class TransactionStat(
		val index: Long,
		val duration: Duration?,
		val created: TimeZoned,
		val error: Boolean,
	)
	public class Repos(
		public val user: UserRepo
	)
	public interface Transaction {
		public val repos: Repos
		public fun commit()
		public fun rollback()
		public fun timeout(duration: Duration)
	}

	public suspend fun <T> transaction(name: String, block: (tx: Transaction) -> T?): T?
	public val stats: Map<String, List<TransactionStat>>
}
