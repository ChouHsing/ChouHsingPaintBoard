package org.hoshino9.luogu.paintboard.server

import com.google.gson.Gson
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.sessions.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking
import org.litote.kmongo.Id
import org.litote.kmongo.coroutine.CoroutineDatabase
import org.litote.kmongo.coroutine.coroutine
import org.litote.kmongo.reactivestreams.KMongo
import java.io.File
import java.util.*

data class PaintRequest(val x: Int, val y: Int, val color: String)
data class User(val _id: Id<User>?, val username: String, val email: String, val password: String, val name: String, val stuId: String)
data class UserSession(val id: String, val username: String, val time: Long) : Principal
data class RegisterSession(val email: String, val captcha: String, val time: Long) : Principal
data class Identity(val email: String, val name: String, val stuId: String)
data class PaintRecord(
    val time: Long,
    val userId: String,
    val x: Int,
    val y: Int,
    val color: Int
)

class RequestException(errorMessage: String) : Exception(errorMessage)
object Unknown

lateinit var config: Properties
lateinit var mongo: CoroutineDatabase

var delay: Long = 0

val sessions: MutableList<WebSocketSession> = Collections.synchronizedList(LinkedList())

fun loadConfig() {
    config = Properties().apply {
        load(File("config.properties").inputStream())
    }
}

fun connectMongoDB() {
    val host = config.getProperty("host") ?: throw IllegalArgumentException("no host found")
    val port = config.getProperty("port") ?: "27017"
    val db = config.getProperty("database") ?: throw IllegalArgumentException("no database found")

    mongo = KMongo.createClient("$host:$port").coroutine.getDatabase(db)
    println("Connected to MongoDB server: $host:$port/$db")
}

suspend fun onPaint(req: PaintRequest, id: Int) {
    val str = Gson().toJsonTree(req).apply {
        asJsonObject.addProperty("type", "paintboard_update")
        asJsonObject.addProperty("id", id)
    }.toString()
    sessions.forEach {
        try {
            it.send(str)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}

suspend fun onRefresh(id: Int) {
    val str = "{\"type\":\"refresh\",\"id\":$id}"
    sessions.forEach {
        try {
            it.send(str)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}

fun main() {
    loadConfig()
    connectMongoDB()
    var indexPage = String(Unknown::class.java.getResourceAsStream("/paintboard.html").readBytes())
    var adminPage = String(Unknown::class.java.getResourceAsStream("/admin.html").readBytes())
    if (config.containsKey("wsurl")) {
        indexPage = indexPage.replace("\${wsurl}", config.getProperty("wsurl"))
        adminPage = adminPage.replace("\${wsurl}", config.getProperty("wsurl"))
    }
    delay = (config.getProperty("delay")?.toLong() ?: 0) * 1000
    indexPage = indexPage.replace("\${delay}", (delay / 1000).toString())


    runBlocking {
        initDB()
        loadAllBoards(System.currentTimeMillis())
    }

    embeddedServer(Netty, 8080) {
        install(Compression)
        install(WebSockets)
        install(ContentNegotiation) {
            gson {
            }
        }
        install(Sessions) {
            cookie<UserSession>(
                "user_session",
                directorySessionStorage(File(".sessions"), cached = true)
            ) { cookie.path = "/" }
            cookie<RegisterSession>("register_session") {
                cookie.path = "/"
                transform(
                    SessionTransportTransformerEncrypt(
                        hex(config.getProperty("encryptkey")),
                        hex(config.getProperty("signkey"))
                    )
                )
            }
        }
        install(Authentication) {
            session<UserSession>("auth-session") {
                validate { session ->
                    session
                }
                challenge {
                    call.respondText(
                        "{\"status\": 400, \"data\": \"请先登录\"}",
                        contentType = ContentType.Application.Json,
                        status = HttpStatusCode.OK
                    )
                }
            }
        }

        routing {
            managePage()
            loginPage()
            board()

            get("/paintBoard") {
                call.respondText(indexPage, ContentType.Text.Html)
            }
            get("/paintBoard/admin") {
                call.respondText(adminPage, ContentType.Text.Html)
            }

            webSocket("/paintBoard/ws") {
                try {
                    send("{\"type\": \"result\"}")
                    sessions.add(this)

                    for (frame in incoming) {
                        println("Received: ${String(frame.readBytes())}")
                    }
                } finally {
                    sessions.remove(this)
                    println("Removed.")
                }
            }
        }
    }.start(true)
}