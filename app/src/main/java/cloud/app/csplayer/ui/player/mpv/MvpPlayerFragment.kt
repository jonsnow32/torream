package cloud.app.csplayer.ui.player.mpv

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.Editable
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity.RESULT_CANCELED
import androidx.appcompat.app.AppCompatActivity.RESULT_OK
import androidx.core.view.WindowCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.PlayerCustomLayoutBinding
import cloud.app.csplayer.databinding.PlayerSelectSourceAndSubsBinding
import cloud.app.csplayer.databinding.PlayerSelectTracksBinding
import cloud.app.csplayer.databinding.PlayerSelectVideoTracksBinding
import cloud.app.csplayer.databinding.SubtitleOffsetBinding
import cloud.app.csplayer.ui.player.CSPlayerViewModel
import cloud.app.csplayer.ui.player.PlayBackResult
import cloud.app.csplayer.ui.player.PlayerEventType
import cloud.app.csplayer.ui.player.SUBTITLE_DELAY_BUNDLE_KEY
import cloud.app.csplayer.ui.player.exo.DOUBLE_TAB_MAXIMUM_HOLD_TIME
import cloud.app.csplayer.ui.player.exo.DOUBLE_TAB_MINIMUM_TIME_BETWEEN
import cloud.app.csplayer.ui.player.exo.DOUBLE_TAB_PAUSE_PERCENTAGE
import cloud.app.csplayer.ui.player.exo.FullScreenPlayer.Companion.convertTimeToString
import cloud.app.csplayer.ui.player.exo.HORIZONTAL_MULTIPLIER
import cloud.app.csplayer.ui.player.exo.MINIMUM_HORIZONTAL_SWIPE
import cloud.app.csplayer.ui.player.exo.MINIMUM_SEEK_TIME
import cloud.app.csplayer.ui.player.exo.MINIMUM_VERTICAL_SWIPE
import cloud.app.csplayer.ui.player.exo.PlayerResize
import cloud.app.csplayer.ui.player.exo.VERTICAL_MULTIPLIER
import cloud.app.csplayer.ui.player.youtube.YouTubeOverlay
import cloud.app.csplayer.utils.CommonActivitty
import cloud.app.csplayer.utils.CommonActivitty.keyEventListener
import cloud.app.csplayer.utils.CommonActivitty.playerEventListener
import cloud.app.csplayer.utils.CommonActivitty.screenHeight
import cloud.app.csplayer.utils.CommonActivitty.screenWidth
import cloud.app.csplayer.utils.DataStore
import cloud.app.csplayer.utils.ExtractorLink
import cloud.app.csplayer.utils.ExtractorUri
import cloud.app.csplayer.utils.SingleSelectionHelper.showDialog
import cloud.app.csplayer.utils.SubtitleData
import cloud.app.csplayer.utils.SubtitleOrigin
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
import cloud.app.csplayer.utils.Utils.toSubtitleMimeType
import cloud.app.csplayer.utils.hideSystemUI
import cloud.app.csplayer.utils.isTvOrEmulator
import cloud.app.csplayer.utils.observe
import cloud.app.csplayer.utils.setText
import cloud.app.csplayer.utils.txt
import com.github.rubensousa.previewseekbar.PreviewBar
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

enum class DecodeMode {
  hwDec,
  swDec
}

enum class TouchAction {
  Brightness,
  Volume,
  Time,
}

class MvpPlayerFragment : Fragment(), MPVLib.EventObserver {

  private var isSameEpisode: Boolean = false;

  private val viewModel by viewModels<CSPlayerViewModel>()

  // for calls to eventUi() and eventPropertyUi()
  private val eventUiHandler = Handler(Looper.getMainLooper())

  // for use with fadeRunnable1..3
  private val fadeHandler = Handler(Looper.getMainLooper())

  // for use with stopServiceRunnable
  private val stopServiceHandler = Handler(Looper.getMainLooper())


  private var player: MPVView? = null
  private var playerBinding: PlayerCustomLayoutBinding? = null


  // state of player UI
  private var isShowing = true
  private var isLocked = false
  private var resizeMode: Int = 0
  private var backgroundPlayMode = ""

  //settings
  private var fastForwardTime = 10000L
  private var androidTVInterfaceOffSeekTime = 10000L
  private var androidTVInterfaceOnSeekTime = 30000L
  private var swipeHorizontalEnabled = false
  private var swipeVerticalEnabled = false
  private var playBackSpeedEnabled = true
  private var playerResizeEnabled = false
  private var doubleTapEnabled = false
  private var doubleTapPauseEnabled = true
  private var playerRotateEnabled = false
  private var autoPlayerRotateEnabled = false
  private var activityIsForeground = true

  private var subtitleDelay
    set(value) = try {
      player?.subDelay = player?.subDelay?.plus(value)
    } catch (e: Exception) {
      logError(e)
    }
    get() = try {
      -(player?.subDelay?.toLong() ?: 0)
    } catch (e: Exception) {
      logError(e)
      0L
    }

  //private var useSystemBrightness = false
  private var useTrueSystemBrightness = true
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

  // Track if we've already retried with software decoding
  private var triedSwDecFallback = false

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    val view = inflater.inflate(R.layout.fragment_mvp_player, container, false)
    val controllerHolder = view.findViewById<FrameLayout>(R.id.controller_holder)
    player = view.findViewById(R.id.mvpPlayer)
    val childView = inflater.inflate(
      if (context?.isTvOrEmulator() == true) R.layout.player_custom_layout_tv else R.layout.player_custom_layout,
      controllerHolder,
      false
    )
    controllerHolder.addView(childView)
    playerBinding = PlayerCustomLayoutBinding.bind(childView.findViewById(R.id.player_holder))
    return view;
  }

  private val seekActionTime = 30000L
  private var decodeMode = DecodeMode.hwDec;

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    player?.addObserver(this)
    player?.initialize(requireActivity().filesDir.path, requireActivity().cacheDir.path)


    observe(viewModel.allLinks) {
      allLinks = it
      //currentSelectedLink = allLinks.first()
    }
    observe(viewModel.isSameEpisode) {
      isSameEpisode = it;
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
        onloadCommands.add(arrayOf("sub-add", url, flag))
      }
    }

    observe(viewModel.currentSubtitleIndex) { index ->
//      if (index >= 0 && index < currentSubs.size)
//        setSubtitles(currentSubs.elementAt(index))
    }

