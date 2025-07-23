package com.solvesall.ktor_template.core.services

import com.solvesall.ktor_template.core.repos.UserRepo
import kotlin.time.Duration

public interface DbService {
	public interface Repos {
		public val userRepo: UserRepo
		public fun commit()
		public fun rollback()
		public fun timeout(duration: Duration)
	}

	public fun <T> transaction(block: (Repos) -> T?): T?
}
