package cloud.app.csplayer.ui.player.mpv

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.Editable
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity.RESULT_CANCELED
import androidx.appcompat.app.AppCompatActivity.RESULT_OK
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.media3.common.util.UnstableApi
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.PlayerCustomLayoutBinding
import cloud.app.csplayer.databinding.PlayerSelectSourceAndSubsBinding
import cloud.app.csplayer.databinding.SubtitleOffsetBinding
import cloud.app.csplayer.ui.player.CSPlayerViewModel
import cloud.app.csplayer.ui.player.PlayerEventType
import cloud.app.csplayer.ui.player.SUBTITLE_DELAY_BUNDLE_KEY
import cloud.app.csplayer.ui.player.exo.PlayerResize
import cloud.app.csplayer.ui.player.youtube.YouTubeOverlay
import cloud.app.csplayer.utils.AppUtils.isCastApiAvailable
import cloud.app.csplayer.utils.CommonActivitty
import cloud.app.csplayer.utils.CommonActivitty.playerEventListener
import cloud.app.csplayer.utils.DataStore
import cloud.app.csplayer.utils.ExtractorLink
import cloud.app.csplayer.utils.ExtractorUri
import cloud.app.csplayer.utils.SingleSelectionHelper.showDialog
import cloud.app.csplayer.utils.SubtitleData
import cloud.app.csplayer.utils.UIHelper.dismissSafe
import cloud.app.csplayer.utils.UIHelper.getNavigationBarHeight
import cloud.app.csplayer.utils.UIHelper.getStatusBarHeight
import cloud.app.csplayer.utils.UIHelper.popCurrentPage
import cloud.app.csplayer.utils.UIHelper.showSystemUI
import cloud.app.csplayer.utils.UIHelper.toPx
import cloud.app.csplayer.utils.Utils
import cloud.app.csplayer.utils.Utils.logError
import cloud.app.csplayer.utils.Utils.normalSafeApiCall
import cloud.app.csplayer.utils.Utils.showToast
import cloud.app.csplayer.utils.Utils.sortSubs
import cloud.app.csplayer.utils.hideSystemUI
import cloud.app.csplayer.utils.observe
import cloud.app.csplayer.utils.setText
import cloud.app.csplayer.utils.txt
import com.github.rubensousa.previewseekbar.PreviewBar
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import java.io.File

enum class DecodeMode {
  hwDec,
  swDec
}

class MvpPlayerFragment : Fragment(), MPVLib.EventObserver {

  private val viewModel by viewModels<CSPlayerViewModel>()

  // for calls to eventUi() and eventPropertyUi()
  private val eventUiHandler = Handler(Looper.getMainLooper())

  // for use with fadeRunnable1..3
  private val fadeHandler = Handler(Looper.getMainLooper())

  // for use with stopServiceRunnable
  private val stopServiceHandler = Handler(Looper.getMainLooper())


  private var mvpPlayer: MPVView? = null
  private var playerBinding: PlayerCustomLayoutBinding? = null


  // state of player UI
  protected var isShowing = true
  protected var isLocked = false
  private var resizeMode: Int = 0
  private var backgroundPlayMode = ""

  //settings
  protected var fastForwardTime = 10000L
  protected var androidTVInterfaceOffSeekTime = 10000L
  protected var androidTVInterfaceOnSeekTime = 30000L
  protected var swipeHorizontalEnabled = false
  protected var swipeVerticalEnabled = false
  protected var playBackSpeedEnabled = true
  protected var playerResizeEnabled = false
  protected var doubleTapEnabled = false
  protected var doubleTapPauseEnabled = true
  protected var playerRotateEnabled = false
  protected var autoPlayerRotateEnabled = false
  private var activityIsForeground = true

  protected var subtitleDelay
    set(value) = try {
      mvpPlayer?.subDelay = mvpPlayer?.subDelay?.plus(value as Long)
    } catch (e: Exception) {
      logError(e)
    }
    get() = try {
      -(mvpPlayer?.subDelay?.toLong() ?: 0)
    } catch (e: Exception) {
      logError(e)
      0L
    }

  //private var useSystemBrightness = false
  protected var useTrueSystemBrightness = true
  private val fullscreenNotch = true //TODO SETTING

  private var statusBarHeight: Int? = null
  private var navigationBarHeight: Int? = null

  private val brightnessIcons = listOf(
    R.drawable.sun_1,
    R.drawable.sun_2,
    R.drawable.sun_3,
    R.drawable.sun_4,
    R.drawable.sun_5,
    R.drawable.sun_6,
    //R.drawable.sun_7,
    // R.drawable.ic_baseline_brightness_1_24,
    // R.drawable.ic_baseline_brightness_2_24,
    // R.drawable.ic_baseline_brightness_3_24,
    // R.drawable.ic_baseline_brightness_4_24,
    // R.drawable.ic_baseline_brightness_5_24,
    // R.drawable.ic_baseline_brightness_6_24,
    // R.drawable.ic_baseline_brightness_7_24,
  )

  private val volumeIcons = listOf(
    R.drawable.ic_baseline_volume_mute_24,
    R.drawable.ic_baseline_volume_down_24,
    R.drawable.ic_baseline_volume_up_24,
  )

  private val psc = MPVUtils.PlaybackStateCache()
  private var mediaSession: MediaSessionCompat? = null
  private var audioManager: AudioManager? = null
  private var audioFocusRequest: AudioFocusRequestCompat? = null
  private var audioFocusRestore: () -> Unit = {}
  private var ignoreAudioFocus = false


  private var playbackHasStarted = false
  private var onloadCommands = mutableListOf<Array<String>>()


  private var allLinks: Set<Pair<ExtractorLink?, ExtractorUri?>> = setOf()
  private var currentSubs: Set<SubtitleData> = mutableSetOf()
  private var currentSelectedLink: Pair<ExtractorLink?, ExtractorUri?>? = null
  private var currentSelectedSubtitles: SubtitleData? = null

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    val view = inflater.inflate(R.layout.fragment_mvp_player, container, false)
    val controllerHolder = view.findViewById<FrameLayout>(R.id.controller_holder)
    mvpPlayer = view.findViewById(R.id.mvpPlayer)
    val childView = inflater.inflate(R.layout.player_custom_layout, controllerHolder, false)
    controllerHolder.addView(childView)
    playerBinding = PlayerCustomLayoutBinding.bind(childView.findViewById(R.id.player_holder))
    return view;
  }

  private val seekActionTime = 30000L
  private var decodeMode = DecodeMode.hwDec;

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    BackgroundPlaybackService.createNotificationChannel(requireActivity())

    mvpPlayer?.addObserver(this)
    mvpPlayer?.initialize(requireActivity().filesDir.path, requireActivity().cacheDir.path)


    observe(viewModel.allLinks) {
      allLinks = it
      //currentSelectedLink = allLinks.first()
    }
    observe(viewModel.currentLinkIndex) {
      normalSafeApiCall {
        loadLink(allLinks.elementAt(it))
      }
    }
    observe(viewModel.currentSubs) { set ->
      for (sub in set) {
        val url = resolveUri(Uri.parse(sub.url)) ?: continue
        val flag = "select"
        Log.v(TAG, "Adding subtitles from intent extras: $url")
      }
    }

    observe(viewModel.currentSubtitleIndex) { index ->
//      if (index >= 0 && index < currentSubs.size)
//        setSubtitles(currentSubs.elementAt(index))
    }

