package com.urosjarc.ktor_template.api.routes.v1

import com.urosjarc.ktor_template.app.App
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.Route
import kotlinx.serialization.json.JsonObject

/**
 * @OpenAPITag users
 * Providing users endpoints
 */
@Resource("entities/{name}")
class entities(val name: String) {
    @Resource("search")
    class search(val parent: entities)

    @Resource("{id}")
    class id(val parent: entities, val id: String)
}

fun Route.entitiesRoutes() {
    get<entities> {
        val entities = App.services.db.transaction("GET ${it.name}") { tx -> tx.repos.resource(name = it.name).select() }
        this.call.respond(entities)
    }
//    post<entities.search> {
//        val pagination = this.call.receive<Pagination>()
//        val entities = App.services.db.transaction("SEARCH USERS") { tx -> tx.repos.user.select(pagination = pagination) }
//        this.call.respond(entities)
//    }
    post<entities> {
        val data = call.receive<JsonObject>()

        App.services.db.transaction("POST ${it.name}") { tx ->
            val repo = tx.repos.resource(it.name)
            repo.insert(repo.decode(data))
        }

        call.respond(HttpStatusCode.OK)
    }
//
//    get<entities.id> {
//        val user = App.services.db.transaction("GET USER") { tx -> tx.repos.user.select(id = it.id) }
//            ?: return@get this.call.ext_clientError("User not found: ${it.id}")
//        this.call.respond(user)
//    }
//    delete<entities.id> {
//        App.services.db.transaction("DELETE USER") { tx -> tx.repos.user.deleteId(id = it.id) }
//        this.call.respond(HttpStatusCode.OK)
//    }
//    put<entities.id> {
//        val user = this.call.receive<User>()
//        App.services.db.transaction("PUT USER") { tx -> tx.repos.user.update(obj = user) }
//        this.call.respond(HttpStatusCode.OK)
//    }
}

//fun Route.usersEvents() {
//    sse("/users") {
//        while(true) {
//            send(data = "data", event = "event", id = "id", retry = 10000L, comments = "comment")
//            delay(1000)
//        }
//    }
//}
