package com.liot.other

import com.liot.data.models.ChatMessage

fun ChatMessage.matchesWord(word: String): Boolean {
    return message.toLowerCase().trim() == word.toLowerCase().trim()
}