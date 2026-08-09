package cloud.streamless.torream.ui.player.stats

/** GPU render pass timing in microseconds, summed across all passes per frame. */
data class FrameTiming(
  val freshLast: Double,
  val freshAvg: Double,
  val freshPeak: Double,
  val redrawLast: Double,
  val redrawAvg: Double,
  val redrawPeak: Double
)
