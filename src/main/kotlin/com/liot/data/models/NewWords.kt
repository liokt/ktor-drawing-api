package com.liot.data.models

import com.liot.other.Constants.TYPE_NEW_WORDS

data class NewWords(
    val newWords: List<String>
): BaseModel(TYPE_NEW_WORDS)
