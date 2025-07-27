package com.solvesall.ktor_template.app

import com.solvesall.ktor_template.core.domain.User
import com.solvesall.ktor_template.core.extend.random
import com.solvesall.ktor_template.core.types.Encrypted
import com.solvesall.ktor_template.core.types.Id
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.kotlin.logger
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

/**
 * Test class for App
 *
 * Tests the Hibernate implementation without Spring dependencies
 */
class AppTest {

    @BeforeTest
    fun setup() = runBlocking {
        App.init()
    }

    @Test
    fun `test repos`() {
        var user = random<User>()
        runBlocking {
            App.services.db.transaction("CREATE") {
                it.repos.user.create(obj = user)
            }
        }
        var finals = 0.seconds
        repeat(10) { rep ->

            val final = measureTime {

                runBlocking {

                    repeat(1000) { index ->
                        launch {

                            val i = Math.random().times(1000000000000000000).toLong()
                            val newUser = user.copy(
                                id = Id(),
                                username = i.toString(),
                                email = Encrypted(i.toString().toByteArray()),
                                phone = Encrypted(i.toString().toByteArray())
                            )
                            App.services.db.transaction("CREATE") {
                                it.repos.user.create(obj = newUser)
                                it.repos.user.select()
                                it.repos.user.update(obj = newUser)
                                it.repos.user.select(id = newUser.id)
                                it.repos.user.delete(id = newUser.id)
                            }
                        }
                    }


                }
            }
            if(rep > 0){
                logger.info("cost $final")
                finals += final
            }
        }
        logger.info("total cost $finals ${finals/10}")
    }
}