//    preferredAutoSelectSubtitles = context?.getAutoSelectLanguageISO639_1()


    mvpPlayer?.setOnClickListener {
      toggleControls()
    }

    mediaSession = initMediaSession()
    updateMediaSession()
    BackgroundPlaybackService.mediaToken = mediaSession?.sessionToken

    audioManager = activity?.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    activity?.volumeControlStream = STREAM_TYPE

    // Handle audio focus
    val req = with(AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)) {
      setAudioAttributes(with(AudioAttributesCompat.Builder()) {
        // N.B.: libmpv may use different values in ao_audiotrack, but here we always pretend to be music.
        setUsage(AudioAttributesCompat.USAGE_MEDIA)
        setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
        build()
      })
      setOnAudioFocusChangeListener(audioFocusChangeListener)
      build()
    }
    val res = AudioManagerCompat.requestAudioFocus(audioManager!!, req)
    if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
      audioFocusRequest = req
    } else {
      Log.v(TAG, "Audio focus not granted")
      if (!ignoreAudioFocus)
        onloadCommands.add(arrayOf("set", "pause", "yes"))
    }

    setPlayBackSpeed(DataStore.playBackSpeed)
    savedInstanceState?.getLong(SUBTITLE_DELAY_BUNDLE_KEY)?.let {
      subtitleDelay = it
    }

    // handle tv controls
    playerEventListener = { eventType ->
      when (eventType) {
        PlayerEventType.Lock -> {
          toggleLock()
        }

        PlayerEventType.NextEpisode -> {
          //mvpPlayer?.handleEvent(CSPlayerEvent.NextEpisode)
        }

        PlayerEventType.Pause -> {
          mvpPlayer?.paused = true
          //mvpPlayer?.handleEvent(CSPlayerEvent.Pause)
        }

        PlayerEventType.PlayPauseToggle -> {
          togglePlayPause()
        }

        PlayerEventType.Play -> {
          mvpPlayer?.paused = false
        }

        PlayerEventType.SkipCurrentChapter -> {
          //mvpPlayer?.handleEvent(CSPlayerEvent.SkipCurrentChapter)
        }

        PlayerEventType.Resize -> {
          nextResize()
          setReizeIcon()
        }

        PlayerEventType.PrevEpisode -> {
          //mvpPlayer?.handleEvent(CSPlayerEvent.PrevEpisode)
        }

        PlayerEventType.SeekForward -> {
          mvpPlayer?.timePos = mvpPlayer?.timePos?.plus(seekActionTime)
        }

        PlayerEventType.ShowSpeed -> {
          showSpeedDialog()
        }

        PlayerEventType.SeekBack -> {
          mvpPlayer?.timePos = mvpPlayer?.timePos?.plus(-seekActionTime)
        }

        PlayerEventType.ToggleMute -> {
          //mvpPlayer?.handleEvent(CSPlayerEvent.ToggleMute)
        }

        PlayerEventType.ToggleHide -> {
          toggleControls()
        }

        PlayerEventType.ShowMirrors -> {
          showSourcesDialog()
        }

        PlayerEventType.SearchSubtitlesOnline -> {
        }

        PlayerEventType.SkipOp -> {
          //skipOp()
        }
      }
    }



    try {
      context?.let { ctx ->
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)

        fastForwardTime =
          settingsManager.getInt(ctx.getString(R.string.double_tap_seek_time_key), 10)
            .toLong() * 1000L

        androidTVInterfaceOffSeekTime =
          settingsManager.getInt(
            ctx.getString(R.string.android_tv_interface_off_seek_key),
            10
          )
            .toLong() * 1000L
        androidTVInterfaceOnSeekTime =
          settingsManager.getInt(
            ctx.getString(R.string.android_tv_interface_on_seek_key),
            10
          )
            .toLong() * 1000L

        navigationBarHeight = ctx.getNavigationBarHeight()
        statusBarHeight = ctx.getStatusBarHeight()

        swipeHorizontalEnabled =
          settingsManager.getBoolean(ctx.getString(R.string.swipe_enabled_key), true)
        swipeVerticalEnabled =
          settingsManager.getBoolean(
            ctx.getString(R.string.swipe_vertical_enabled_key),
            true
          )
        playBackSpeedEnabled = settingsManager.getBoolean(
          ctx.getString(R.string.playback_speed_enabled_key),
          true
        )
        playerRotateEnabled = settingsManager.getBoolean(
          ctx.getString(R.string.rotate_video_key),
          false
        )
        autoPlayerRotateEnabled = settingsManager.getBoolean(
          ctx.getString(R.string.auto_rotate_video_key),
          false
        )
        playerResizeEnabled =
          settingsManager.getBoolean(
            ctx.getString(R.string.player_resize_enabled_key),
            true
          )
        doubleTapEnabled =
          settingsManager.getBoolean(
            ctx.getString(R.string.double_tap_enabled_key),
            false
          )

        doubleTapPauseEnabled =
          settingsManager.getBoolean(
            ctx.getString(R.string.double_tap_pause_enabled_key),
            false
          )
      }
      playerBinding?.apply {
        playerSpeedBtt.isVisible = playBackSpeedEnabled
        playerResizeBtt.isVisible = playerResizeEnabled
        playerRotateBtt.isVisible = playerRotateEnabled
        var resume = false
        exoProgress.addOnScrubListener(object : PreviewBar.OnScrubListener {
          override fun onScrubStart(previewBar: PreviewBar?) {
            resume = mvpPlayer?.paused == false
            if (resume) mvpPlayer?.paused = true
          }

          override fun onScrubMove(
            previewBar: PreviewBar?,
            progress: Int,
            fromUser: Boolean
          ) {
          }

          override fun onScrubStop(previewBar: PreviewBar?) {
            mvpPlayer?.timePos = previewBar?.progress?.toDouble()
            if (resume) mvpPlayer?.paused = false
          }
        })


      }
    } catch (e: Exception) {
      logError(e)
    }

    playerBinding?.apply {
      playerPausePlay.setOnClickListener {
        togglePlayPause()
      }

      ytOverlay.performListener(object : YouTubeOverlay.PerformListener {
        override fun onAnimationStart() {
          // Do UI changes when circle scaling animation starts (e.g. hide controller views)
          ytOverlay.visibility = View.VISIBLE
        }

        override fun onAnimationEnd() {
          // Do UI changes when circle scaling animation starts (e.g. show controller views)
          ytOverlay.visibility = View.GONE
        }
      })
      ytOverlay.seekSeconds((fastForwardTime / 1000).toInt());

      exoDuration.setOnClickListener {
        //setRemainingTimeCounter(true)
      }

      timeLeft.setOnClickListener {
        //setRemainingTimeCounter(false)
      }

      skipChapterButton.setOnClickListener {
        //mvpPlayer?.handleEvent(CSPlayerEvent.SkipCurrentChapter)
      }

      playerRotateBtt.setOnClickListener {
        autoHide()
        //toggleRotate()
      }

      // init clicks
      playerResizeBtt.setOnClickListener {
        autoHide()
        nextResize()
        setReizeIcon()
      }

      playerSpeedBtt.setOnClickListener {
        showSpeedDialog()
      }

      playerSkipOp.setOnClickListener {
        autoHide()
        //skipOp()
      }

      playerSkipEpisode.setOnClickListener {
        autoHide()
        //mvpPlayer?.handleEvent(CSPlayerEvent.NextEpisode)
      }

      playerLock.setOnClickListener {
        autoHide()
        toggleLock()
      }

      playerSubtitleOffsetBtt.setOnClickListener {
        showSubtitleOffsetDialog()
      }

      exoRew.setOnClickListener {
        autoHide()
        rewind()
      }

      exoFfwd.setOnClickListener {
        autoHide()
        fastForward()
      }

      playerGoBack.setOnClickListener {
        activity?.popCurrentPage()
        mvpPlayer?.timePos
          ?.let { CommonActivitty.activityResultEvent?.invoke(Activity.RESULT_OK, it.toLong()) }
      }

      playerGoSetting.setOnClickListener {
        findNavController().navigate(R.id.navigation_settings_player)
      }
      playerSourcesBtt.setOnClickListener {
        showSourcesDialog()
      }

      playerTracksBtt.setOnClickListener {
        // showTracksDialogue()
      }

      // it is !not! a bug that you cant touch the right side, it does not register inputs on navbar or status bar
//      playerHolder.setOnTouchListener { callView, event ->
//        return@setOnTouchListener handleMotionEvent(callView, event)
//      }

      playerMediaRouteButton.apply {
        val chromecastSupport = true;//api?.hasChromecastSupport == true
        alpha = if (chromecastSupport) 1f else 0.3f
        if (!chromecastSupport) {
          setOnClickListener {
            showToast(
              R.string.no_chromecast_support_toast,
              Toast.LENGTH_LONG
            )
          }
        }
        activity?.let { act ->
          if (act.isCastApiAvailable()) {
            try {
              CastButtonFactory.setUpMediaRouteButton(act, this)
              val castContext = CastContext.getSharedInstance(act.applicationContext)
              isGone = castContext.castState == CastState.NO_DEVICES_AVAILABLE
              // this shit leaks for some reason
              //castContext.addCastStateListener { state ->
              //    media_route_button?.isGone = state == CastState.NO_DEVICES_AVAILABLE
              //}
            } catch (e: Exception) {
              logError(e)
            }
          }
        }
      }
      exoProgress.setOnTouchListener { _, event ->
        // this makes the bar not disappear when sliding
        when (event.action) {
          MotionEvent.ACTION_DOWN -> {
            currentTapIndex++
          }

          MotionEvent.ACTION_MOVE -> {
            currentTapIndex++
          }

          MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_BUTTON_RELEASE -> {
            autoHide()
          }
        }
        return@setOnTouchListener false
      }

    }
  }

  private fun showSubtitleOffsetDialog() {
    val ctx = context ?: return

    val binding = SubtitleOffsetBinding.inflate(LayoutInflater.from(ctx), null, false)

    val builder =
      AlertDialog.Builder(ctx, R.style.AlertDialogCustom)
        .setView(binding.root)
    val dialog = builder.create()
    dialog.show()

    val beforeOffset = subtitleDelay

    /*val applyButton = dialog.findViewById<TextView>(R.id.apply_btt)!!
    val cancelButton = dialog.findViewById<TextView>(R.id.cancel_btt)!!
    val input = dialog.findViewById<EditText>(R.id.subtitle_offset_input)!!
    val sub = dialog.findViewById<ImageView>(R.id.subtitle_offset_subtract)!!
    val subMore = dialog.findViewById<ImageView>(R.id.subtitle_offset_subtract_more)!!
    val add = dialog.findViewById<ImageView>(R.id.subtitle_offset_add)!!
    val addMore = dialog.findViewById<ImageView>(R.id.subtitle_offset_add_more)!!
    val subTitle = dialog.findViewById<TextView>(R.id.subtitle_offset_sub_title)!!*/
    binding.apply {
      subtitleOffsetInput.doOnTextChanged { text, _, _, _ ->
        text?.toString()?.toLongOrNull()?.let { time ->
          subtitleDelay = time
          val str = when {
            time > 0L -> {
              txt(R.string.subtitle_offset_extra_hint_later_format, time)
            }

            time < 0L -> {
              txt(R.string.subtitle_offset_extra_hint_before_format, -time)
            }

            else -> {
              txt(R.string.subtitle_offset_extra_hint_none_format)
            }
          }
          subtitleOffsetSubTitle.setText(str)
        }
      }
      subtitleOffsetInput.text =
        Editable.Factory.getInstance()?.newEditable(beforeOffset.toString())

      val buttonChange = 100L
      val buttonChangeMore = 1000L

      fun changeBy(by: Long) {
        val current = (subtitleOffsetInput.text?.toString()?.toLongOrNull() ?: 0) + by
        subtitleOffsetInput.text =
          Editable.Factory.getInstance()?.newEditable(current.toString())
      }

      subtitleOffsetAdd.setOnClickListener {
        changeBy(buttonChange)
      }
      subtitleOffsetAddMore.setOnClickListener {
        changeBy(buttonChangeMore)
      }
      subtitleOffsetSubtract.setOnClickListener {
        changeBy(-buttonChange)
      }
      subtitleOffsetSubtractMore.setOnClickListener {
        changeBy(-buttonChangeMore)
      }

      dialog.setOnDismissListener {
          activity?.hideSystemUI()
      }
      applyBtt.setOnClickListener {
        dialog.dismissSafe(activity)
        //player.seekTime(1L)
      }
      resetBtt.setOnClickListener {
        subtitleDelay = 0
        dialog.dismissSafe(activity)
        //player.seekTime(1L)
      }
      cancelBtt.setOnClickListener {
        subtitleDelay = beforeOffset
        dialog.dismissSafe(activity)
      }
    }
  }

  private fun showSpeedDialog() {
    val speedsText =
      listOf(
        "0.5x",
        "0.75x",
        "1x",
        "1.25x",
        "1.5x",
        "1.75x",
        "2x"
      )
    val speedsNumbers =
      listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    val speedIndex = speedsNumbers.indexOf(mvpPlayer?.playbackSpeed?.toFloat() ?: 1.0f)

    activity?.let { act ->
      act.showDialog(
        speedsText,
        speedIndex,
        act.getString(R.string.player_speed),
        false,
        {
          activity?.hideSystemUI()
        }) { index ->
          setPlayBackSpeed(speedsNumbers[index])
      }
    }
  }

  private fun loadLink(link: Pair<ExtractorLink?, ExtractorUri?>, sub: SubtitleData? = null) {
    currentSelectedLink = link
    if (decodeMode == DecodeMode.swDec) {
      pushOption("hwdec", "no")
    }
    pushOption(
      "force-media-title",
      currentSelectedLink?.first?.name ?: currentSelectedLink?.first?.url!!
    )

    pushOption(
      "start",
      "${if (psc.position > 0) psc.positionSec else (currentSelectedLink?.first?.position ?: 0L) / 1000}"
    )

    mvpPlayer?.playFile(currentSelectedLink?.first?.url ?: "", currentSelectedLink?.first?.headers)
    playerBinding?.playerBuffering?.isVisible = true

    try {
      uiReset()
    } catch (e: Exception) {
      logError(e)
    }
  }

  fun pushOption(key: String, value: String) {
    onloadCommands.add(arrayOf("set", "file-local-options/${key}", value))
  }

  fun togglePlayPause() {
    mvpPlayer?.paused = mvpPlayer?.paused?.not();
    if (mvpPlayer?.paused == false)
      autoHide()
  }

  private fun rewind() {
    try {
      playerBinding?.apply {
        playerCenterMenu.isGone = false
        playerRewHolder.alpha = 1f

        val rotateLeft = AnimationUtils.loadAnimation(context, R.anim.rotate_left)
        exoRew.startAnimation(rotateLeft)
//        val width = resources.displayMetrics.widthPixels
//        ytOverlay.onDoubleTapProgressUp(width, width/2.0f - 20.0f, playerView?.height!!/2.0f)

        val goLeft = AnimationUtils.loadAnimation(context, R.anim.go_left)
        goLeft.setAnimationListener(object : Animation.AnimationListener {
          override fun onAnimationStart(animation: Animation?) {}

          override fun onAnimationRepeat(animation: Animation?) {}

          override fun onAnimationEnd(animation: Animation?) {
            exoRewText.post {
              resetRewindText()
              playerCenterMenu.isGone = !isShowing
              playerRewHolder.alpha = if (isShowing) 1f else 0f
            }
          }
        })
        exoRewText.startAnimation(goLeft)
        exoRewText.text =
          getString(R.string.rew_text_format).format(fastForwardTime / 1000)
      }
      mvpPlayer?.timePos = mvpPlayer?.timePos?.plus(-fastForwardTime / 1000)
    } catch (e: Exception) {
      logError(e)
    }
  }

  private fun fastForward() {
    try {
      playerBinding?.apply {
        playerCenterMenu.isGone = false
        playerFfwdHolder.alpha = 1f
        val rotateRight = AnimationUtils.loadAnimation(context, R.anim.rotate_right)
        exoFfwd.startAnimation(rotateRight)

        val goRight = AnimationUtils.loadAnimation(context, R.anim.go_right)
        goRight.setAnimationListener(object : Animation.AnimationListener {
          override fun onAnimationStart(animation: Animation?) {}

          override fun onAnimationRepeat(animation: Animation?) {}

          override fun onAnimationEnd(animation: Animation?) {
            exoFfwdText.post {
              resetFastForwardText()
              playerCenterMenu.isGone = !isShowing
              playerFfwdHolder.alpha = if (isShowing) 1f else 0f
            }
          }
        })
        exoFfwdText.startAnimation(goRight)
        exoFfwdText.text =
          getString(R.string.ffw_text_format).format(fastForwardTime / 1000)
      }
      mvpPlayer?.timePos = mvpPlayer?.timePos?.plus(fastForwardTime / 1000)
    } catch (e: Exception) {
      logError(e)
    }
  }

  fun resetRewindText() {
    playerBinding?.exoRewText?.text =
      getString(R.string.rew_text_regular_format).format(fastForwardTime / 1000)
  }

  fun resetFastForwardText() {
    playerBinding?.exoFfwdText?.text =
      getString(R.string.ffw_text_regular_format).format(fastForwardTime / 1000)
  }

  protected fun uiReset() {
    isShowing = false
    // if nothing has loaded these buttons should not be visible
    playerBinding?.apply {
      playerSkipEpisode.isVisible = false
      playerSkipOp.isVisible = false
      shadowOverlay.isVisible = false
      //playerSourcesBtt.isVisible = false
      setReizeIcon()
    }
    //updateLockUI()
    updateUIVisibility()
    animateLayoutChanges()
    resetFastForwardText()
    resetRewindText()
    updateMetadataDisplay()
    mvpPlayer?.loadTracks()
  }

  fun nextResize() {
    resizeMode = (resizeMode + 1) % PlayerResize.values().size
    resize(resizeMode, true)
  }

  fun resize(resize: Int, showToast: Boolean) {
    resize(PlayerResize.values()[resize], showToast)
  }

  fun getScreenAspectRatioString(): String {

    val width = resources.displayMetrics.widthPixels
    val height = resources.displayMetrics.heightPixels

    // Find the greatest common divisor (GCD) of width and height
    fun gcd(a: Int, b: Int): Int {
      return if (b == 0) a else gcd(b, a % b)
    }

    val gcd = gcd(width, height)

    // Return the aspect ratio in the format "width:height"
    return "${width / gcd}:${height / gcd}"
  }

  @SuppressLint("UnsafeOptInUsageError")
  fun resize(resize: PlayerResize, showToast: Boolean) {
    DataStore.resizeMode = resize.ordinal
    when (resize) {
      PlayerResize.Zoom -> {
        MPVLib.setPropertyString("video-aspect-override", "-1")
        MPVLib.setPropertyDouble("panscan", 1.0)
      }

      PlayerResize.Fill -> {
        MPVLib.setPropertyString("video-aspect-override", getScreenAspectRatioString())
        MPVLib.setPropertyDouble("panscan", 1.0)
      }

      PlayerResize.Fit -> {
        MPVLib.setPropertyString("video-aspect-override", "16:9")
        MPVLib.setPropertyDouble("panscan", 0.0)
      }
    }

    if (showToast)
      showToast(resize.nameRes, Toast.LENGTH_SHORT)
  }

  @OptIn(UnstableApi::class)
  private fun setReizeIcon() {
    //  val resize = MPVLib.getPropertyString("video-params/aspect")
//    when(mvpPlayer?.resizeMode) {
//      AspectRatioFrameLayout.RESIZE_MODE_FIT -> {
//        playerBinding?.playerResizeBtt?.icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_baseline_resize_fit_24, null)
//      }
//      AspectRatioFrameLayout.RESIZE_MODE_FILL -> {
//        playerBinding?.playerResizeBtt?.icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_baseline_resize_stretch_24, null)
//      }
//      AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> {
//        playerBinding?.playerResizeBtt?.icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_baseline_resize_zoom_24, null)
//      }
//
//      else -> {
//        //nothing todo
//      }
//    }
  }


  private var isCurrentTouchValid = false
  private var currentTouchStart: Utils.Vector2? = null
  private var currentTouchLast: Utils.Vector2? = null
  private var currentTouchAction: TouchAction? = null
  private var currentLastTouchAction: TouchAction? = null
  private var currentTouchStartPlayerTime: Long? =
    null // the time in the player when you first click
  private var currentTouchStartTime: Long? = null // the system time when you first click
  private var currentLastTouchEndTime: Long = 0 // the system time when you released your finger
  private var currentClickCount: Int =
    0 // amount of times you have double clicked, will reset when other action is taken

  // requested volume and brightness is used to make swiping smoother
  // to make it not jump between values,
  // this value is within the range [0,1]
  private var currentRequestedVolume: Float = 0.0f
  private var currentRequestedBrightness: Float = 1.0f

  enum class TouchAction {
    Brightness,
    Volume,
    Time,
  }


  private var currentTapIndex = 0
  protected fun autoHide() {
    currentTapIndex++
    val index = currentTapIndex
    playerBinding?.playerHolder?.postDelayed({
      if (!isCurrentTouchValid && isShowing && index == currentTapIndex && activityIsForeground) {
        toggleControls()
      }
    }, 2000)
  }

  private fun setPlayBackSpeed(speed: Float) {
    try {
      DataStore.playBackSpeed = speed
      playerBinding?.playerSpeedBtt?.text =
        getString(R.string.player_speed_text_format).format(speed)
          .replace(".0x", "x")
    } catch (e: Exception) {
      // the format string was wrong
      logError(e)
    }

    mvpPlayer?.playbackSpeed = speed.toDouble()
  }

  override fun onResume() {
    enterFullscreen()
    if (activityIsForeground) {
      super.onResume()
      return
    }

    mvpPlayer?.paused = false
    activityIsForeground = true

    uiReset()
    super.onResume()
  }


  override fun onDestroyView() {
    exitFullscreen()
    playerBinding = null
    Log.v(TAG, "Exiting.")

    // Suppress any further callbacks
    activityIsForeground = false

    BackgroundPlaybackService.mediaToken = null
    mediaSession?.let {
      it.isActive = false
      it.release()
    }
    mediaSession = null

    audioFocusRequest?.let {
      AudioManagerCompat.abandonAudioFocusRequest(audioManager!!, it)
    }
    audioFocusRequest = null

    // take the background service with us
    stopServiceRunnable.run()

    mvpPlayer?.removeObserver(this)
    mvpPlayer?.destroy()

    super.onDestroyView()
  }

  private val stopServiceRunnable = Runnable {
    val intent = Intent(requireActivity(), BackgroundPlaybackService::class.java)
    requireActivity().applicationContext.stopService(intent)

  }

  private fun toggleControls() {
    isShowing = !isShowing
    autoHide()
    animateLayoutChanges()
  }

  protected fun animateLayoutChanges() {
    if (isShowing) {
      updateUIVisibility()
    } else {
      playerBinding?.playerHolder?.postDelayed({ updateUIVisibility() }, 200)
    }

    val titleMove = if (isShowing) 0f else -50.toPx.toFloat()
    playerBinding?.playerVideoTitle?.let {
      ObjectAnimator.ofFloat(it, "translationY", titleMove).apply {
        duration = 200
        start()
      }
    }
    playerBinding?.playerVideoTitleRez?.let {
      ObjectAnimator.ofFloat(it, "translationY", titleMove).apply {
        duration = 200
        start()
      }
    }
    val playerBarMove = if (isShowing) 0f else 70.toPx.toFloat()
    playerBinding?.bottomPlayerBar?.let {
      ObjectAnimator.ofFloat(it, "translationY", playerBarMove).apply {
        duration = 200
        start()
      }
    }

    val fadeTo = if (isShowing) 1f else 0f
    val fadeAnimation = AlphaAnimation(1f - fadeTo, fadeTo)

    fadeAnimation.duration = 100
    fadeAnimation.fillAfter = true

//    val sView = subView
//    val sStyle = subStyle
//    if (sView != null && sStyle != null) {
//      val move = if (isShowing) (-70.toPx.toFloat() -sStyle.elevation.toPx.toFloat())else -sStyle.elevation.toPx.toFloat()
//      ObjectAnimator.ofFloat(sView, "translationY", move).apply {
//        duration = 200
//        start()
//      }
//    }

    val playerSourceMove = if (isShowing) 0f else -50.toPx.toFloat()


    playerBinding?.apply {
      playerOpenSource.let {
        ObjectAnimator.ofFloat(it, "translationY", playerSourceMove).apply {
          duration = 200
          start()
        }
      }

      if (!isLocked) {
        playerFfwdHolder.alpha = 1f
        playerRewHolder.alpha = 1f
        shadowOverlay.isVisible = true
        shadowOverlay.startAnimation(fadeAnimation)
        playerFfwdHolder.startAnimation(fadeAnimation)
        playerRewHolder.startAnimation(fadeAnimation)
        playerPausePlay.startAnimation(fadeAnimation)
      }

      bottomPlayerBar.startAnimation(fadeAnimation)
      playerOpenSource.startAnimation(fadeAnimation)
      playerTopHolder.startAnimation(fadeAnimation)
    }
  }

  fun updateUIVisibility() {
    val isGone = isLocked || !isShowing
    var togglePlayerTitleGone = isGone
    context?.let {
      val settingsManager = PreferenceManager.getDefaultSharedPreferences(it)
      val limitTitle = settingsManager.getInt(getString(R.string.prefer_limit_title_key), 0)
      if (limitTitle < 0) {
        togglePlayerTitleGone = true
      }
    }
    playerBinding?.apply {
      playerLockHolder.isGone = isGone
      playerVideoBar.isGone = isGone
      playerPausePlay.isGone = isGone
      playerTopHolder.isGone = isGone
      playerVideoTitle.isGone = togglePlayerTitleGone
      playerEpisodeFiller.isGone = isGone
      playerCenterMenu.isGone = isGone
      playerLock.isGone = !isShowing
      playerGoBackHolder.isGone = isGone
      playerSkipEpisode.isClickable = !isGone
      playerSourcesBtt.isGone = isGone
      playerEpisodeFillerHolder.isGone = true
    }
  }

  private fun toggleLock() {
    isLocked = !isLocked
    if (isLocked && isShowing) {
      playerBinding?.playerHolder?.postDelayed({
        if (isLocked && isShowing) {
          toggleControls()
        }
      }, 200)
    }

    val fadeTo = if (isLocked) 0f else 1f
    playerBinding?.apply {
      val fadeAnimation = AlphaAnimation(playerVideoTitle.alpha, fadeTo).apply {
        duration = 100
        fillAfter = true
      }

      updateUIVisibility()
      // MENUS
      //centerMenu.startAnimation(fadeAnimation)
      playerPausePlay.startAnimation(fadeAnimation)
      playerFfwdHolder.startAnimation(fadeAnimation)
      playerRewHolder.startAnimation(fadeAnimation)
      playerMediaRouteButton.startAnimation(fadeAnimation)
      //TITLE
      playerVideoTitleRez.startAnimation(fadeAnimation)
      playerEpisodeFiller.startAnimation(fadeAnimation)
      playerVideoTitle.startAnimation(fadeAnimation)
      playerTopHolder.startAnimation(fadeAnimation)
      playerGoSettingHolder.startAnimation(fadeAnimation)
      // BOTTOM
      playerLockHolder.startAnimation(fadeAnimation)
      shadowOverlay.isVisible = true
      shadowOverlay.startAnimation(fadeAnimation)
    }
    //updateLockUI()
  }
