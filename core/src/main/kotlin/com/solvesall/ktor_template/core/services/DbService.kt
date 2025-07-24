package com.solvesall.ktor_template.core.services

import com.solvesall.ktor_template.core.repos.UserRepo
import kotlin.time.Duration

public interface DbService {
	public class Repos(
		public val user: UserRepo
	)
	public interface Transaction {
		public val repos: Repos
		public fun commit()
		public fun rollback()
		public fun timeout(duration: Duration)
	}

	public fun <T> transaction(block: (tx: Transaction) -> T?): T?
}
