package org.hoshino9.luogu.paintboard.server

import com.google.gson.Gson
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.pipeline.*
import org.litote.kmongo.lte


suspend fun PipelineContext<*, ApplicationCall>.manageRequest(block: suspend PipelineContext<*, ApplicationCall>. () -> Unit) {
    try {
        val body = call.receive<String>().parseJson().asJsonObject
        val password = body["password"].asString

        if (password == mongo.getCollection<Admin>()
                .find(Admin::time lte System.currentTimeMillis())
                .descendingSort(Admin::time).first() ?.password) {
            block()
        } else call.respond(HttpStatusCode.Forbidden)
    } catch (e: Exception) {
        call.respond(HttpStatusCode.Forbidden)
    }
}

fun Routing.managePage() {
    post("/paintBoard/history") {
        manageRequest {
            try {
                val id = call.request.queryParameters["id"]?.toInt() ?: throw RequestException("未指定画板号")
                val time = call.request.queryParameters["time"]?.toLong() ?: throw RequestException("未指定时间")
                val board = readBoard(id, time)
                call.respondText(board.toString())
            } catch (e: Throwable) {
                call.respondText(
                    "{\"status\": 400,\"data\": \"${e.message}\"}",
                    ContentType.Application.Json,
                    HttpStatusCode.OK
                )
            }
        }
    }

    post("/paintBoard/blame") {
        manageRequest {
            try {
                val id = call.request.queryParameters["id"]?.toInt() ?: throw RequestException("未指定画板号")
                val time = call.request.queryParameters["time"]?.toLong() ?: throw RequestException("未指定时间")
                val x = call.request.queryParameters["x"]?.toInt() ?: throw RequestException("未指定坐标")
                val y = call.request.queryParameters["y"]?.toInt() ?: throw RequestException("未指定坐标")
                val record = blame(id, time, x, y)
                call.respondText(Gson().toJsonTree(record).toString())
            } catch (e: Throwable) {
                call.respondText(
                    "{\"status\": 400,\"data\": \"${e.message}\"}",
                    ContentType.Application.Json,
                    HttpStatusCode.OK
                )
            }
        }
    }

    post("/paintBoard/rollback") {
        manageRequest {
            try {
                val id = call.request.queryParameters["id"]?.toInt() ?: throw RequestException("未指定画板号")
                val time = call.request.queryParameters["time"]?.toLong() ?: throw RequestException("未指定时间")
                rollback(id, time)
                onRefresh(id)
                call.respondText("{\"status\": 200}")

            } catch (e: Throwable) {
                call.respondText(
                    "{\"status\": 400,\"data\": \"${e.message}\"}",
                    ContentType.Application.Json,
                    HttpStatusCode.OK
                )
            }
        }
    }
}