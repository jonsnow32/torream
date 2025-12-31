package com.tv.apps.zippy.ui.player.youtube.views

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import com.tv.apps.zippy.R
import java.lang.ref.WeakReference

class SecondsView(context: Context, attrs: AttributeSet?) :
  ConstraintLayout(context, attrs) {

  private var trianglesContainer: LinearLayout
  private var secondsTextView: TextView
  private var icon1: ImageView
  private var icon2: ImageView
  private var icon3: ImageView

  init {
    LayoutInflater.from(context).inflate(R.layout.yt_seconds_view, this, true)

    trianglesContainer = findViewById(R.id.triangle_container)
    secondsTextView = findViewById(R.id.tv_seconds)
    icon1 = findViewById(R.id.icon_1)
    icon2 = findViewById(R.id.icon_2)
    icon3 = findViewById(R.id.icon_3)
  }

  var cycleDuration: Long = 750L
    set(value) {
      firstAnimator.duration = value / 5
      secondAnimator.duration = value / 5
      thirdAnimator.duration = value / 5
      fourthAnimator.duration = value / 5
      fifthAnimator.duration = value / 5
      field = value
    }

  var seconds: Int = 0
    set(value) {
      secondsTextView.text = context.resources.getQuantityString(
        R.plurals.quick_seek_x_second, value, value
      )
      field = value
    }

  var isForward: Boolean = true
    set(value) {
      trianglesContainer.rotation = if (value) 0f else 180f
      field = value
    }

  val textView: TextView
    get() = secondsTextView

  @DrawableRes
  var icon: Int = R.drawable.ic_play_triangle
    set(value) {
      if (value > 0) {
        icon1.setImageResource(value)
        icon2.setImageResource(value)
        icon3.setImageResource(value)
      }
      field = value
    }

  fun start() {
    stop()
    firstAnimator.start()
  }

  fun stop() {
    firstAnimator.cancel()
    secondAnimator.cancel()
    thirdAnimator.cancel()
    fourthAnimator.cancel()
    fifthAnimator.cancel()
    reset()
  }

  private fun reset() {
    icon1.alpha = 0f
    icon2.alpha = 0f
    icon3.alpha = 0f
  }

  private val firstAnimator: ValueAnimator by lazy {
    ValueAnimator.ofFloat(0f, 1f).setDuration(cycleDuration / 5).apply {
      doOnStart {
        icon1.alpha = 0f
        icon2.alpha = 0f
        icon3.alpha = 0f
      }
      addUpdateListener {
        icon1.alpha = (it.animatedValue as Float)
      }
      doOnEnd {
        secondAnimator.start()
      }
    }
  }

  private val secondAnimator: ValueAnimator by lazy {
    ValueAnimator.ofFloat(0f, 1f).setDuration(cycleDuration / 5).apply {
      doOnStart {
        icon1.alpha = 1f
        icon2.alpha = 0f
        icon3.alpha = 0f
      }
      addUpdateListener {
        icon2.alpha = (it.animatedValue as Float)
      }
      doOnEnd {
        thirdAnimator.start()
      }
    }
  }

  private val thirdAnimator: ValueAnimator by lazy {
    ValueAnimator.ofFloat(0f, 1f).setDuration(cycleDuration / 5).apply {
      doOnStart {
        icon1.alpha = 1f
        icon2.alpha = 1f
        icon3.alpha = 0f
      }
      addUpdateListener {
        icon1.alpha = 1f - icon3.alpha
        icon3.alpha = (it.animatedValue as Float)
      }
      doOnEnd {
        fourthAnimator.start()
      }
    }
  }

  private val fourthAnimator: ValueAnimator by lazy {
    ValueAnimator.ofFloat(0f, 1f).setDuration(cycleDuration / 5).apply {
      doOnStart {
        icon1.alpha = 0f
        icon2.alpha = 1f
        icon3.alpha = 1f
      }
      addUpdateListener {
        icon2.alpha = 1f - (it.animatedValue as Float)
      }
      doOnEnd {
        fifthAnimator.start()
      }
    }
  }

  private val fifthAnimator: ValueAnimator by lazy {
    ValueAnimator.ofFloat(0f, 1f).setDuration(cycleDuration / 5).apply {
      doOnStart {
        icon1.alpha = 0f
        icon2.alpha = 0f
        icon3.alpha = 1f
      }
      addUpdateListener {
        icon3.alpha = 1f - (it.animatedValue as Float)
      }
      doOnEnd {
        firstAnimator.start()
      }
    }
  }

  // Cancel animators when the view is detached from the window
  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    stop() // Stop and cancel all animations
    clearAnimators()
  }

  private fun clearAnimators() {
    firstAnimator.removeAllListeners()
    secondAnimator.removeAllListeners()
    thirdAnimator.removeAllListeners()
    fourthAnimator.removeAllListeners()
    fifthAnimator.removeAllListeners()
  }
}
