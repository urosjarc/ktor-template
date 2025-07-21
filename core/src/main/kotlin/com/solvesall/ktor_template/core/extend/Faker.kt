package com.solvesall.ktor_template.core.extend

import com.solvesall.ktor_template.core.domain.User
import com.solvesall.ktor_template.core.types.Encrypted
import com.solvesall.ktor_template.core.types.Hashed
import com.solvesall.ktor_template.core.types.TimeZoned
import com.solvesall.ktor_template.core.types.encrypted
import com.solvesall.ktor_template.core.types.hashed
import io.github.serpro69.kfaker.Faker
import io.github.serpro69.kfaker.fakerConfig
import kotlin.uuid.Uuid

public val faker: Faker = Faker(fakerConfig { randomSeed = 0 })
public val fakerCounter: MutableMap<String, Long> = mutableMapOf()

public inline fun <reified T : Any> Faker.random(): T = faker.randomProvider.randomClassInstance {
    val key = T::class.qualifiedName.toString()
    val value = fakerCounter.getOrDefault(key, 0) + 1
    fakerCounter[key] = value

    typeGenerator<Int> { faker.random.nextInt(min = 0, max = 100) }
    typeGenerator<Uuid> { Uuid.fromLongs(0, value) }
    typeGenerator<Encrypted> { "${it.name}_$value".encrypted() }
    typeGenerator<Hashed> { "${it.name}_$value".hashed() }
    typeGenerator<TimeZoned> { TimeZoned.nowUtc() }
    typeGenerator<String> { "${it.name}_$value" }
}

public fun main() {
    println(faker.random<User>())
}
