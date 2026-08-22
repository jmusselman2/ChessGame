package com.jmussel.chessgame.server

import com.jmussel.chessgame.core.GameCore
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main() {
    embeddedServer(
        factory = Netty,
        port = 8080,
        host = "0.0.0.0",
        module = Application::module,
    ).start(wait = true)
}

fun Application.module() {
    routing {
        get("/health") {
            call.respondText(
                text = "${GameCore.NAME} server is healthy",
                status = HttpStatusCode.OK,
            )
        }
    }
}