//    preferredAutoSelectSubtitles = context?.getAutoSelectLanguageISO639_1()


    player?.setOnClickListener {
      autoHide()
      toggleControls()
    }

    mediaSession = initMediaSession()
    updateMediaSession()

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
          player?.paused = true
          //mvpPlayer?.handleEvent(CSPlayerEvent.Pause)
        }

        PlayerEventType.PlayPauseToggle -> {
          togglePlayPause()
        }

        PlayerEventType.Play -> {
          player?.paused = false
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
          player?.timePos = player?.timePos?.plus(seekActionTime)
        }

        PlayerEventType.ShowSpeed -> {
          showSpeedDialog()
        }

        PlayerEventType.SeekBack -> {
          player?.timePos = player?.timePos?.plus(-seekActionTime)
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

    keyEventListener = { eventNav ->
      // Don't hook player keys if player isn't active
      if (player != null) {
        val (event, hasNavigated) = eventNav
        if (event != null)
          handleKeyEvent(event, hasNavigated)
        else false
      } else false
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
            resume = player?.paused == false
            if (resume) player?.paused = true
          }

          override fun onScrubMove(
            previewBar: PreviewBar?,
            progress: Int,
            fromUser: Boolean
          ) {
          }

          override fun onScrubStop(previewBar: PreviewBar?) {
            player?.timePos = previewBar?.progress?.toDouble()
            if (resume) player?.paused = false
          }
        })


      }
    } catch (e: Exception) {
      logError(e)
    }

    playerBinding?.apply {

      playerMoreOptionsBtt.setOnClickListener {

        val hasQualitys = player?.tracks?.get("video")?.let {
          it.size > 1
        }

        playerVideoTracks.isVisible = (hasQualitys == true)

        moreOptions.isGone = moreOptions.isGone.not()
        if (!moreOptions.isGone)
          moreOptions.requestFocus()

        autoHide()
      }

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


      playerRotateBtt.setOnClickListener {
        toggleRotate()
      }

      // init clicks
      playerResizeBtt.setOnClickListener {
        nextResize()
        setReizeIcon()
      }

      playerSpeedBtt.setOnClickListener {
        showSpeedDialog()
      }

      playerSkipOp.setOnClickListener {
        //skipOp()
      }

      playerSkipEpisode.setOnClickListener {
        playNext()
      }

      playerLock.setOnClickListener {
        toggleLock()
      }

      exoRew.setOnClickListener {
        rewind()
      }

      exoFfwd.setOnClickListener {
        fastForward()
      }

      playerGoBack.setOnClickListener {
        player?.timePos
          ?.let {
            CommonActivitty.activityResultEvent?.invoke(
              PlayBackResult(
                RESULT_OK,
                it.toLong() * 1000L,
                "cancel",
                if(isSameEpisode) null else allLinks.indexOf(currentSelectedLink) + 1
              )
            )
          }

        activity?.popCurrentPage()
      }

      playerGoSetting.setOnClickListener {
        findNavController().navigate(R.id.action_navigation_global_to_navigation_settings_player)
      }
      playerSourcesBtt.setOnClickListener {
        showSourcesDialog()
      }

      playerTracksBtt.setOnClickListener {
        showTracksDialogue()
      }

      playerVideoTracks.setOnClickListener {
        showVideoTracks()
      }

      playerCodecBtt.setOnClickListener {
        showCodecsDialog()
      }

      // it is !not! a bug that you cant touch the right side, it does not register inputs on navbar or status bar
      playerHolder.setOnTouchListener { callView, event ->
        return@setOnTouchListener handleMotionEvent(callView, event)
      }

      playerMediaRouteButton.apply {
        val chromecastSupport = false
        alpha = if (chromecastSupport) 1f else 0.3f
        if (!chromecastSupport) {
          setOnClickListener {
            showToast(
              R.string.no_chromecast_support_toast,
              Toast.LENGTH_LONG
            )
          }
        }
//        activity?.let { act ->
//          if (act.isCastApiAvailable()) {
//            try {
//              CastButtonFactory.setUpMediaRouteButton(act, this)
//              val castContext = CastContext.getSharedInstance(act.applicationContext)
//              isGone = castContext.castState == CastState.NO_DEVICES_AVAILABLE
//              // this shit leaks for some reason
//              //castContext.addCastStateListener { state ->
//              //    media_route_button?.isGone = state == CastState.NO_DEVICES_AVAILABLE
//              //}
//            } catch (e: Exception) {
//              logError(e)
//            }
//          }
//        }
      }
    }
  }

  @SuppressLint("SetTextI18n")
  private fun handleMotionEvent(view: View?, event: MotionEvent?): Boolean {
    if (event == null || view == null) return false
    val currentTouch = Utils.Vector2(event.x, event.y)
    val startTouch = currentTouchStart

    playerBinding?.apply {
      playerIntroPlay.isGone = true

      when (event.action) {
        MotionEvent.ACTION_DOWN -> {
          // validates if the touch is inside of the player area
          isCurrentTouchValid = isValidTouch(currentTouch.x, currentTouch.y)
          /*if (isCurrentTouchValid && player_episode_list?.isVisible == true) {
              player_episode_list?.isVisible = false
          } else*/ if (isCurrentTouchValid) {
            currentTouchStartTime = System.currentTimeMillis()
            currentTouchStart = currentTouch
            currentTouchLast = currentTouch
            currentTouchStartPlayerTime = player?.timePos?.toLong()

            getBrightness()?.let {
              currentRequestedBrightness = it
            }
            (activity?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.let { audioManager ->
              val currentVolume =
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
              val maxVolume =
                audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

              currentRequestedVolume = currentVolume.toFloat() / maxVolume.toFloat()
            }
          }
        }

        MotionEvent.ACTION_UP -> {
          if (isCurrentTouchValid && !isLocked) {
            // seek time
            if (swipeHorizontalEnabled && currentTouchAction == TouchAction.Time) {
              val startTime = currentTouchStartPlayerTime
              if (startTime != null) {
                calculateNewTime(
                  startTime,
                  startTouch,
                  currentTouch
                )?.let { seekTo ->
                  if (abs(seekTo - startTime) > MINIMUM_SEEK_TIME) {
                    player?.timePos = (seekTo.toDouble())
                  }
                }
              }
            }
          }
          //  see if click is eligible for seek 10s
          val holdTime = currentTouchStartTime?.minus(System.currentTimeMillis())
          if (isCurrentTouchValid // is valid
            && currentTouchAction == null // no other action like swiping is taking place
            && currentLastTouchAction == null // last action was none, this prevents mis input random seek
            && holdTime != null
            && holdTime < DOUBLE_TAB_MAXIMUM_HOLD_TIME // it is a click not a long hold
          ) {
            if (!isLocked
              && (System.currentTimeMillis() - currentLastTouchEndTime) < DOUBLE_TAB_MINIMUM_TIME_BETWEEN // the time since the last action is short
            ) {
              currentClickCount++

              if (currentClickCount >= 1) { // have double clicked
                currentDoubleTapIndex++
                if (doubleTapPauseEnabled) { // you can pause if your tap is in the middle of the screen
                  when {
                    currentTouch.x < screenWidth / 2 - (DOUBLE_TAB_PAUSE_PERCENTAGE * screenWidth) -> {
                      if (doubleTapEnabled)
                        doubleTapRewind()
                    }

                    currentTouch.x > screenWidth / 2 + (DOUBLE_TAB_PAUSE_PERCENTAGE * screenWidth) -> {
                      if (doubleTapEnabled)
                        doubleTapForawd()
                    }

                    else -> {
                      togglePlayPause()
                    }
                  }
                } else if (doubleTapEnabled) {
                  if (currentTouch.x < screenWidth / 2) {
                    doubleTapRewind()
                  } else {
                    doubleTapForawd()
                  }
                }
              }
            } else {
              // is a valid click but not fast enough for seek
              currentClickCount = 0
//              autoHide()
              toggleShowDelayed()
            }
          } else {
            currentClickCount = 0
          }

          // reset variables
          isCurrentTouchValid = false
          currentTouchStart = null
          currentLastTouchAction = currentTouchAction
          currentTouchAction = null
          currentTouchStartPlayerTime = null
          currentTouchLast = null
          currentTouchStartTime = null

          // resets UI
          playerTimeText.isVisible = false
          playerProgressbarLeftHolder.isVisible = false
          playerProgressbarRightHolder.isVisible = false

          currentLastTouchEndTime = System.currentTimeMillis()
        }

        MotionEvent.ACTION_MOVE -> {
          // if current touch is valid
          if (startTouch != null && isCurrentTouchValid && !isLocked) {
            // action is unassigned and can therefore be assigned
            if (currentTouchAction == null) {
              val diffFromStart = startTouch - currentTouch

              if (swipeVerticalEnabled) {
                if (abs(diffFromStart.y * 100 / screenHeight) > MINIMUM_VERTICAL_SWIPE) {
                  // left = Brightness, right = Volume, but the UI is reversed to show the UI better
                  currentTouchAction = if (startTouch.x < screenWidth / 2) {
                    // hide the UI if you hold brightness to show screen better, better UX
                    if (isShowing) {
                      isShowing = false
                      animateLayoutChanges()
                    }
                    TouchAction.Brightness
                  } else {
                    TouchAction.Volume
                  }
                }
              }
              if (swipeHorizontalEnabled) {
                if (abs(diffFromStart.x * 100 / screenHeight) > MINIMUM_HORIZONTAL_SWIPE) {
                  currentTouchAction =
                    TouchAction.Time
                }
              }
            }

            // display action
            val lastTouch = currentTouchLast
            if (lastTouch != null) {
              val diffFromLast = lastTouch - currentTouch
              val verticalAddition =
                diffFromLast.y * VERTICAL_MULTIPLIER / screenHeight.toFloat()

              // update UI
              playerTimeText.isVisible = false
              playerProgressbarLeftHolder.isVisible = false
              playerProgressbarRightHolder.isVisible = false

              when (currentTouchAction) {
                TouchAction.Time -> {
                  // this simply updates UI as the seek logic happens on release
                  // startTime is rounded to make the UI sync in a nice way
                  val startTime =
                    currentTouchStartPlayerTime
                  if (startTime != null) {
                    calculateNewTime(
                      startTime,
                      startTouch,
                      currentTouch
                    )?.let { newMs ->
                      val skipMs = newMs - startTime
                      playerTimeText.apply {
                        text =
                          "${convertTimeToString(newMs)} [${
                            (if (abs(skipMs) < 0) "" else (if (skipMs > 0) "+" else "-"))
                          }${convertTimeToString(abs(skipMs))}]"
                        isVisible = true
                      }
                    }
                  }
                }

                TouchAction.Brightness -> {
                  playerProgressbarRightHolder.isVisible = true
                  val lastRequested = currentRequestedBrightness
                  currentRequestedBrightness =
                    min(
                      1.0f,
                      max(currentRequestedBrightness + verticalAddition, 0.0f)
                    )

                  // this is to not spam request it, just in case it fucks over someone
                  if (lastRequested != currentRequestedBrightness)
                    setBrightness(currentRequestedBrightness)

                  // max is set high to make it smooth
                  playerProgressbarRight.max = 100_000
                  playerProgressbarRight.progress =
                    max(2_000, (currentRequestedBrightness * 100_000f).toInt())

                  playerProgressbarRightIcon.setImageResource(
                    brightnessIcons[min( // clamp the value just in case
                      brightnessIcons.size - 1,
                      max(
                        0,
                        round(currentRequestedBrightness * (brightnessIcons.size - 1)).toInt()
                      )
                    )]
                  )
                }

                TouchAction.Volume -> {
                  (activity?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.let { audioManager ->
                    playerProgressbarLeftHolder.isVisible = true
                    val maxVolume =
                      audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val currentVolume =
                      audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

                    // clamps volume and adds swipe
                    currentRequestedVolume =
                      min(
                        1.0f,
                        max(currentRequestedVolume + verticalAddition, 0.0f)
                      )

                    // max is set high to make it smooth
                    playerProgressbarLeft.max = 100_000
                    playerProgressbarLeft.progress =
                      max(2_000, (currentRequestedVolume * 100_000f).toInt())

                    playerProgressbarLeftIcon.setImageResource(
                      volumeIcons[min( // clamp the value just in case
                        volumeIcons.size - 1,
                        max(
                          0,
                          round(currentRequestedVolume * (volumeIcons.size - 1)).toInt()
                        )
                      )]
                    )

                    // this is used instead of set volume because old devices does not support it
                    val desiredVolume =
                      round(currentRequestedVolume * maxVolume).toInt()
                    if (desiredVolume != currentVolume) {
                      val newVolumeAdjusted =
                        if (desiredVolume < currentVolume) AudioManager.ADJUST_LOWER else AudioManager.ADJUST_RAISE

                      audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        newVolumeAdjusted,
                        0
                      )
                    }
                  }
                }

                else -> Unit
              }
            }
          }
        }
      }
    }
    currentTouchLast = currentTouch
    return true
  }

  private var currentDoubleTapIndex = 0
  private fun toggleShowDelayed() {
    if (doubleTapEnabled || doubleTapPauseEnabled) {
      val index = currentDoubleTapIndex
      playerBinding?.playerHolder?.postDelayed({
        if (index == currentDoubleTapIndex) {
          autoHide()
          toggleControls()
        }
      }, DOUBLE_TAB_MINIMUM_TIME_BETWEEN)
    } else {
      autoHide()
      toggleControls()
    }
  }

  private fun doubleTapRewind() {
    try {
      playerBinding?.apply {
        val width = resources.displayMetrics.widthPixels
        ytOverlay.onDoubleTapProgressUp(width, width / 2.0f - 20.0f, player?.height!! / 2.0f)
      }
      player?.timePos = player?.timePos?.plus(-fastForwardTime / 1000)

    } catch (e: Exception) {
      logError(e)
    }
  }

  private fun doubleTapForawd() {
    try {
      playerBinding?.apply {
        val width = resources.displayMetrics.widthPixels
        val height = resources.displayMetrics.heightPixels
        ytOverlay.onDoubleTapProgressUp(width, width / 2.0f + 20.0f, height / 2.0f)
      }
      player?.timePos = player?.timePos?.plus(fastForwardTime / 1000)
    } catch (e: Exception) {
      logError(e)
    }
  }

  private fun calculateNewTime(
    startTime: Long?,
    touchStart: Utils.Vector2?,
    touchEnd: Utils.Vector2?
  ): Long? {
    if (touchStart == null || touchEnd == null || startTime == null) return null
    val diffX = (touchEnd.x - touchStart.x) * HORIZONTAL_MULTIPLIER / screenWidth.toFloat()
    val duration = psc.durationSec.toLong() ?: return null
    return max(
      min(
        startTime + ((duration * (diffX * diffX)) * (if (diffX < 0) -1 else 1)).toLong(),
        duration
      ), 0
    )
  }

  private fun getBrightness(): Float? {
    return if (useTrueSystemBrightness) {
      try {
        Settings.System.getInt(
          context?.contentResolver,
          Settings.System.SCREEN_BRIGHTNESS
        ) / 255f
      } catch (e: Exception) {
        // because true system brightness requires
        // permission, this is a lazy way to check
        // as it will throw an error if we do not have it
        useTrueSystemBrightness = false
        return getBrightness()
      }
    } else {
      try {
        activity?.window?.attributes?.screenBrightness
      } catch (e: Exception) {
        logError(e)
        null
      }
    }
  }

  private fun setBrightness(brightness: Float) {
    if (useTrueSystemBrightness) {
      try {
        Settings.System.putInt(
          context?.contentResolver,
          Settings.System.SCREEN_BRIGHTNESS_MODE,
          Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )

        Settings.System.putInt(
          context?.contentResolver,
          Settings.System.SCREEN_BRIGHTNESS, (brightness * 255).toInt()
        )
      } catch (e: Exception) {
        useTrueSystemBrightness = false
        setBrightness(brightness)
      }
    } else {
      try {
        val lp = activity?.window?.attributes
        lp?.screenBrightness = brightness
        activity?.window?.attributes = lp
      } catch (e: Exception) {
        logError(e)
      }
    }
  }

  private fun isValidTouch(rawX: Float, rawY: Float): Boolean {
    val statusHeight = statusBarHeight ?: 0
    // val navHeight = navigationBarHeight ?: 0
    // nav height is removed because screenWidth already takes into account that
    return rawY > statusHeight && rawX < screenWidth //- navHeight
  }

  private fun handleKeyEvent(event: KeyEvent, hasNavigated: Boolean): Boolean {
    if (hasNavigated) {
    } else {
      autoHide()
      event.keyCode.let { keyCode ->
        when (event.action) {
          KeyEvent.ACTION_DOWN -> {
            when (keyCode) {
              KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (!isShowing) {
                  if (!isLocked) {
                    togglePlayPause()
                  }
                  toggleControls()
                  return true
                }
              }

              KeyEvent.KEYCODE_DPAD_DOWN,
              KeyEvent.KEYCODE_DPAD_UP -> {
                if (!isShowing) {
                  toggleControls()
                  return true
                }
              }

              KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!isShowing && !isLocked) {
                  player?.timePos =
                    player?.timePos?.plus(-androidTVInterfaceOffSeekTime / 1000L)
                  return true
                } else if (playerBinding?.playerPausePlay?.isFocused == true) {
                  player?.timePos =
                    player?.timePos?.plus(-androidTVInterfaceOnSeekTime / 1000L)
                  return true
                }
              }

              KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!isShowing && !isLocked) {
                  player?.timePos =
                    player?.timePos?.plus(androidTVInterfaceOffSeekTime / 1000L)
                  return true
                } else if (playerBinding?.playerPausePlay?.isFocused == true) {
                  player?.timePos =
                    player?.timePos?.plus(androidTVInterfaceOnSeekTime / 1000L)
                  return true
                }
              }
            }
          }
        }

        when (keyCode) {
          // don't allow dpad move when hidden

          KeyEvent.KEYCODE_DPAD_DOWN,
          KeyEvent.KEYCODE_DPAD_UP,
          KeyEvent.KEYCODE_DPAD_DOWN_LEFT,
          KeyEvent.KEYCODE_DPAD_DOWN_RIGHT,
          KeyEvent.KEYCODE_DPAD_UP_LEFT,
          KeyEvent.KEYCODE_DPAD_UP_RIGHT -> {
            if (!isShowing) {
              return true
            } else {
            }
          }

          // netflix capture back and hide ~monke
          KeyEvent.KEYCODE_BACK -> {
            if (isShowing) {
              toggleControls()
              return true
            } else {
              player?.timePos
                ?.let {
                  CommonActivitty.activityResultEvent?.invoke(
                    PlayBackResult(
                      RESULT_OK,
                      it.toLong() * 1000L,
                      "cancel",
                      if(isSameEpisode) null else allLinks.indexOf(currentSelectedLink) + 1
                    )
                  )
                }
            }
          }

          else -> return false
        }
      }
    }

    return false
  }

  private fun showCodecsDialog() {
    val codecTexts = mutableListOf("HW (mediacodec-copy)", "SW")
    val codecValues = mutableListOf("mediacodec-copy", "no")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      codecTexts.add(0, "HW+ (mediacodec)")
      codecValues.add(0, "mediacodec")
    }


    val hwdecActive = player?.hwdecActive
    val selectedIndex = codecValues.indexOfFirst { it == hwdecActive }

    activity?.let { act ->
      act.showDialog(
        codecTexts,
        selectedIndex,
        act.getString(R.string.player_decoders),
        false,
        {
          activity?.hideSystemUI()
        }) { index ->
        MPVLib.setPropertyString("hwdec", codecValues[index])
      }
    }
  }

  private fun updateDecoderButton() {
    if (playerBinding?.playerCodecBtt?.isVisible == true) {
      playerBinding?.playerCodecBtt?.text = when (player?.hwdecActive) {
        "mediacodec" -> "HW+"
        "no" -> "SW"
        else -> "HW"
      }
    }
  }


  var selectVideoDialog: Dialog? = null
  private fun showVideoTracks() {
    try {
      //println("CURRENT SELECTED :$currentSelectedSubtitles of $currentSubs")
      context?.let { ctx ->
        val tracks = player?.tracks ?: return

        player?.paused = true

        val currentVideoTracks = tracks["video"]
        var videoIndex = max((currentVideoTracks?.indexOfFirst { it.selected } ?: 0), 0)


        val binding: PlayerSelectVideoTracksBinding =
          PlayerSelectVideoTracksBinding.inflate(LayoutInflater.from(ctx), null, false)
        val trackDialog = Dialog(ctx, R.style.AlertDialogCustom)
        trackDialog.setContentView(binding.root)
        trackDialog.show()
        selectVideoDialog = trackDialog

        fun dismiss() {
          player?.paused = false
          activity?.hideSystemUI()
        }

        currentVideoTracks?.let {
          val videosList = binding.videoTracksList
          binding.videoTracksHolder.isVisible = currentVideoTracks.isNotEmpty()
          val videosArrayAdapter =
            ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)
          videosArrayAdapter.addAll(currentVideoTracks.mapIndexed { index, format ->
            format.name
          })

          videosList.choiceMode = AbsListView.CHOICE_MODE_SINGLE
          videosList.adapter = videosArrayAdapter

          // Sometimes the data is not the same because some data gets resolved at different stages i think
          videosList.setSelection(videoIndex)
          videosList.setItemChecked(videoIndex, true)

          videosList.setOnItemClickListener { _, _, which, _ ->
            videoIndex = which
            videosList.setItemChecked(which, true)
          }
        }


        trackDialog.setOnDismissListener {
          dismiss()
        }

        binding.cancelBtt.setOnClickListener {
          trackDialog.dismissSafe(activity)
        }
        binding.applyBtt.setOnClickListener {
          player?.vid = videoIndex
          trackDialog.dismissSafe(activity)
        }
      }
    } catch (e: Exception) {
      logError(e)
    }
  }


  var selectTrackDialog: Dialog? = null
  private fun showTracksDialogue() {
    try {
      //println("CURRENT SELECTED :$currentSelectedSubtitles of $currentSubs")
      context?.let { ctx ->
        val tracks = player?.tracks ?: return

        player?.paused = true

        val currentAudioTracks = tracks["audio"]
        var audioIndexStart =
          max((currentAudioTracks?.indexOfFirst { it.selected } ?: 0), 0)

        val currentSubtitleTracks = tracks["sub"]
        var subtitleIndex =
          max((currentSubtitleTracks?.indexOfFirst { it.selected } ?: 0), 0)

        val binding: PlayerSelectTracksBinding =
          PlayerSelectTracksBinding.inflate(LayoutInflater.from(ctx), null, false)
        val trackDialog = Dialog(ctx, R.style.AlertDialogCustom)
        trackDialog.setContentView(binding.root)
        trackDialog.show()
        selectTrackDialog = trackDialog

        fun dismiss() {
          player?.paused = false
          activity?.hideSystemUI()
        }

        trackDialog.setOnDismissListener {
          dismiss()
        }

        currentAudioTracks?.let {
          val audioList = binding.autoTracksList
          binding.audioTracksHolder.isVisible = currentAudioTracks.isEmpty() == false

          val audioArrayAdapter =
            ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)
//                audioArrayAdapter.add(ctx.getString(R.string.no_subtitles))
          audioArrayAdapter.addAll(currentAudioTracks.mapIndexed { index, format ->
            format.name//fromTwoLettersToLanguage(format.name)

          })

          audioList.adapter = audioArrayAdapter
          audioList.choiceMode = AbsListView.CHOICE_MODE_SINGLE

          audioList.setSelection(audioIndexStart)
          audioList.setItemChecked(audioIndexStart, true)

          audioList.setOnItemClickListener { _, _, which, _ ->
            audioIndexStart = which
            audioList.setItemChecked(which, true)
          }
        }


        currentSubtitleTracks?.let {
          val subtitleList = binding.sortSubtitles
          val loadFromFileFooter: TextView =
            layoutInflater.inflate(
              R.layout.sort_bottom_footer_add_choice,
              null
            ) as TextView

          loadFromFileFooter.text = ctx.getString(R.string.player_load_subtitles)
          loadFromFileFooter.setOnClickListener {
            openSubPicker()
          }
          subtitleList.addFooterView(loadFromFileFooter)

          val subsArrayAdapter =
            ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)
          subsArrayAdapter.addAll(currentSubtitleTracks.map { it.name })

          subtitleList.adapter = subsArrayAdapter
          subtitleList.choiceMode = AbsListView.CHOICE_MODE_SINGLE

          subtitleList.setSelection(subtitleIndex)
          subtitleList.setItemChecked(subtitleIndex, true)

          subtitleList.setOnItemClickListener { _, _, which, _ ->
            if (which > currentSubtitleTracks.size) {
              // Since android TV is funky the setOnItemClickListener will be triggered
              // instead of setOnClickListener when selecting. To override this we programmatically
              // click the view when selecting an item outside the list.

              // Cheeky way of getting the view at that position to click it
              // to avoid keeping track of the various footers.
              // getChildAt() gives null :(
              val child = subtitleList.adapter.getView(which, null, subtitleList)
              child?.performClick()
            } else {
              subtitleIndex = which
              subtitleList.setItemChecked(which, true)
            }
          }
        }

        binding.subtitlesEncodingFormat.apply {
          val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)

          val prefNames = ctx.resources.getStringArray(R.array.subtitles_encoding_list)
          val prefValues = ctx.resources.getStringArray(R.array.subtitles_encoding_values)

          val value = settingsManager.getString(
            ctx.getString(R.string.subtitles_encoding_key), null
          )
          val index = prefValues.indexOf(value)
          text = prefNames[if (index == -1) 0 else index]
        }
