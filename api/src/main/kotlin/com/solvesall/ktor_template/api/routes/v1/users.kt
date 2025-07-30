package com.solvesall.ktor_template.api.routes.v1

import com.solvesall.ktor_template.api.extend.ext_clientError
import com.solvesall.ktor_template.app.App
import com.solvesall.ktor_template.core.domain.User
import com.solvesall.ktor_template.core.types.Id
import io.ktor.http.HttpStatusCode
import io.ktor.resources.*
import io.ktor.server.request.receive
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

/**
 * @OpenAPITag users
 * Providing users endpoints
 */

@Resource("users")
class users {
    @Resource("{id}")
    class id(val parent: users, val id: Id<User>)
}

fun Route.usersRoutes() {
    get<users> {
        val users = App.services.db.transaction("GET USERS") { tx -> tx.repos.user.select() }
        this.call.respond(users)
    }
    post<users> {
        val users = App.services.db.transaction("POST USERS") { tx -> tx.repos.user.select() }
        this.call.respond(users)
    }

    get<users.id> {
        val user = App.services.db.transaction("GET USER") { tx -> tx.repos.user.select(id = it.id) }
            ?: return@get this.call.ext_clientError("User not found: ${it.id}")
        this.call.respond(user)
    }
    delete<users.id> {
        App.services.db.transaction("DELETE USER") { tx -> tx.repos.user.deleteId(id = it.id) }
        this.call.respond(HttpStatusCode.OK)
    }
    put<users.id> {
        val user = this.call.receive<User>()
        App.services.db.transaction("PUT USER") { tx -> tx.repos.user.update(obj = user) }
        this.call.respond(HttpStatusCode.OK)
    }

}
