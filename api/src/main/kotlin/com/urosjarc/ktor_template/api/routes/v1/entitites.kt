package com.urosjarc.ktor_template.api.routes.v1

import com.urosjarc.ktor_template.api.extend.ext_clientError
import com.urosjarc.ktor_template.app.App
import com.urosjarc.ktor_template.core.types.Id
import com.urosjarc.ktor_template.core.types.Pagination
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.response.*
import io.ktor.server.routing.Route
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.Uuid

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
        val entities = App.services.db.transaction("GET ${it.name}") { tx ->
            tx.repos.resource(it.name).select()
        }
        this.call.respond(entities)
    }

    post<entities.search> {
        val pagination = this.call.receive<Pagination>()
        App.services.db.transaction("SEARCH ${it.parent.name}") { tx ->
            this.call.respond(tx.repos.resource(it.parent.name).select(pagination = pagination))
        }
    }

    post<entities> {
        val data = call.receive<JsonObject>()
        App.services.db.transaction("POST ${it.name}") { tx ->
            val repo = tx.repos.resource(it.name)
            repo.insert(repo.decode(data))
        }
        call.respond(HttpStatusCode.OK)
    }

    get<entities.id> {
        val data = App.services.db.transaction("GET ${it.parent.name}") { tx ->
            tx.repos.resource(it.parent.name).select(id = Id(Uuid.parse(it.id)))
        } ?: this.call.ext_clientError("Resource not found")
        this.call.respond(data)
    }

    delete<entities.id> {
        App.services.db.transaction("DELETE ${it.parent.name}") { tx ->
            tx.repos.resource(it.parent.name).deleteId(id = Id(Uuid.parse(it.id)))
        }
        this.call.respond(HttpStatusCode.OK)
    }

    put<entities.id> {
        val data = call.receive<JsonObject>()

        App.services.db.transaction("PUT ${it.parent.name}") { tx ->
            val repo = tx.repos.resource(it.parent.name)
            repo.update(repo.decode(data))
        }
        this.call.respond(HttpStatusCode.OK)
    }
}