//        binding.subtitlesClickSettings.setOnClickListener {
//          val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)
//
//          val prefNames = ctx.resources.getStringArray(R.array.subtitles_encoding_list)
//          val prefValues = ctx.resources.getStringArray(R.array.subtitles_encoding_values)
//
//          val currentPrefMedia = settingsManager.getString(
//            ctx.getString(R.string.subtitles_encoding_key), null
//          )
//          val index = prefValues.indexOf(currentPrefMedia)
//          activity?.showDialog(prefNames.toList(),
//            if (index == -1) 0 else index,
//            ctx.getString(R.string.subtitles_encoding),
//            true,
//            {}) {
//            settingsManager.edit().putString(
//              ctx.getString(R.string.subtitles_encoding_key), prefValues[it]
//            ).apply()
//
//            //updateForcedEncoding(ctx)
//            dismiss()
//            //player.seekTime(-1) // to update subtitles, a dirty trick
//          }
//        }

        binding.cancelBtt.setOnClickListener {
          trackDialog.dismissSafe(activity)
        }

        binding.applyBtt.setOnClickListener {
          player?.aid = audioIndexStart
          player?.sid = subtitleIndex

          trackDialog.dismissSafe(activity)
        }
      }
    } catch (e: Exception) {
      logError(e)
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
    val speedIndex = speedsNumbers.indexOf(player?.playbackSpeed?.toFloat() ?: 1.0f)

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

  private fun loadPlayList() {
    if (decodeMode == DecodeMode.swDec) {
      pushOption("hwdec", "no")
    }
//    pushOption(
//      "force-media-title",
//      currentSelectedLink?.first?.name ?: currentSelectedLink?.first?.url!!
//    )

    pushOption(
      "start",
      "${if (psc.position > 0) psc.positionSec else (currentSelectedLink?.first?.position ?: 0L) / 1000}"
    )

    player?.playPlayList(
      allLinks.map { it.first?.url ?: "" },
      allLinks.first().first?.headers
    )
    playerBinding?.playerBuffering?.isVisible = true

    try {
      uiReset()
    } catch (e: Exception) {
      logError(e)
    }
  }

  private fun loadLink(
    link: Pair<ExtractorLink?, ExtractorUri?>,
    sub: SubtitleData? = null,
    nextEpisode: Boolean = false
  ) {
    currentSelectedLink = link

    // Force software decoding on emulator or if already in swDec mode
    if (decodeMode == DecodeMode.swDec || context?.isTvOrEmulator() == true) {
      pushOption("hwdec", "no")
      // Additional options to ensure software decoding on emulator
      pushOption("vd-lavc-software-fallback", "yes")
      pushOption("ad-lavc-downmix", "yes")
      // Force software video decoder to avoid goldfish decoder issues
      pushOption("vd", "lavc:h264")
      // Disable hardware accelerated codecs that might trigger goldfish
      pushOption("hwdec-codecs", "")
    }
    pushOption(
      "force-media-title",
      currentSelectedLink?.first?.name ?: currentSelectedLink?.first?.url!!
    )

    if (nextEpisode) {
      pushOption(
        "start",
        "0"
      )
    } else {
      pushOption(
        "start",
        "${if (psc.position > 0) psc.positionSec else (currentSelectedLink?.first?.position ?: 0L) / 1000}"
      )
    }

    val uri = Uri.parse(currentSelectedLink?.first?.url)

    player?.playFile(
      resolveUri(uri) ?: "",
      currentSelectedLink?.first?.headers
    )

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
    player?.paused = player?.paused?.not();
    if (player?.paused == false)
      autoHide()
    else
      currentTapIndex++;
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
      player?.timePos = player?.timePos?.plus(-fastForwardTime / 1000)
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
      player?.timePos = player?.timePos?.plus(fastForwardTime / 1000)
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
    updateDecoderButton()
    resetRewindText()
    updateMetadataDisplay()
    player?.loadTracks()

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

  private var currentTapIndex = 0
  protected fun autoHide() {
    currentTapIndex++
    val index = currentTapIndex
    playerBinding?.playerHolder?.postDelayed({
      if (!isCurrentTouchValid && isShowing && activityIsForeground && index == currentTapIndex) {
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

    player?.playbackSpeed = speed.toDouble()
  }

  override fun onResume() {
    enterFullscreen()
    if (activityIsForeground) {
      super.onResume()
      return
    }

    player?.paused = false
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

    mediaSession?.let {
      it.isActive = false
      it.release()
    }
    mediaSession = null

    audioFocusRequest?.let {
      AudioManagerCompat.abandonAudioFocusRequest(audioManager!!, it)
    }
    audioFocusRequest = null

    player?.removeObserver(this)
    player?.destroy()

    playerEventListener = null
    keyEventListener = null

    super.onDestroyView()
  }


  private fun toggleControls() {
    isShowing = !isShowing
    animateLayoutChanges()
    playerBinding?.playerPausePlay?.requestFocus()
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
      playerCenterMenu.isGone = isGone
      playerLock.isGone = !isShowing
      playerGoBackHolder.isGone = isGone
      playerSourcesBtt.isGone = isGone
      moreOptions.isGone = true
      playerSubttileOffset.isGone = true
      playerSkipEpisode.isGone = isSameEpisode
      playerBinding?.playerSkipEpisode?.isGone = isSameEpisode || (allLinks.indexOf(currentSelectedLink) >= allLinks.size - 1)
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
    // Use WindowCompat to allow drawing edge-to-edge (including display cutout)
    try {
      activity?.let { WindowCompat.setDecorFitsSystemWindows(it.window, false) }
    } catch (e: Exception) {
      logError(e)
    }
  }

  protected fun exitFullscreen() {
    //if (lockRotation)
    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER

    // simply resets brightness and notch settings that might have been overridden
    val lp = activity?.window?.attributes
    lp?.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    try {
      // Restore decor fitting to default so system bars are not overlaid
      activity?.let { WindowCompat.setDecorFitsSystemWindows(it.window, true) }
    } catch (e: Exception) {
      logError(e)
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
        when (player?.getRepeat()) {
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
    if (eventId == MPVLib.mpvEventId.MPV_EVENT_SHUTDOWN) {
      Log.v(TAG, "MPV_EVENT_SHUTDOWN")
    }

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
      "track-list" -> {
        player?.loadTracks()
      }

      "video-format" -> {
        //updateAudioUI()
      }

      "hwdec-current" -> {
        updateDecoderButton()
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

      "end_file_reason" -> {
        finishWithResult(
          if (playbackHasStarted) RESULT_OK else RESULT_CANCELED,
          true,
          value.toInt()
        )
      }
    }
  }

  private fun updatePlaybackPos(position: Int) {
    playerBinding?.exoPosition?.text = MPVUtils.prettyTime(position.toLong())
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

  private var firstTime = false;
  private fun eventPropertyUi(property: String, value: Double) {
    if (!activityIsForeground) return
    when (property) {
      "duration/full" -> {
        playerBinding?.exoDuration?.text = MPVUtils.prettyTime(psc.durationSec)
        playerBinding?.exoProgress?.setDuration(psc.durationSec.toLong())
      }

      "video-params/aspect", "video-params/rotate" -> {
        updateOrientation()
        //updatePiPParams()
      }
    }
  }

  private fun updateOrientation() {

    if (!requireActivity().packageManager.hasSystemFeature(PackageManager.FEATURE_SCREEN_PORTRAIT))
      return
    val ratio = player?.getVideoAspect()?.toFloat() ?: 0f
    if (ratio == 0f || ratio in (1f / ASPECT_RATIO_MIN)..ASPECT_RATIO_MIN) {
      // video is square, let Android do what it wants
      activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
      return
    }
    activity?.requestedOrientation = if (ratio > 1f)
      ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    else
      ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT

  }

  private fun toggleRotate() {
    toggleOrientationWithSensor()
  }

  private fun toggleOrientationWithSensor() {
    val currentOrientation = resources.configuration.orientation
    var orientation = 0
    when (currentOrientation) {
      Configuration.ORIENTATION_LANDSCAPE ->
        orientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT

      Configuration.ORIENTATION_PORTRAIT ->
        orientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }
    activity?.requestedOrientation = orientation
  }

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
    playerBinding?.playerVideoTitleRez?.text = psc.meta.formatTitle()
    //playerBinding?.playerVideoTitleRez?.text = psc.meta.formatArtistAlbum()
    val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireActivity())
    val limitTitle =
      settingsManager.getInt(requireActivity().getString(R.string.prefer_limit_title_key), 0)
    currentSelectedLink?.first?.url?.let { playerVideoTitle ->
      //Hide title, if set in setting
      if (limitTitle <= 0) {
        playerBinding?.playerVideoTitle?.text = playerVideoTitle
      } else {
        //Truncate video title if it exceeds limit
        val differenceInLength = playerVideoTitle.length - limitTitle
        val margin = 3 //If the difference is smaller than or equal to this value, ignore it
        if (limitTitle > 0 && differenceInLength > margin) {
          playerBinding?.playerVideoTitle?.text =
            playerVideoTitle.substring(0, limitTitle - 1) + "..."
        }
      }
    }
  }

  private fun playNext() {

    val sourceIndex = allLinks.indexOf(currentSelectedLink)
    CommonActivitty.activityResultEvent?.invoke(
      PlayBackResult(
        RESULT_OK,
        psc.positionSec,
        resources.getString(R.string.end_of_file),
        sourceIndex + 1
      )
    )

    if (sourceIndex >= allLinks.size - 1) {
      playerBinding?.playerSkipEpisode?.isGone = true
    } else {
      try {
        MPVLib.command(arrayOf("stop"))
        val link = allLinks.elementAt(sourceIndex + 1)
        loadLink(link, currentSelectedSubtitles, true)
        showToast(resources.getString(R.string.next_episode))
      } catch (e: Exception) {
        logError(e)
        showToast(resources.getString(R.string.next_episode))
      }
    }

  }

  private fun finishWithResult(code: Int, includeTimePos: Boolean = false, endFileReason: Int) {
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

    when (endFileReason) {
      MPVLib.mpvEndFileReason.MPV_END_FILE_REASON_EOF -> {
        if (!isSameEpisode) {
          playNext()
        } else {
          player?.timePos
            ?.let {
              CommonActivitty.activityResultEvent?.invoke(
                PlayBackResult(
                  RESULT_OK,
                  it.toLong() * 1000L,
                  "finish"
                )
              )
            }
          activity?.finish()
        }
      }


      MPVLib.mpvEndFileReason.MPV_END_FILE_REASON_ERROR -> {
        // Try to get a detailed error message from mpv
        val mpvError = try {
          MPVLib.getPropertyString("playback-abort-reason")
            ?: MPVLib.getPropertyString("property-text")
            ?: MPVLib.getPropertyString("event-log-message")
        } catch (e: Exception) {
          null
        }
        val errorMsg = mpvError?.takeIf { it.isNotBlank() } ?: resources.getString(R.string.source_error)
        // Fallback: if hardware decoder error and not yet retried, try software decoding
        if (!triedSwDecFallback && errorMsg.contains("mediacodec", true)) {
          triedSwDecFallback = true
          decodeMode = DecodeMode.swDec
          showToast(getString(R.string.fallback_to_software_decoding))
          // Reload the file with software decoding
          currentSelectedLink?.let { loadLink(it) }
        } else {
          CommonActivitty.activityResultEvent?.invoke(
            PlayBackResult(
              code,
              psc.positionSec,
              errorMsg,
              if(isSameEpisode) null else allLinks.indexOf(currentSelectedLink) + 1
            )
          )
          showToast(errorMsg)
          Log.e(TAG, "mpv playback error: $errorMsg")
        }
      }

      MPVLib.mpvEndFileReason.MPV_END_FILE_REASON_QUIT,
      MPVLib.mpvEndFileReason.MPV_END_FILE_REASON_STOP -> {
//        if (isSameEpisode) {
//          activity?.finish()
//        } else {
//          playlistNext()
//        }
      }

      MPVLib.mpvEndFileReason.MPV_END_FILE_REASON_REDIRECT -> {

      }
    }
    //
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
    if (player?.aid == -1)
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
      player?.paused = true;
    }

    super.onPause()
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
        player?.paused = true
      }

      override fun onPlay() {
        player?.paused = false
      }

      override fun onSeekTo(pos: Long) {
        player?.timePos = (pos / 1000.0)
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
        player?.changeShuffle(false, shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL)
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
        val wasPlayerPaused = player?.paused ?: false
        player?.paused = true
        audioFocusRestore = {
          oldRestore()
          if (!wasPlayerPaused) player?.paused = false
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
        player?.paused = true
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
          player?.paused = false
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

  @OptIn(UnstableApi::class)
  private fun openSubPicker() {
    try {
      subsPathPicker.launch(
        arrayOf(
          "text/plain",
          "text/str",
          "application/octet-stream",
          MimeTypes.TEXT_UNKNOWN,
          MimeTypes.TEXT_VTT,
          MimeTypes.TEXT_SSA,
          MimeTypes.APPLICATION_TTML,
          MimeTypes.APPLICATION_MP4VTT,
          MimeTypes.APPLICATION_SUBRIP,
        )
      )
    } catch (e: Exception) {
      logError(e)
    }
  }

  private val subsPathPicker =
    registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      normalSafeApiCall {
        // It lies, it can be null if file manager quits.
        if (uri == null) return@normalSafeApiCall
        val ctx = context ?: Utils.activity ?: return@normalSafeApiCall
        // RW perms for the path
        ctx.contentResolver.takePersistableUriPermission(
          uri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )

        val file = File(requireNotNull(uri.path))// SafeFile.fromUri(ctx, uri)
        val fileName = file.name
        println("Loaded subtitle file. Selected URI path: $uri - Name: $fileName")
        // DO NOT REMOVE THE FILE EXTENSION FROM NAME, IT'S NEEDED FOR MIME TYPES
        val name = fileName ?: uri.toString()

        val subtitleData = SubtitleData(
          name,
          uri.toString(),
          SubtitleOrigin.DOWNLOADED_FILE,
          name.toSubtitleMimeType(),
          emptyMap(),
          null
        )

        addAndSelectSubtitles(subtitleData)
      }
    }

  private fun addAndSelectSubtitles(
    vararg subtitleData: SubtitleData
  ) {
    if (subtitleData.isEmpty()) return
    val selectedSubtitle = subtitleData.first()
    val ctx = context ?: return

    val subs = currentSubs + subtitleData

    // this is used instead of observe(viewModel._currentSubs), because observe is too slow
//    player.setActiveSubtitles(subs)
//
//    // Save current time as to not reset player to 00:00
//    player.saveData()
//    player.reloadPlayer(ctx)
//
//    setSubtitles(selectedSubtitle)

    viewModel.addSubtitles(subtitleData.toSet())

    selectTrackDialog?.dismissSafe(activity)

    showToast(
      String.format(ctx.getString(R.string.player_loaded_subtitles), selectedSubtitle.name),
      Toast.LENGTH_LONG
    )
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