//  private fun updateLockUI() {
//    playerBinding?.apply {
//      playerLock.setIconResource(if (isLocked) R.drawable.video_locked else R.drawable.video_unlocked)
//      if (layout == R.layout.fragment_player) {
//        val color = if (isLocked) context?.colorFromAttribute(R.attr.colorPrimary)
//        else Color.WHITE
//        if (color != null) {
//          playerLock.setTextColor(color)
//          playerLock.iconTint = ColorStateList.valueOf(color)
//          playerLock.rippleColor =
//            ColorStateList.valueOf(Color.argb(50, color.red, color.green, color.blue))
//        }
//      }
//    }
//  }

  protected fun enterFullscreen() {
    activity?.hideSystemUI()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      val params = activity?.window?.attributes
      params?.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
      activity?.window?.attributes = params
    }
  }


  protected fun exitFullscreen() {
    //if (lockRotation)
    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER

    // simply resets brightness and notch settings that might have been overridden
    val lp = activity?.window?.attributes
    lp?.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      lp?.layoutInDisplayCutoutMode =
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
    }
    activity?.window?.attributes = lp
    activity?.showSystemUI()
  }

  override fun eventProperty(property: String) {
    val metaUpdated = psc.update(property)
    if (metaUpdated)
      updateMediaSession()
    if (property == "loop-file" || property == "loop-playlist") {
      mediaSession?.setRepeatMode(
        when (mvpPlayer?.getRepeat()) {
          2 -> PlaybackStateCompat.REPEAT_MODE_ONE
          1 -> PlaybackStateCompat.REPEAT_MODE_ALL
          else -> PlaybackStateCompat.REPEAT_MODE_NONE
        }
      )
    }

    if (!activityIsForeground) return
    eventUiHandler.post { eventPropertyUi(property, null, metaUpdated) }
  }

  override fun eventProperty(property: String, value: Long) {
    Log.v(TAG, property)
    if (psc.update(property, value))
      updateMediaSession()

    if (!activityIsForeground) return
    eventUiHandler.post { eventPropertyUi(property, value) }
  }

  override fun eventProperty(property: String, value: Boolean) {
    if (psc.update(property, value))
      updateMediaSession()
    if (property == "shuffle") {
      mediaSession?.setShuffleMode(
        if (value)
          PlaybackStateCompat.SHUFFLE_MODE_ALL
        else
          PlaybackStateCompat.SHUFFLE_MODE_NONE
      )
    }

    if (!activityIsForeground) return
    eventUiHandler.post { eventPropertyUi(property, value) }
  }

  override fun eventProperty(property: String, value: String) {
    val metaUpdated = psc.update(property, value)
    if (metaUpdated)
      updateMediaSession()

    if (!activityIsForeground) return
    eventUiHandler.post { eventPropertyUi(property, value, metaUpdated) }
  }

  override fun eventProperty(property: String, value: Double) {
    if (psc.update(property, value))
      updateMediaSession()

    if (!activityIsForeground) return
    eventUiHandler.post { eventPropertyUi(property, value) }
  }

  override fun event(eventId: Int) {
    if (eventId == MPVLib.mpvEventId.MPV_EVENT_SHUTDOWN)
      finishWithResult(if (playbackHasStarted) RESULT_OK else RESULT_CANCELED)


    if (eventId == MPVLib.mpvEventId.MPV_EVENT_START_FILE) {
      for (c in onloadCommands)
        MPVLib.command(c)
      onloadCommands.clear()
      playbackHasStarted = true
    }
    if (!activityIsForeground) return
    eventUiHandler.post {
      if (eventId == MPVLib.mpvEventId.MPV_EVENT_SEEK) {
        playerBinding?.playerBuffering?.isVisible = true
      }
      if (eventId == MPVLib.mpvEventId.MPV_EVENT_PLAYBACK_RESTART) {
        playerBinding?.playerBuffering?.isVisible = false
      }
    }
  }


  // mpv events

  private fun eventPropertyUi(property: String, dummy: Any?, metaUpdated: Boolean) {
    if (!activityIsForeground) return
    when (property) {
      "track-list" -> mvpPlayer?.loadTracks()
      "video-format" -> {
        //updateAudioUI()
      }

      "hwdec-current" -> {
        //updateDecoderButton()
      }
    }
    if (metaUpdated)
      updateMetadataDisplay()
  }

  private fun eventPropertyUi(property: String, value: Boolean) {
    if (!activityIsForeground) return
    when (property) {
      "pause" -> {
        playerBinding?.playerPausePlay?.setImageResource(if (!value) R.drawable.netflix_pause else R.drawable.netflix_play)
      }

      "paused-for-cache" -> {
        playerBinding?.playerBuffering?.isVisible = value
      }
    }
  }

  private fun eventPropertyUi(property: String, value: Long) {
    if (!activityIsForeground) return
    when (property) {
      "time-pos" -> updatePlaybackPos(value.toInt())
      "playlist-pos", "playlist-count" -> {
        //updatePlaylistButtons()
      }
    }
  }

  private fun updatePlaybackPos(position: Int) {
    playerBinding?.exoPosition?.text = MPVUtils.prettyTime(position)
    val diff = psc.durationSec - position
    playerBinding?.exoDuration?.text = if (diff <= 0)
      "-00:00"
    else
      MPVUtils.prettyTime(-diff, true)
    playerBinding?.exoProgress?.setPosition((position).toLong())
    // Note: do NOT add other update functions here just because this is called every second.
    // Use property observation instead.
    //updateStats()
  }

  private fun eventPropertyUi(property: String, value: Double) {
    if (!activityIsForeground) return
    when (property) {
      "duration/full" -> {
        playerBinding?.exoDuration?.text = MPVUtils.prettyTime(psc.durationSec)
        playerBinding?.exoProgress?.setDuration(psc.durationSec.toLong())
      }

      "video-params/aspect", "video-params/rotate" -> {
//        updateOrientation()
//        updatePiPParams()
      }
    }
  }

  //  private fun updateOrientation(initial: Boolean = false) {
