package com.liot

import com.google.gson.Gson
import com.liot.data.routes.createRoomRoute
import com.liot.data.routes.gameWebSocketRoute
import com.liot.data.routes.getRoomsRoute
import com.liot.data.routes.joinRoomRoute
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import com.liot.plugins.*
import com.liot.session.DrawingSession
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.*
import io.ktor.websocket.*

val server = DrawingServer()
val gson = Gson()

fun main() {
    embeddedServer(Netty, port = 8001, host = "0.0.0.0") {
        install(Sessions) {
            cookie<DrawingSession>("SESSION")
        }
        intercept(ApplicationCallPipeline.Features) {
            if(call.sessions.get<DrawingSession>() == null) {
                val clientId = call.parameters["client_id"] ?: ""
                call.sessions.set(DrawingSession(clientId, generateNonce()))
            }
        }
        install(WebSockets)
        install(Routing) {
            createRoomRoute()
            getRoomsRoute()
            joinRoomRoute()
            gameWebSocketRoute()
        }
        //configureRouting()
        //configureSockets()
        configureMonitoring()
        configureSerialization()
    }.start(wait = true)
}
