package com.solvesall.ktor_template.core.services

import com.solvesall.ktor_template.core.repos.UserRepo
import kotlin.time.Duration

public interface DbService {
	public data class TransactionStats(
		val name: String,
		val durations: MutableList<Duration> = mutableListOf(), //TODO: Make this fixed size list!!!
		var completed: Long = 0,
		var failed: Long = 0,
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

	public fun <T> transaction(name: String, block: (tx: Transaction) -> T?): T?
	public val stats: List<TransactionStats>
}
