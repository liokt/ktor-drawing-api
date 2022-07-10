package com.liot.data.routes

import com.google.gson.JsonParser
import com.liot.data.Player
import com.liot.data.Room
import com.liot.data.models.*
import com.liot.gson
import com.liot.other.Constants.TYPE_ANNOUNCEMENT
import com.liot.other.Constants.TYPE_CHAT_MESSAGE
import com.liot.other.Constants.TYPE_CHOSEN_WORD
import com.liot.other.Constants.TYPE_DISCONNECT_REQUEST
import com.liot.other.Constants.TYPE_DRAW_ACTION
import com.liot.other.Constants.TYPE_DRAW_DATA
import com.liot.other.Constants.TYPE_GAME_STATE
import com.liot.other.Constants.TYPE_JOIN_ROOM_HANDSHAKE
import com.liot.other.Constants.TYPE_PHASE_CHANGE
import com.liot.other.Constants.TYPE_PING
import com.liot.server
import com.liot.session.DrawingSession
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach

fun Route.gameWebSocketRoute() {
    route("/ws/draw") {
        standardWebSocket { socket, clientID, message, payload ->
            when (payload) {
                is JoinRoomHandshake -> {
                    val room = server.rooms[payload.roomName]
                    if (room == null) {
                        val gameError = GameError(GameError.ERROR_ROOM_NOT_FOUND)
                        socket.send(Frame.Text(gson.toJson(gameError)))
                        return@standardWebSocket
                    }
                    val player = Player(
                        payload.username,
                        socket,
                        payload.clientId
                    )
                    server.playerJoined(player)
                    if (!room.containsPlayer(player.username)) {
                        room.addPlayer(player.clientId, player.username, socket)
                    } else {
                        //when the player disconnect and reconnect in an amount of time less than the ping frequency
                        val playerInRoom = room.players.find { it.clientId == clientID }
                        playerInRoom?.socket = socket
                        playerInRoom?.startPinging()
                    }
                }
                is ChosenWord -> {
                    val room = server.rooms[payload.roomName] ?: return@standardWebSocket
                    room.setWordAndSwitchToGameRunning(payload.chosenWord)
                }
                is DrawData -> {
                    val room = server.rooms[payload.roomName] ?: return@standardWebSocket
                    if (room.phase == Room.Phase.GAME_RUNNING) {
                        room.broadcastToAllExcept(message, clientID)
                        room.addSerializedDrawInfo(message)
                    }
                    room.lastDrawData = payload
                }
                is DrawAction -> {
                    val room = server.getRoomWithClientId(clientID) ?: return@standardWebSocket
                    room.broadcastToAllExcept(message, clientID)
                    room.addSerializedDrawInfo(message)
                }
                is ChatMessage -> {
                    val room = server.rooms[payload.roomName] ?: return@standardWebSocket
                    if(!room.checkWordAndNotifyPlayers(payload)) {
                        room.broadcast(message)
                    }
                }
                is Ping -> {
                    server.players[clientID]?.receivedPong()
                }
                is DisconnectRequest -> {
                    server.playerLeft(clientID, true)
                }
            }
        }
    }
}

fun Route.standardWebSocket(
    handleFrame: suspend (
        socket: DefaultWebSocketServerSession,
        clientID: String,
        message: String,
        payload: BaseModel
    ) -> Unit
) {
    webSocket {
        val session = call.sessions.get<DrawingSession>()
        if (session == null) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No session"))
            return@webSocket
        }
        try {
            incoming.consumeEach { frame ->
                if (frame is Frame.Text) {
                    val message = frame.readText()
                    val jsonObject = JsonParser.parseString(message).asJsonObject
                    val type = when (jsonObject.get("type").asString) {
                        TYPE_CHAT_MESSAGE -> ChatMessage::class.java
                        TYPE_DRAW_DATA -> DrawData::class.java
                        TYPE_ANNOUNCEMENT -> Announcement::class.java
                        TYPE_JOIN_ROOM_HANDSHAKE -> JoinRoomHandshake::class.java
                        TYPE_PHASE_CHANGE -> PhaseChange::class.java
                        TYPE_CHOSEN_WORD -> ChosenWord::class.java
                        TYPE_GAME_STATE -> GameState::class.java
                        TYPE_PING -> Ping::class.java
                        TYPE_DISCONNECT_REQUEST -> DisconnectRequest::class.java
                        TYPE_DRAW_ACTION -> DrawAction::class.java
                        else -> BaseModel::class.java
                    }
                    val payload = gson.fromJson(message, type)
                    handleFrame(this, session.clientId, message, payload)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            //Handle disconnect
            val playerWithClientId = server.getRoomWithClientId(session.clientId)?.players?.find {
                it.clientId == session.clientId
            }
            if (playerWithClientId != null) {
                server.playerLeft(session.clientId)
            }
        }
    }
}