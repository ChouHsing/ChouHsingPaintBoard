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
            catchAndRespond {
                val id = call.request.queryParameters["id"]?.toInt() ?: throw RequestException("未指定画板号")
                val time = call.request.queryParameters["time"]?.toLong() ?: throw RequestException("未指定时间")
                val board = readBoard(id, time)
                call.respondText(text = board.toString())
            }
        }
    }

    post("/paintBoard/blame") {
        manageRequest {
            catchAndRespond {
                val id = call.request.queryParameters["id"]?.toInt() ?: throw RequestException("未指定画板号")
                val time = call.request.queryParameters["time"]?.toLong() ?: throw RequestException("未指定时间")
                val x = call.request.queryParameters["x"]?.toInt() ?: throw RequestException("未指定坐标")
                val y = call.request.queryParameters["y"]?.toInt() ?: throw RequestException("未指定坐标")
                val record = blame(id, time, x, y)
                call.respondText(
                    text = Gson().toJsonTree(record).toString(),
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK
                )
            }
        }
    }

    post("/paintBoard/rollback") {
        manageRequest {
            catchAndRespond {
                val id = call.request.queryParameters["id"]?.toInt() ?: throw RequestException("未指定画板号")
                val time = call.request.queryParameters["time"]?.toLong() ?: throw RequestException("未指定时间")
                rollback(id, time)
                onRefresh(id)
                call.respondText(
                    text = "{\"status\": 200}",
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK
                )
            }
        }
    }

    post("/paintBoard/brush") {
        manageRequest {
            catchAndRespond {
                val id = call.request.queryParameters["id"]?.toInt() ?: throw RequestException("未指定画板号")
                val x1 = call.request.queryParameters["x1"]?.toInt() ?: throw RequestException("未指定坐标")
                val y1 = call.request.queryParameters["y1"]?.toInt() ?: throw RequestException("未指定坐标")
                val x2 = call.request.queryParameters["x2"]?.toInt() ?: throw RequestException("未指定坐标")
                val y2 = call.request.queryParameters["y2"]?.toInt() ?: throw RequestException("未指定坐标")
                val color = call.request.queryParameters["color"]?.toInt(16) ?: throw RequestException("未指定颜色")
                if (x1 !in 0 until 800 || y1 !in 0 until 400 || x2 !in 0 until 800 || y2 !in 0 until 400) throw RequestException("坐标越界")
                if (color !in 0x000000..0xFFFFFF) throw RequestException("颜色越界")

                brush(id, x1, y1, x2, y2, color)
                onRefresh(id)
                call.respondText(
                    text = "{\"status\": 200}",
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK
                )
            }
        }
    }
}