package org.hoshino9.luogu.paintboard.server

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.util.pipeline.*
import java.security.MessageDigest

fun String.parseJson(): JsonElement {
    return JsonParser.parseString(this)
}

fun toHexString(byteArray: ByteArray) = with(StringBuilder()) {
    byteArray.forEach {
        val hex = it.toInt() and (0xFF)
        val hexStr = Integer.toHexString(hex)
        if (hexStr.length == 1) append("0").append(hexStr)
        else append(hexStr)
    }
    toString()
}

fun getSalt(length: Int, pattern: String = "0123456789abcdef") = StringBuilder()
    .apply { (1..length).onEach { append(pattern.random()) } }.toString()

fun encrypt(s: String, salt: String = getSalt(16)) = with(StringBuilder()) {
    val md5 = toHexString(MessageDigest.getInstance("MD5").digest((s + salt).toByteArray()))
    (0 until 16).onEach { i -> append(salt[i]).append(md5[i * 2]).append(md5[i * 2 + 1]) }
    toString()
}

suspend fun PipelineContext<*, ApplicationCall>.catchAndRespond(
    block: suspend PipelineContext<*, ApplicationCall>.() -> Unit
) {
    try {
        block()
    } catch (e: Throwable) {
        call.respondText(
            "{\"status\": 400,\"data\": \"${e.message}\"}",
            contentType = ContentType.Application.Json,
            status = HttpStatusCode.OK
        )
    }
}