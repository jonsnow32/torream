package cloud.streamless.torream.ui.player.stats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import cloud.streamless.torream.R
import cloud.streamless.torream.utils.UIHelper.colorFromAttribute

/** Minimal rolling line chart, used to visualize recent network throughput samples. */
class SparklineView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

  constructor(context: Context) : this(context, null)

  private val maxSamples = 30
  private val samples = ArrayDeque<Float>()

  private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = context.colorFromAttribute(R.attr.colorPrimary)
    style = Paint.Style.STROKE
    strokeWidth = 3f
  }
  private val path = Path()

  fun addSample(value: Float) {
    if (samples.size >= maxSamples) samples.removeFirst()
    samples.addLast(value.coerceAtLeast(0f))
    invalidate()
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    if (samples.size < 2) return

    val max = samples.max().coerceAtLeast(1f)
    val stepX = width / (maxSamples - 1).toFloat()
    val startIndex = maxSamples - samples.size

    path.reset()
    samples.forEachIndexed { i, value ->
      val x = (startIndex + i) * stepX
      val y = height - (value / max) * height
      if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    canvas.drawPath(path, paint)
  }
}
