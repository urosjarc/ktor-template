package com.urosjarc.ktor_template.api.routes.v1

import com.urosjarc.ktor_template.api.extend.ext_clientError
import com.urosjarc.ktor_template.app.App
import com.urosjarc.ktor_template.core.domain.Task
import com.urosjarc.ktor_template.core.types.Id
import io.ktor.http.HttpStatusCode
import io.ktor.resources.*
import io.ktor.server.request.receive
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.response.respond
import io.ktor.server.routing.Route

/**
 * @OpenAPITag tasks
 * Providing tasks endpoints
 */

@Resource("tasks")
class tasks {
    @Resource("{id}")
    class id(val parent: tasks, val id: Id<Task>)
}

fun Route.tasksRoutes() {
    get<tasks> {
        val tasks = App.services.db.transaction("GET TASKS") { tx -> tx.repos.task.select() }
        this.call.respond(tasks)
    }
    post<tasks> {
        val tasks = App.services.db.transaction("POST TASKS") { tx -> tx.repos.task.select() }
        this.call.respond(tasks)
    }

    get<tasks.id> {
        val task = App.services.db.transaction("GET TASK") { tx -> tx.repos.task.select(id = it.id) }
            ?: return@get this.call.ext_clientError("Task not found: ${it.id}")
        this.call.respond(task)
    }
    delete<tasks.id> {
        App.services.db.transaction("DELETE TASK") { tx -> tx.repos.task.deleteId(id = it.id) }
        this.call.respond(HttpStatusCode.OK)
    }
    put<tasks.id> {
        val task = this.call.receive<Task>()
        App.services.db.transaction("PUT TASK") { tx -> tx.repos.task.update(obj = task) }
        this.call.respond(HttpStatusCode.OK)
    }

}
