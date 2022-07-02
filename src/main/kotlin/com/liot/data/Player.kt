package com.liot.data

import io.ktor.http.cio.websocket.*

class Player(
    val username: String,
    var socket: WebSocketSession,
    val clientId: String,
    var isDrawing: Boolean,
    var score: Int = 0,
    val rank: Int = 0
)