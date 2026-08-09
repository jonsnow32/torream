package cloud.streamless.torream.ui.player.stats

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import cloud.streamless.torream.R

// The scroll area (statsText) is sized to wrap its content in stats_overlay.xml, not the
// full screen, so blank space outside it has no view to claim touches - those fall through
// to the player underneath (show controls, seek, etc.) untouched, while the text block itself
// can still be scrolled normally when its content overflows.
class StatsOverlay(context: Context, attrs: AttributeSet?) : ConstraintLayout(context, attrs) {

  constructor(context: Context) : this(context, null)

  private val statsText: TextView
  private val networkText: TextView
  private val sparkline: SparklineView
  private val closeButton: ImageView

  /** Invoked when the user taps the close button; caller is responsible for hiding/disabling the overlay. */
  var onCloseClick: (() -> Unit)? = null

  init {
    LayoutInflater.from(context).inflate(R.layout.stats_overlay, this, true)
    statsText = findViewById(R.id.statsText)
    networkText = findViewById(R.id.statsNetwork)
    sparkline = findViewById(R.id.statsSparkline)
    closeButton = findViewById(R.id.statsClose)
    closeButton.setOnClickListener { onCloseClick?.invoke() }
  }

  fun render(text: String, networkSpeedKBps: Float) {
    statsText.text = text
    networkText.text = "Net: %.0f KB/s".format(networkSpeedKBps)
    sparkline.addSample(networkSpeedKBps)
  }
}
