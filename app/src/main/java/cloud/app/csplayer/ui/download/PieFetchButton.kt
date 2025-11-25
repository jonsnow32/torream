package cloud.app.csplayer.ui.download

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import cloud.app.csplayer.R

/**
 * A circular button that shows download progress as a pie chart
 */
class PieFetchButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    enum class State {
        IDLE,
        DOWNLOADING,
        PAUSED,
        COMPLETED,
        ERROR
    }

    private var currentState: State = State.IDLE
    private var progressPercent: Float = 0f

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * resources.displayMetrics.density
        color = 0x40808080.toInt() // Semi-transparent gray
    }

    private val arcRect = RectF()

    init {
        // Parse custom attributes if needed
        // Get default colorPrimary from theme
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(
            R.attr.colorPrimary,
            typedValue,
            true
        )
        val defaultColor = if (typedValue.resourceId != 0) {
            ContextCompat.getColor(context, typedValue.resourceId)
        } else {
            typedValue.data
        }

        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.PieFetchButton,
            defStyleAttr,
            0
        ).apply {
            try {
                // Can customize colors, icons from XML here
                val fillColor = getColor(
                    R.styleable.PieFetchButton_download_fill_color,
                    defaultColor
                )
                progressPaint.color = fillColor
            } finally {
                recycle()
            }
        }

        // Make clickable
        isClickable = true
        isFocusable = true

        // Set default icon
        setImageResource(android.R.drawable.stat_sys_download)
        scaleType = ScaleType.CENTER_INSIDE

        // Add ripple effect
        val outValue = android.util.TypedValue()
        context.theme.resolveAttribute(
            android.R.attr.selectableItemBackgroundBorderless,
            outValue,
            true
        )
        setBackgroundResource(outValue.resourceId)
    }

    override fun onDraw(canvas: Canvas) {
        val size = Math.min(width, height).toFloat()
        val padding = progressPaint.strokeWidth
        val center = size / 2f

        arcRect.set(
            padding,
            padding,
            size - padding,
            size - padding
        )

        // Draw background circle
        canvas.drawCircle(center, center, (size - padding * 2) / 2f, backgroundPaint)

        // Draw progress arc
        if (progressPercent > 0 && currentState == State.DOWNLOADING) {
            val sweepAngle = 360f * (progressPercent / 100f)
            canvas.drawArc(arcRect, -90f, sweepAngle, false, progressPaint)
        }

        super.onDraw(canvas)
    }

    /**
     * Set progress (0-100)
     */
    fun setProgress(progress: Float) {
        progressPercent = progress.coerceIn(0f, 100f)
        invalidate()
    }

    /**
     * Set current state
     */
    fun setState(state: State) {
        currentState = state
        updateIcon()
        invalidate()
    }

    private fun updateIcon() {
        val iconRes = when (currentState) {
            State.IDLE -> android.R.drawable.stat_sys_download
            State.DOWNLOADING -> android.R.drawable.ic_media_pause
            State.PAUSED -> android.R.drawable.ic_media_play
            State.COMPLETED -> android.R.drawable.ic_menu_save
            State.ERROR -> android.R.drawable.ic_dialog_alert
        }
        setImageResource(iconRes)

        // Update tint
        val tintColor = when (currentState) {
            State.ERROR -> ContextCompat.getColor(context, android.R.color.holo_red_dark)
            State.COMPLETED -> ContextCompat.getColor(context, android.R.color.holo_green_dark)
            else -> {
                val typedValue = android.util.TypedValue()
                context.theme.resolveAttribute(
                    R.attr.colorPrimary,
                    typedValue,
                    true
                )
                if (typedValue.resourceId != 0) {
                    ContextCompat.getColor(context, typedValue.resourceId)
                } else {
                    typedValue.data
                }
            }
        }
        setColorFilter(tintColor)
    }

    /**
     * Helper methods for common states
     */
    fun setDownloading(progress: Float = progressPercent) {
        setState(State.DOWNLOADING)
        setProgress(progress)
    }

    fun setPaused() {
        setState(State.PAUSED)
    }

    fun setCompleted() {
        setState(State.COMPLETED)
        setProgress(100f)
    }

    fun setError() {
        setState(State.ERROR)
    }

    fun setIdle() {
        setState(State.IDLE)
        setProgress(0f)
    }
}

