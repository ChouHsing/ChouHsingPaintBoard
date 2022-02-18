package org.hoshino9.luogu.paintboard.server

import com.aliyun.dm20151123.Client
import com.aliyun.dm20151123.models.SingleSendMailRequest
import com.aliyun.teaopenapi.models.Config
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import org.litote.kmongo.*

suspend fun userAuth(user: User): User? {
    val query = mongo.getCollection<User>()
        .findOne(or(User::username eq user.username, User::email eq user.email)) ?: return null
    val salt = StringBuilder(16).apply { (0 until 48 step 3).onEach { i -> append(query.password[i]) } }

    return if (query.password == encrypt(user.password, salt.toString())) query else null
}

fun sendCaptcha(email: String, captcha: String) {
    val config = Config()
        .setAccessKeyId(config.getProperty("dmid"))
        .setAccessKeySecret(config.getProperty("dmsecret"))
    config.endpoint = "dm.aliyuncs.com"

    val client = Client(config)
    val req = SingleSendMailRequest()
        .setAccountName("aliyundm@zxoj.top")
        .setAddressType(1)
        .setReplyToAddress(false)
        .setSubject("周行算协新春画板注册验证码")
        .setToAddress(email)
        .setFromAlias("周行算协")
        .setTextBody(
            "您好，您的周行算协新春绘板注册验证码为：$captcha，该验证码5分钟内有效。"
        )

    client.singleSendMail(req)
}

fun Routing.loginPage() {
    post("/paintBoard/login") {
        catchAndRespond {
            val body = call.receive<String>()
            val req = Gson().fromJson(body, User::class.java)
            val user = userAuth(req)
            val session = call.sessions.get<UserSession>()

            if (session != null) {
                call.respondText(
                    "{\"status\": 200,\"data\":${Gson().toJson(mapOf(Pair("username", session.username)))}}",
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK
                )
            } else if (user != null) {
                call.sessions.set(UserSession(user._id.toString(), user.username,
                    System.currentTimeMillis() - delay))
                call.respondText(
                    "{\"status\": 200,\"data\": ${Gson().toJson(mapOf(Pair("username", user.username)))}}",
                    contentType = ContentType.Application.Json,
                    status = HttpStatusCode.OK
                )
            } else call.respondText(
                "{\"status\": 403,\"data\": \"用户名或密码错误\"}",
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK
            )
        }
    }

    get("/paintBoard/logout") {
        catchAndRespond {
            call.sessions.clear<UserSession>()
            call.respondRedirect("/paintBoard")
        }
    }

    get("/paintBoard/captcha") {
        catchAndRespond {
            val email = call.parameters["email"] ?: throw RequestException("请输入邮箱")
            val query = mongo.getCollection<User>().findOne(User::email eq email)

            mongo.getCollection<Identity>().findOne(Identity::email eq email)
                ?: throw RequestException("未经验证的邮箱，请确认该邮箱已报名")
            if (query != null) { throw RequestException("该邮箱已被注册") }

            if (config.getProperty("captcha").toBoolean()) {
                val capt = getSalt(6,"0123456789")
                sendCaptcha(email, capt)
                call.sessions.set(RegisterSession(email, capt, System.currentTimeMillis()))
            }
            call.respondText(
                "{\"status\": 200}",
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK
            )
        }
    }

    get("/paintBoard/user") {
        catchAndRespond {
            val user = mongo.getCollection<User>().findOne(
                or(User::username eq call.parameters["username"], User::email eq call.parameters["email"])
            ) ?: throw RequestException("未找到该用户")

            call.respondText(
                "{\"status\": 200,\"data\": ${Gson().toJson(mapOf(Pair("username", user.username)))}",
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK
            )
        }
    }

    post("/paintBoard/user") {
        catchAndRespond {
            val body = call.receive<String>()
            val req = Gson().fromJson(body, User::class.java)
            val id = mongo.getCollection<Identity>().findOne(Identity::email eq req.email)
                ?: throw RequestException("未经验证的邮箱，请确认该邮箱已报名")

            if (mongo.getCollection<User>()
                    .findOne(or(User::username eq req.username, User::email eq req.email))
                != null) { throw RequestException("该用户名或邮箱已被注册") }

            if (config.getProperty("captcha").toBoolean()) {
                val capt = Gson().fromJson(body, JsonObject::class.java).get("captcha").asString
                val session = call.sessions.get<RegisterSession>() ?: throw RequestException("请先获取验证码")
                if (capt != session.captcha || req.email != session.email ||
                    System.currentTimeMillis() - session.time > 5 * 60 * 1000) throw RequestException("验证码无效")
            }

            mongo.getCollection<User>()
                .insertOne(User(newId(), req.username, req.email, encrypt(req.password), id.name, id.stuId))

            call.sessions.clear<RegisterSession>()
            call.respondText(
                "{\"status\":200}",
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK
            )
        }
    }
}