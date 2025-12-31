package com.tv.apps.zippy.datastore.app.helper

import com.tv.apps.zippy.model.SaveCaptionStyle
import kotlinx.serialization.Serializable

@Serializable
data class PlayerSettingItem(val subtitleStyle: SaveCaptionStyle) {
}
