package cloud.streamless.torream.model

import androidx.annotation.FontRes
import androidx.media3.ui.CaptionStyleCompat
import kotlinx.serialization.Serializable

@Serializable
data class SaveCaptionStyle(
  var foregroundColor: Int,
  var backgroundColor: Int,
  var windowColor: Int,
  var edgeType: Int,
  var edgeColor: Int,
  @FontRes
  var typeface: Int?,
  var typefaceFilePath: String?,
  /**in dp**/
  var elevation: Int,
  /**in sp**/
  var fixedTextSize: Float?,
  var removeCaptions: Boolean = false,
  var removeBloat: Boolean = true,
  var upperCase: Boolean = false,
)

