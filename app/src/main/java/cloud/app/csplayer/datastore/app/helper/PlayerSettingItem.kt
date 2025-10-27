package cloud.app.csplayer.datastore.app.helper

import cloud.app.csplayer.model.SaveCaptionStyle
import kotlinx.serialization.Serializable

@Serializable
data class PlayerSettingItem(val subtitleStyle: SaveCaptionStyle) {
}
