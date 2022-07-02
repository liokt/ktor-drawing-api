package com.liot.data.routes

import com.google.gson.JsonParser
import com.liot.data.models.BaseModel
import com.liot.data.models.ChatMessage
import com.liot.gson
import com.liot.other.Constants.TYPE_CHAT_MESSAGE
import com.liot.session.DrawingSession
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach

fun Route.standardWebSocket(
    handleFrame: suspend (socket: DefaultWebSocketServerSession,
                          clientID: String,
                          message: String,
                          payload: BaseModel
                          ) -> Unit
) {
   webSocket {
       val session = call.sessions.get<DrawingSession>()
       if(session == null) {
           close(CloseReason(CloseReason.Codes.VIOLATED_POLICY,  "No session"))
           return@webSocket
       }
       try {
           incoming.consumeEach {frame ->
               if(frame is Frame.Text) {
                   val message = frame.readText()
                   val jsonObject = JsonParser.parseString(message).asJsonObject
                   val type = when(jsonObject.get("type").asString) {
                       TYPE_CHAT_MESSAGE -> ChatMessage::class.java
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
       }
   }
}