//    // screen orientation is fixed (Android TV)
//    if (!requireActivity().packageManager.hasSystemFeature(PackageManager.FEATURE_SCREEN_PORTRAIT))
//      return
//
//    if (autoRotationMode != "auto") {
//      if (!initial)
//        return // don't reset at runtime
//      requestedOrientation = when (autoRotationMode) {
//        "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
//        "portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
//        else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
//      }
//    }
//    if (initial || player.vid == -1)
//      return
//
//    val ratio = player.getVideoAspect()?.toFloat() ?: 0f
//    if (ratio == 0f || ratio in (1f / ASPECT_RATIO_MIN) .. ASPECT_RATIO_MIN) {
//      // video is square, let Android do what it wants
//      requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
//      return
//    }
//    requestedOrientation = if (ratio > 1f)
//      ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
//    else
//      ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
//  }
  private fun eventPropertyUi(property: String, value: String, metaUpdated: Boolean) {
    if (!activityIsForeground) return
    when (property) {
      "speed" -> {
        //updateSpeedButton()
      }
    }
    if (metaUpdated)
      updateMetadataDisplay()
  }

  private fun updateMetadataDisplay() {
    playerBinding?.playerVideoTitle?.text = psc.meta.formatTitle()
    playerBinding?.playerVideoTitleRez?.text = psc.meta.formatArtistAlbum()
  }

  private fun finishWithResult(code: Int, includeTimePos: Boolean = false) {
    // Refer to http://mpv-android.github.io/mpv-android/intent.html
    // FIXME: should track end-file events to accurately report OK vs CANCELED
    if (activity?.isFinishing == true) // only count first call
      return
    val result = Intent(RESULT_INTENT)
    val intent = requireActivity().intent
    result.data = if (intent.data?.scheme == "file") null else intent.data
    if (includeTimePos) {
      result.putExtra("position", psc.position.toInt())
      result.putExtra("duration", psc.duration.toInt())
    }
    requireActivity().setResult(code, result)
    //finish()
  }

  override fun onPause() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      if (requireActivity().isInMultiWindowMode || requireActivity().isInPictureInPictureMode) {
        Log.v(TAG, "Going into multi-window mode")
        super.onPause()
        return
      }
    }
    onPauseImpl()
    super.onPause()

  }

  private fun isPlayingAudioOnly(): Boolean {
    if (mvpPlayer?.aid == -1)
      return false
    val fmt = MPVLib.getPropertyString("video-format")
    return fmt.isNullOrEmpty() || arrayOf("mjpeg", "png", "bmp").indexOf(fmt) != -1
  }

  private fun shouldBackground(): Boolean {
    if (requireActivity().isFinishing) // about to exit?
      return false
    return when (backgroundPlayMode) {
      "always" -> true
      "audio-only" -> isPlayingAudioOnly()
      else -> false // "never"
    }
  }

  private fun updateMediaSession() {
    synchronized(psc) {
      mediaSession?.let { psc.write(it) }
    }
  }

  private fun onPauseImpl() {
    val fmt = MPVLib.getPropertyString("video-format")
    val shouldBackground = shouldBackground()
    if (shouldBackground && !fmt.isNullOrEmpty())
      BackgroundPlaybackService.thumbnail = MPVLib.grabThumbnail(THUMB_SIZE)
    else
      BackgroundPlaybackService.thumbnail = null
    // media session uses the same thumbnail
    updateMediaSession()

    activityIsForeground = false
    eventUiHandler.removeCallbacksAndMessages(null)
    if (activity?.isFinishing == true) {
      savePosition()
      // tell mpv to shut down so that any other property changes or such are ignored,
      // preventing useless busywork
      MPVLib.command(arrayOf("stop"))
    } else if (!shouldBackground) {
      mvpPlayer?.paused = true;
    }
    super.onPause()

    if (shouldBackground) {
      Log.v(TAG, "Resuming playback in background")
      stopServiceHandler.removeCallbacks(stopServiceRunnable)
      val serviceIntent = Intent(requireActivity(), BackgroundPlaybackService::class.java)
      ContextCompat.startForegroundService(requireActivity(), serviceIntent)
    }
  }

  private fun savePosition() {
    if (MPVLib.getPropertyBoolean("eof-reached") ?: true) {
      Log.d(TAG, "player indicates EOF, not saving watch-later config")
      return
    }
    MPVLib.command(arrayOf("write-watch-later-config"))
  }

  private fun initMediaSession(): MediaSessionCompat {
    /*
        https://developer.android.com/guide/topics/media-apps/working-with-a-media-session
        https://developer.android.com/guide/topics/media-apps/audio-app/mediasession-callbacks
        https://developer.android.com/reference/android/support/v4/media/session/MediaSessionCompat
     */
    val session = MediaSessionCompat(requireActivity(), TAG)
    session.setFlags(0)
    session.setCallback(object : MediaSessionCompat.Callback() {
      override fun onPause() {
        mvpPlayer?.paused = true
      }

      override fun onPlay() {
        mvpPlayer?.paused = false
      }

      override fun onSeekTo(pos: Long) {
        mvpPlayer?.timePos = (pos / 1000.0)
      }

      override fun onSkipToNext() = playlistNext()
      override fun onSkipToPrevious() = playlistPrev()
      override fun onSetRepeatMode(repeatMode: Int) {
        MPVLib.setPropertyString(
          "loop-playlist",
          if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ALL) "inf" else "no"
        )
        MPVLib.setPropertyString(
          "loop-file",
          if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ONE) "inf" else "no"
        )
      }

      override fun onSetShuffleMode(shuffleMode: Int) {
        mvpPlayer?.changeShuffle(false, shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL)
      }
    })
    return session
  }

  private fun playlistPrev() = MPVLib.command(arrayOf("playlist-prev"))
  private fun playlistNext() = MPVLib.command(arrayOf("playlist-next"))

  private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { type ->
    Log.v(TAG, "Audio focus changed: $type")
    if (ignoreAudioFocus)
      return@OnAudioFocusChangeListener
    when (type) {
      AudioManager.AUDIOFOCUS_LOSS,
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
        // loss can occur in addition to ducking, so remember the old callback
        val oldRestore = audioFocusRestore
        val wasPlayerPaused = mvpPlayer?.paused ?: false
        mvpPlayer?.paused = true
        audioFocusRestore = {
          oldRestore()
          if (!wasPlayerPaused) mvpPlayer?.paused = false
        }
      }

      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
        MPVLib.command(arrayOf("multiply", "volume", AUDIO_FOCUS_DUCKING.toString()))
        audioFocusRestore = {
          val inv = 1f / AUDIO_FOCUS_DUCKING
          MPVLib.command(arrayOf("multiply", "volume", inv.toString()))
        }
      }

      AudioManager.AUDIOFOCUS_GAIN -> {
        audioFocusRestore()
        audioFocusRestore = {}
      }
    }
  }


  var selectSourceDialog: Dialog? = null
  fun showSourcesDialog() {
    try {

      //println("CURRENT SELECTED :$currentSelectedSubtitles of $currentSubs")
      context?.let { ctx ->
        mvpPlayer?.paused = true
        val currentSubtitles = sortSubs(currentSubs)

        val sourceDialog = Dialog(ctx, R.style.AlertDialogCustom)
        val binding =
          PlayerSelectSourceAndSubsBinding.inflate(LayoutInflater.from(ctx), null, false)
        sourceDialog.setContentView(binding.root)

        selectSourceDialog = sourceDialog

        sourceDialog.show()
        val providerList = binding.sortProviders
        var shouldDismiss = true

        fun dismiss() {
          mvpPlayer?.paused = false
          activity?.hideSystemUI()
        }

        var startSource = 0
        var sortedUrls = allLinks
        var sourceIndex = allLinks.indexOf(currentSelectedLink)
        val sourcesArrayAdapter =
          ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)

        sourcesArrayAdapter.addAll(sortedUrls.mapIndexed { index, (link, uri) ->
          "${index + 1}. " + (link?.source ?: uri?.name ?: "NULL")
        })

        providerList.choiceMode = AbsListView.CHOICE_MODE_SINGLE
        providerList.adapter = sourcesArrayAdapter
        providerList.setSelection(sourceIndex)
        providerList.setItemChecked(sourceIndex, true)

        providerList.setOnItemClickListener { _, _, which, _ ->
          sourceIndex = which
          providerList.setItemChecked(which, true)
        }

        sourceDialog.setOnDismissListener {
          if (shouldDismiss) dismiss()
          selectSourceDialog = null
        }

        binding.cancelBtt.setOnClickListener {
          sourceDialog.dismissSafe(activity)
        }

        binding.applyBtt.setOnClickListener {
          sortedUrls.elementAt(sourceIndex).let {
            loadLink(it, currentSelectedSubtitles)
          }
          sourceDialog.dismissSafe(activity)
        }
      }
    } catch (e: Exception) {
      logError(e)
    }
  }

  private fun resolveUri(data: Uri): String? {
    val filepath = when (data.scheme) {
      "file" -> data.path
      "content" -> openContentFd(data)
      // mpv supports data URIs but needs data:// to pass it through correctly
      "data" -> "data://${data.schemeSpecificPart}"
      "http", "https", "rtmp", "rtmps", "rtp", "rtsp", "mms", "mmst", "mmsh", "tcp", "udp", "lavf"
      -> data.toString()

      else -> data.path
    }

    if (filepath == null)
      Log.e(TAG, "unknown scheme: ${data.scheme}")
    return filepath
  }

  private fun openContentFd(uri: Uri): String? {
    val resolver = requireActivity().applicationContext.contentResolver
    Log.v(TAG, "Resolving content URI: $uri")
    val fd = try {
      val desc = resolver.openFileDescriptor(uri, "r")
      desc!!.detachFd()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to open content fd: $e")
      return null
    }
    // See if we skip the indirection and read the real file directly
    val path = MPVUtils.findRealPath(fd)
    if (path != null) {
      Log.v(TAG, "Found real file path: $path")
      ParcelFileDescriptor.adoptFd(fd).close() // we don't need that anymore
      return path
    }
    // Else, pass the fd to mpv
    return "fd://${fd}"
  }

  companion object {
    private const val TAG = "mpv"

    // how long should controls be displayed on screen (ms)
    private const val CONTROLS_DISPLAY_TIMEOUT = 1500L

    // how long controls fade to disappear (ms)
    private const val CONTROLS_FADE_DURATION = 500L

    // resolution (px) of the thumbnail displayed with playback notification
    private const val THUMB_SIZE = 384

    // smallest aspect ratio that is considered non-square
    private const val ASPECT_RATIO_MIN = 1.2f // covers 5:4 and up

    // fraction to which audio volume is ducked on loss of audio focus
    private const val AUDIO_FOCUS_DUCKING = 0.5f

    // request codes for invoking other activities
    private const val RCODE_EXTERNAL_AUDIO = 1000
    private const val RCODE_EXTERNAL_SUB = 1001
    private const val RCODE_LOAD_FILE = 1002

    // action of result intent
    private const val RESULT_INTENT = "is.xyz.mpv.MPVActivity.result"

    // stream type used with AudioManager
    private const val STREAM_TYPE = AudioManager.STREAM_MUSIC

    // precision used by seekbar (1/s)
    private const val SEEK_BAR_PRECISION = 2
  }
}
