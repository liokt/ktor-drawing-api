package com.liot.data.models

import com.liot.other.Constants.TYPE_PLAYERS_LIST

data class PLayersList(
    val players: List<PlayerData>
): BaseModel(TYPE_PLAYERS_LIST)
