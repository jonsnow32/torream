package cloud.streamless.torream.datastore.app.helper

import cloud.streamless.torream.model.SaveCaptionStyle
import kotlinx.serialization.Serializable

@Serializable
data class PlayerSettingItem(val subtitleStyle: SaveCaptionStyle) {
}
