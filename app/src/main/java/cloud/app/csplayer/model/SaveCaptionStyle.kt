package cloud.app.csplayer.model

import androidx.annotation.FontRes
import androidx.media3.ui.CaptionStyleCompat
import com.fasterxml.jackson.annotation.JsonProperty

data class SaveCaptionStyle(
  @JsonProperty("foregroundColor") var foregroundColor: Int,
  @JsonProperty("backgroundColor") var backgroundColor: Int,
  @JsonProperty("windowColor") var windowColor: Int,
  @JsonProperty("edgeType") var edgeType: Int,
  @JsonProperty("edgeColor") var edgeColor: Int,
  @FontRes
  @JsonProperty("typeface") var typeface: Int?,
  @JsonProperty("typefaceFilePath") var typefaceFilePath: String?,
  /**in dp**/
  @JsonProperty("elevation") var elevation: Int,
  /**in sp**/
  @JsonProperty("fixedTextSize") var fixedTextSize: Float?,
  @JsonProperty("removeCaptions") var removeCaptions: Boolean = false,
  @JsonProperty("removeBloat") var removeBloat: Boolean = true,
  /** Apply caps lock to the text **/
  @JsonProperty("upperCase") var upperCase: Boolean = false,
)

