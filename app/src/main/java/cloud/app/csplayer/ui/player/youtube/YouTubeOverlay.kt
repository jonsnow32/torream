package cloud.app.csplayer.ui.player.youtube

import android.content.Context
import android.media.session.PlaybackState
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.annotation.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.media3.common.Player
import cloud.app.csplayer.R
import cloud.app.csplayer.ui.player.youtube.views.CircleClipTapView
import cloud.app.csplayer.ui.player.youtube.views.SecondsView

/**
 * Overlay for [DoubleTapPlayerView] to create a similar UI/UX experience like the official
 * YouTube Android app.
 *
 * The overlay has the typical YouTube scaling circle animation and provides some configurations
 * which can't be accomplished with the regular Android Ripple (I didn't find any options in the
 * documentation ...).
 */

class YouTubeOverlay(context: Context, private val attrs: AttributeSet?) :
  ConstraintLayout(context, attrs) {

  private var rootLayout: ConstraintLayout
  private var secondsView: SecondsView
  private var circleClipTapView: CircleClipTapView
  private var listener: Listener? = null;
  fun setListener(listener: Listener) {
    this.listener = listener;
  }

  constructor(context: Context) : this(context, null) {
    // Hide overlay initially when added programmatically
    this.visibility = View.INVISIBLE
  }

  init {
    LayoutInflater.from(context).inflate(R.layout.yt_overlay, this, true)

    rootLayout = findViewById(R.id.root_constraint_layout)
    secondsView = findViewById(R.id.seconds_view)
    circleClipTapView = findViewById(R.id.circle_clip_tap_view)

    // Initialize UI components
    initializeAttributes()
    secondsView.isForward = true
    changeConstraints(true)

    // This code snippet is executed when the circle scale animation is finished
    circleClipTapView.performAtEnd = {
      performListener?.onAnimationEnd()

      secondsView.visibility = View.INVISIBLE
      secondsView.seconds = 0
      secondsView.stop()
    }
  }

  /**
   * Sets all optional XML attributes and defaults
   */
  private fun initializeAttributes() {
    if (attrs != null) {
      val a = context.obtainStyledAttributes(
        attrs,
        R.styleable.YouTubeOverlay, 0, 0
      )

      // Durations
      animationDuration = a.getInt(
        R.styleable.YouTubeOverlay_yt_animationDuration, 650
      ).toLong()

      seekSeconds = a.getInt(
        R.styleable.YouTubeOverlay_yt_seekSeconds, 10
      )

      iconAnimationDuration = a.getInt(
        R.styleable.YouTubeOverlay_yt_iconAnimationDuration, 750
      ).toLong()

      // Arc size
      arcSize = a.getDimensionPixelSize(
        R.styleable.YouTubeOverlay_yt_arcSize,
        context.resources.getDimensionPixelSize(R.dimen.dtpv_yt_arc_size)
      ).toFloat()

      // Colors
      tapCircleColor = a.getColor(
        R.styleable.YouTubeOverlay_yt_tapCircleColor,
        ContextCompat.getColor(context, R.color.dtpv_yt_tap_circle_color)
      )

      circleBackgroundColor = a.getColor(
        R.styleable.YouTubeOverlay_yt_backgroundCircleColor,
        ContextCompat.getColor(context, R.color.dtpv_yt_background_circle_color)
      )

      // Seconds TextAppearance
      textAppearance = a.getResourceId(
        R.styleable.YouTubeOverlay_yt_textAppearance,
        R.style.YTOSecondsTextAppearance
      )

      // Seconds icon
      icon = a.getResourceId(
        R.styleable.YouTubeOverlay_yt_icon,
        R.drawable.ic_play_triangle
      )

      a.recycle()

    } else {
      // Set defaults
      arcSize = context.resources.getDimensionPixelSize(R.dimen.dtpv_yt_arc_size).toFloat()
      tapCircleColor = ContextCompat.getColor(context, R.color.dtpv_yt_tap_circle_color)
      circleBackgroundColor =
        ContextCompat.getColor(context, R.color.dtpv_yt_background_circle_color)
      animationDuration = 650
      iconAnimationDuration = 750
      seekSeconds = 10
      textAppearance = R.style.YTOSecondsTextAppearance
    }
  }


  private var performListener: PerformListener? = null

  /**
   * Sets a listener to execute some code before and after the animation
   * (for example UI changes (hide and show views etc.))
   */
  fun performListener(listener: PerformListener) = apply {
    performListener = listener
  }

  /**
   * Forward / rewind duration on a tap in seconds.
   */
  var seekSeconds: Int = 0
    private set

  fun seekSeconds(seconds: Int) = apply {
    seekSeconds = seconds
  }

  /**
   * Color of the scaling circle on touch feedback.
   */
  var tapCircleColor: Int
    get() = circleClipTapView.circleColor
    private set(value) {
      circleClipTapView.circleColor = value
    }

  fun tapCircleColorRes(@ColorRes resId: Int) = apply {
    tapCircleColor = ContextCompat.getColor(context, resId)
  }

  fun tapCircleColorInt(@ColorInt color: Int) = apply {
    tapCircleColor = color
  }

  /**
   * Color of the clipped background circle
   */
  var circleBackgroundColor: Int
    get() = circleClipTapView.circleBackgroundColor
    private set(value) {
      circleClipTapView.circleBackgroundColor = value
    }

  fun circleBackgroundColorRes(@ColorRes resId: Int) = apply {
    circleBackgroundColor = ContextCompat.getColor(context, resId)
  }

  fun circleBackgroundColorInt(@ColorInt color: Int) = apply {
    circleBackgroundColor = color
  }

  /**
   * Duration of the circle scaling animation / speed in milliseconds.
   * The overlay keeps visible until the animation finishes.
   */
  var animationDuration: Long
    get() = circleClipTapView.animationDuration
    private set(value) {
      circleClipTapView.animationDuration = value
    }

  fun animationDuration(duration: Long) = apply {
    animationDuration = duration
  }

  /**
   * Size of the arc which will be clipped from the background circle.
   * The greater the value the more roundish the shape becomes
   */
  var arcSize: Float
    get() = circleClipTapView.arcSize
    internal set(value) {
      circleClipTapView.arcSize = value
    }

  fun arcSize(@DimenRes resId: Int) = apply {
    arcSize = context.resources.getDimension(resId)
  }

  fun arcSize(px: Float) = apply {
    arcSize = px
  }

  /**
   * Duration the icon animation (fade in + fade out) for a full cycle in milliseconds.
   */
  var iconAnimationDuration: Long = 750
    get() = secondsView.cycleDuration
    private set(value) {
      secondsView.cycleDuration = value
      field = value
    }

  fun iconAnimationDuration(duration: Long) = apply {
    iconAnimationDuration = duration
  }

  /**
   * One of the three forward icons which will be animated above the seconds indicator.
   * The rewind icon will be the 180° mirrored version.
   *
   * Keep in mind that padding on the left and right of the drawable will be rendered which
   * could result in additional space between the three icons.
   */
  @DrawableRes
  var icon: Int = 0
    get() = secondsView.icon
    private set(value) {
      secondsView.stop()
      secondsView.icon = value
      field = value
    }

  fun icon(@DrawableRes resId: Int) = apply {
    icon = resId
  }

  /**
   * Text appearance of the *xx seconds* text.
   */
  @StyleRes
  var textAppearance: Int = 0
    private set(value) {
      TextViewCompat.setTextAppearance(secondsView.textView, value)
      field = value
    }

  fun textAppearance(@StyleRes resId: Int) = apply {
    textAppearance = resId
  }

  /**
   * TextView view for *xx seconds*.
   *
   * In case of you'd like to change some specific attributes of the TextView in runtime.
   */
  val secondsTextView: TextView
    get() = secondsView.textView


   fun onDoubleTapProgressUp(with: Int, posX: Float, posY: Float) {

    val shouldForward = (posX >= with/2)

     
    // YouTube behavior: show overlay on MOTION_UP
    // But check whether the first double tap is in invalid area
    if (this.visibility != View.VISIBLE) {
      performListener?.onAnimationStart()
      secondsView.visibility = View.VISIBLE
      secondsView.start()
    }

    when (shouldForward) {
      false -> {

        // First time tap or switched
        if (secondsView.isForward) {
          changeConstraints(false)
          secondsView.apply {
            isForward = false
            seconds = 0
          }
        }

        // Cancel ripple and start new without triggering overlay disappearance
        // (resetting instead of ending)
        circleClipTapView.resetAnimation {
          circleClipTapView.updatePosition(posX, posY)
        }
        rewinding()
      }

      true -> {

        // First time tap or switched
        if (!secondsView.isForward) {
          changeConstraints(true)
          secondsView.apply {
            isForward = true
            seconds = 0
          }
        }

        // Cancel ripple and start new without triggering overlay disappearance
        // (resetting instead of ending)
        circleClipTapView.resetAnimation {
          circleClipTapView.updatePosition(posX, posY)
        }
        forwarding()
      }

      else -> {
        // Middle area tapped: do nothing
        //
        // playerView?.cancelInDoubleTapMode()
        // circle_clip_tap_view.endAnimation()
        // triangle_seconds_view.stop()
      }
    }
  }

  /**
   * Seeks the video to desired position.
   * Calls interface functions when start reached ([SeekListener.onVideoStartReached])
   * or when end reached ([SeekListener.onVideoEndReached])
   *
   * @param newPosition desired position
   */
  private fun seekToPosition(newPosition: Long) {

  }

  private fun forwarding() {
    secondsView.seconds += seekSeconds
    listener?.onSeekTo(secondsView.seconds.toLong() * 1000)

  }

  private fun rewinding() {
    secondsView.seconds += seekSeconds
    listener?.onSeekTo(secondsView.seconds.toLong() * 1000)
  }

  private fun changeConstraints(forward: Boolean) {
    val constraintSet = ConstraintSet()
    with(constraintSet) {
      clone(rootLayout)
      if (forward) {
        clear(secondsView.id, ConstraintSet.START)
        connect(
          secondsView.id, ConstraintSet.END,
          ConstraintSet.PARENT_ID, ConstraintSet.END
        )
      } else {
        clear(secondsView.id, ConstraintSet.END)
        connect(
          secondsView.id, ConstraintSet.START,
          ConstraintSet.PARENT_ID, ConstraintSet.START
        )
      }
      secondsView.start()
      applyTo(rootLayout)
    }
  }

  interface PerformListener {
    /**
     * Called when the overlay is not visible and onDoubleTapProgressUp event occurred.
     * Visibility of the overlay should be set to VISIBLE within this interface method.
     */
    fun onAnimationStart()

    /**
     * Called when the circle animation is finished.
     * Visibility of the overlay should be set to GONE within this interface method.
     */
    fun onAnimationEnd()
  }

  interface Listener {
    fun onSeekTo(position: Long) {

    }
  }
}

