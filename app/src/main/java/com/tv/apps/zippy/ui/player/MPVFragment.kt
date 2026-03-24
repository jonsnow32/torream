package com.tv.apps.zippy.ui.player

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity.RESULT_CANCELED
import androidx.appcompat.app.AppCompatActivity.RESULT_OK
import androidx.core.view.WindowCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.preference.PreferenceManager
import com.tv.apps.zippy.MainActivityViewModel.Companion.applyContentRect
import com.tv.apps.zippy.MainActivityViewModel.Companion.applyNotch
import com.tv.apps.zippy.R
import com.tv.apps.zippy.databinding.PlayerCustomLayoutBinding
import com.tv.apps.zippy.databinding.PlayerCustomLayoutTvBinding
import com.tv.apps.zippy.media.dao.MediaPlaybackDao
import com.tv.apps.zippy.media.repository.MediaPlaybackRepository
import com.tv.apps.zippy.model.SaveCaptionStyle
import com.tv.apps.zippy.model.SubtitleData
import com.tv.apps.zippy.model.SubtitleOrigin
import com.tv.apps.zippy.model.VideoLink
import com.tv.apps.zippy.ui.player.mpv.MPVLib
import com.tv.apps.zippy.ui.player.mpv.MPVUtils
import com.tv.apps.zippy.ui.player.mpv.MPVView
import com.tv.apps.zippy.ui.player.mpv.PipActionManager
import com.tv.apps.zippy.ui.player.mpv.PlayerAudioManager
import com.tv.apps.zippy.ui.player.mpv.PlayerDialogManager
import com.tv.apps.zippy.ui.player.mpv.PlayerGestureHandler
import com.tv.apps.zippy.ui.player.mpv.PlayerMediaManager
import com.tv.apps.zippy.ui.player.mpv.PlayerUIController
import com.tv.apps.zippy.ui.player.youtube.YouTubeOverlay
import com.tv.apps.zippy.ui.subtitles.MPVSubtitleFragment
import com.tv.apps.zippy.utils.CommonActivitty
import com.tv.apps.zippy.utils.CommonActivitty.keyEventListener
import com.tv.apps.zippy.utils.CommonActivitty.playerEventListener
import com.tv.apps.zippy.utils.CommonActivitty.screenHeight
import com.tv.apps.zippy.utils.CommonActivitty.screenWidth
import com.tv.apps.zippy.utils.DataStore
import com.tv.apps.zippy.utils.DataStore.getKey
import com.tv.apps.zippy.utils.UIHelper.dismissSafe
import com.tv.apps.zippy.utils.UIHelper.getNavigationBarHeight
import com.tv.apps.zippy.utils.UIHelper.getStatusBarHeight
import com.tv.apps.zippy.utils.UIHelper.popCurrentPage
import com.tv.apps.zippy.utils.UIHelper.showSystemUI
import com.tv.apps.zippy.utils.Utils
import com.tv.apps.zippy.utils.Utils.logError
import com.tv.apps.zippy.utils.Utils.normalSafeApiCall
import com.tv.apps.zippy.utils.Utils.showToast
import com.tv.apps.zippy.utils.Utils.toSubtitleMimeType
import com.tv.apps.zippy.utils.hideSystemUI
import com.tv.apps.zippy.utils.isTvOrEmulator
import com.tv.apps.zippy.utils.observe
import com.github.rubensousa.previewseekbar.PreviewBar
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import kotlin.math.max


enum class PlayerResize(@StringRes val nameRes: Int) {
  Fit(R.string.resize_fit),
  Fill(R.string.resize_fill),
  Zoom(R.string.resize_zoom),
}


enum class DecodeMode {
  hwDec,
  swDec
}


/**
 * Playlist state management for MPV player
 * Manages playlist playback including repeat modes and navigation
 */
data class PlaylistState(
  val items: List<MPVView.PlaylistItem>,
  val currentIndex: Int = 0,
  val repeatMode: RepeatMode = RepeatMode.NONE,
  val shuffleEnabled: Boolean = false
) {
  enum class RepeatMode {
    NONE,     // Play once and stop
    ALL,      // Loop entire playlist
    ONE       // Loop current item
  }

  /**
   * Get next index based on current state and repeat mode
   * @return next index or null if playlist should end
   */
  fun getNextIndex(): Int? = when {
    repeatMode == RepeatMode.ONE -> currentIndex
    currentIndex < items.size - 1 -> currentIndex + 1
    repeatMode == RepeatMode.ALL -> 0
    else -> null // End of playlist
  }

  /**
   * Get previous index based on current state and repeat mode
   * @return previous index or null if at start with no repeat
   */
  fun getPreviousIndex(): Int? = when {
    repeatMode == RepeatMode.ONE -> currentIndex
    currentIndex > 0 -> currentIndex - 1
    repeatMode == RepeatMode.ALL -> items.size - 1
    else -> null
  }

  /**
   * Check if we can navigate to next item
   */
  fun hasNext(): Boolean = getNextIndex() != null

  /**
   * Check if we can navigate to previous item
   */
  fun hasPrevious(): Boolean = getPreviousIndex() != null
}

@AndroidEntryPoint
class MPVFragment : Fragment(), MPVLib.EventObserver {
  @Inject
  lateinit var mediaPlaybackDao: MediaPlaybackRepository

  @Inject
  lateinit var sharedPreferences: SharedPreferences

  private var player: MPVView? = null
  private val viewModel by viewModels<PlayerViewModel>()
  private val eventUiHandler = Handler(Looper.getMainLooper())

  private var playerBinding: PlayerCustomLayoutBinding? = null

  // Manager components for cleaner architecture
  private lateinit var gestureHandler: PlayerGestureHandler
  private lateinit var uiController: PlayerUIController
  private lateinit var playerAudioManager: PlayerAudioManager
  private lateinit var mediaManager: PlayerMediaManager
  private lateinit var dialogManager: PlayerDialogManager
  private var pipActionManager: PipActionManager? = null

  // state of player UI
  private var resizeMode: Int = 0
  private var backgroundPlayMode = ""

  //settings
  private var fastForwardTime = 10000L
  private var androidTVInterfaceOffSeekTime = 10000L
  private var androidTVInterfaceOnSeekTime = 30000L
  private var activityIsForeground = true
  private var sWidth = screenWidth
  private var sHeight = screenHeight

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

  private val psc = MPVUtils.PlaybackStateCache()
  private var ignoreAudioFocus = false

  private var shouldAutoPlay = true
  private var playbackHasStarted = false
  private var allLinks: List<VideoLink> = emptyList()
  private var currentSubs: Set<SubtitleData> = mutableSetOf()
  private var currentSelectedLink: VideoLink? = null
  private var currentSelectedSubtitles: SubtitleData? = null

  private var isSameEpisode: Boolean = false;
  private var triedSwDecFallback = false

  private var playlistState: PlaylistState? = null
  private val seekActionTime = 30000L
  private var decodeMode = DecodeMode.hwDec;

  private var playbackInitialized = false

  // Video resolution tracking
  private var videoWidth: Int = 0
  private var videoHeight: Int = 0

  /**
   * Check if ready to initialize playback and determine mode (single vs playlist)
   * Called when allLinks or isSameEpisode changes
   */
  private fun checkAndInitializePlayback() {
    // Only initialize once
    if (playbackInitialized) return

    // Need both values to be set
    if (allLinks.isEmpty()) return

    // Now determine playback mode
    if (isSameEpisode || allLinks.size == 1) {
      // Single video playback mode
      Timber.tag(TAG).d("Initializing SINGLE video playback")
      val startIndex = viewModel.currentLinkIndex.value ?: 0
      normalSafeApiCall {
        loadLink(allLinks.getOrNull(startIndex))
      }
    } else {
      // Playlist mode - multiple different videos
      Timber.tag(TAG).d("Initializing PLAYLIST mode with ${allLinks.size} videos")
      val startIndex = viewModel.currentLinkIndex.value ?: 0
      loadPlaylist(allLinks, startIndex)
    }

    playbackInitialized = true
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    val view = inflater.inflate(R.layout.fragment_mvp_player, container, false)
    val controllerHolder = view.findViewById<FrameLayout>(R.id.controller_holder)
    player = view.findViewById(R.id.mvpPlayer)
    val childView = inflater.inflate(
       if(context?.isTvOrEmulator() == true) R.layout.player_custom_layout_tv else R.layout.player_custom_layout,
      controllerHolder,
      false
    )
    controllerHolder.addView(childView)
    playerBinding = PlayerCustomLayoutBinding.bind(childView.findViewById(R.id.player_holder))
    return view;
  }


  @SuppressLint("ClickableViewAccessibility")
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    player?.addObserver(this)
    player?.initialize(requireActivity().filesDir.path, requireActivity().cacheDir.path)

    // Initialize manager components
    initializeManagers()

    observe(viewModel.allLinks) {
      allLinks = it

      // Check if ready to determine playback mode
      checkAndInitializePlayback()
    }
    observe(viewModel.isSameEpisode) {
      isSameEpisode = it

      // Check if ready to determine playback mode
      checkAndInitializePlayback()
    }
    observe(viewModel.currentLinkIndex) {
      // Current link index changed - will be used by checkAndInitializePlayback
    }
    observe(viewModel.currentSubs) { set ->
      for (sub in set) {
        mediaManager.addSubtitle(sub, select = true)
      }
    }

    observe(viewModel.currentSubtitleIndex) { index ->
//      if (index >= 0 && index < currentSubs.size)
//        setSubtitles(currentSubs.elementAt(index))
    }

    // Initialize audio manager and request audio focus
    val audioFocusGranted = playerAudioManager.initialize()
    if (audioFocusGranted) {
      shouldAutoPlay = true // Auto-play when ready
    } else {
      Timber.v("Audio focus not granted")
      if (!ignoreAudioFocus) {
        mediaManager.queueCommand(arrayOf("set", "pause", "yes"))
        shouldAutoPlay = false // Don't auto-play without audio focus
      } else {
        shouldAutoPlay = true // Auto-play even without audio focus if ignoring
      }
    }

    // Initialize media session
    val mediaSession = playerAudioManager.initMediaSession(requireActivity().packageName)
    initMediaSessionCallbacks(mediaSession)
    updateMediaSession()

    activity?.volumeControlStream = STREAM_TYPE

    setPlayBackSpeed(DataStore.playBackSpeed)
    savedInstanceState?.getLong(SUBTITLE_DELAY_BUNDLE_KEY)?.let {
      subtitleDelay = it
    }

    // Restore playback state after configuration changes (e.g., rotation)
    savedInstanceState?.let { bundle ->
      val savedPosition = bundle.getLong(STATE_POSITION, -1L)
      val savedDuration = bundle.getLong(STATE_DURATION, 0L)
      val savedPaused = bundle.getBoolean(STATE_PAUSED, false)
      val savedResizeMode = bundle.getInt(STATE_RESIZE_MODE, 0)
      val savedSubDelay = bundle.getLong(STATE_SUBTITLE_DELAY, 0L)

      if (savedPosition >= 0) {
        // Update playback state cache with saved values
        psc.restorePosition(savedPosition)
        psc.restoreDuration(savedDuration)
        psc.restorePause(savedPaused)
        resizeMode = savedResizeMode

        // Set subtitle delay if saved
        if (savedSubDelay != 0L) {
          subtitleDelay = savedSubDelay
        }

        // Restore pause state after video loads
        if (savedPaused) {
          shouldAutoPlay = false
          mediaManager.queueCommand(arrayOf("set", "pause", "yes"))
        }

        Timber.v("Restored playback state - position: $savedPosition, paused: $savedPaused")
      }
    }

    // handle tv controls
    playerEventListener = { eventType ->
      when (eventType) {
        PlayerEventType.Lock -> {
          uiController.toggleLock()
        }

        PlayerEventType.NextEpisode -> {
          // Check if in playlist mode first
          if (playlistState?.hasNext() == true) {
            playNextInPlaylist()
          } else {
            //todo playNextInAllLink()
          }
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
          // Check if in playlist mode first
          if (playlistState?.hasPrevious() == true) {
            playPreviousInPlaylist()
          } else {
            //todo playPreviousInAllLink()
          }

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
          uiController.hide()
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

      }

      playerBinding?.apply {
        playerBinding?.exoFfwdText?.text =
          getString(R.string.ffw_text_regular_format).format(fastForwardTime / 1000)
        playerBinding?.exoRewText?.text =
          getString(R.string.rew_text_regular_format).format(fastForwardTime / 1000)
        playPauseToggle.isChecked = false
        var resume = false
        exoProgress.addOnScrubListener(object : PreviewBar.OnScrubListener {
          override fun onScrubStart(previewBar: PreviewBar?) {
            exoProgress.isPreviewEnabled = false;
            resume = player?.paused == false
            if (resume) player?.paused = true
            uiController.resetAutoHideTimer()
          }

          override fun onScrubMove(
            previewBar: PreviewBar?,
            progress: Int,
            fromUser: Boolean
          ) {
            playerBinding?.exoPosition?.text =
              MPVUtils.prettyTime(previewBar?.progress?.toLong() ?: 0L)
            val diff = psc.durationSec - (previewBar?.progress?.toLong() ?: 0L)
            playerBinding?.exoDuration?.text = if (diff <= 0)
              "-00:00"
            else
              MPVUtils.prettyTime(-diff, true)
          }

          override fun onScrubStop(previewBar: PreviewBar?) {
            player?.timePos = previewBar?.progress?.toDouble()
            if (resume) player?.paused = false
            uiController.resetAutoHideTimer()
          }
        })

        exoProgress.attachPreviewView(previewFrameLayout)
        exoProgress.setPreviewLoader { currentPosition, max ->
          val bitmap = player?.getPreview(currentPosition.toFloat().div(max.toFloat()))
          previewImageView.isGone = bitmap == null
          previewImageView.setImageBitmap(bitmap)
          uiController.resetAutoHideTimer()
        }
      }
    } catch (e: Exception) {
      logError(e)
    }

    playerBinding?.apply {

      playPauseToggle.setOnClickListener {
        togglePlayPause()
        uiController.resetAutoHideTimer()
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
        uiController.resetAutoHideTimer()
      }

      // init clicks
      playerResizeBtt.setOnClickListener {
        nextResize()
        setReizeIcon()
        uiController.resetAutoHideTimer()
      }

      playerSpeedBtt.setOnClickListener {
        showSpeedDialog()
      }


      playerSkipEpisode.setOnClickListener {
        // Check if in playlist mode first
        if (playlistState?.hasNext() == true) {
          playNextInPlaylist()
        } else {
          //todo playNextInAllLink()
        }
      }

      playerPrevEpisode.setOnClickListener {
        // Check if in playlist mode first
        if (playlistState?.hasPrevious() == true) {
          playPreviousInPlaylist()
        }
        // Note: No app-level "previous episode" logic for non-playlist mode
      }

      playerLock.setOnClickListener {
        uiController.toggleLock()
      }

      exoRew.setOnClickListener {
        rewind()
        uiController.resetAutoHideTimer()
      }

      exoFfwd.setOnClickListener {
        fastForward()
        uiController.resetAutoHideTimer()
      }

      playerGoBack.setOnClickListener {
        player?.timePos
          ?.let {
            CommonActivitty.activityResultEvent?.invoke(
              PlayBackResult(
                RESULT_OK,
                it.toLong() * 1000L,
                "cancel",
                if (isSameEpisode) null else currentSelectedLink?.let { link ->
                  allLinks.indexOf(
                    link
                  ) + 1
                }
              )
            )
          }

        activity?.popCurrentPage()
      }


      playerSourcesBtt.setOnClickListener {
        showSourcesDialog()
      }

      playerAudioTracksBtt.setOnClickListener {
        showAudioTracksDialogue()
      }

      playerSubtitleTracksBtt.setOnClickListener {
        showSubtitleTracksDialogue()
      }

      playerVideoTracks.setOnClickListener {
        showVideoTracks()
      }

      playerCodecBtt.setOnClickListener {
        showCodecsDialog()
      }

      playerOptionPip.setOnClickListener {
        enterPipMode()
      }

      // Show PIP button only on Android N (7.0) and above
      playerOptionPip.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

      // it is !not! a bug that you cant touch the right side, it does not register inputs on navbar or status bar
      playerHolder.setOnTouchListener { callView, event ->
        uiController.hideIntroPlay()
        return@setOnTouchListener gestureHandler.handleMotionEvent(
          callView,
          event,
          player?.timePos?.toLong(),
          psc.durationSec.toLong(),
          ::isValidTouch
        )
      }

      playerMediaRouteButton.apply {
        val chromecastEnabled = sharedPreferences.getBoolean(
          getString(R.string.enable_chromecast_key), true
        )
        val fcastEnabled = sharedPreferences.getBoolean(
          getString(R.string.enable_fcast_key), false
        )
        val castSupported = chromecastEnabled || fcastEnabled

        alpha = if (castSupported) 1f else 0.3f
        isEnabled = castSupported

        if (!castSupported) {
          setOnClickListener {
            showToast(
              R.string.no_chromecast_support_toast,
              Toast.LENGTH_LONG
            )
          }
        }
      }
    }
  }


  private fun initializeManagers() {
    playerBinding?.let { binding ->
      // Initialize gesture handler with all required callbacks

      // Initialize UI controller
      uiController = PlayerUIController(binding, sharedPreferences)

      // Initialize audio manager
      playerAudioManager = PlayerAudioManager(
        context = requireContext(),
        onAudioFocusLost = {
          val wasPlayerPaused = player?.paused ?: false
          player?.paused = true
        },
        onAudioFocusGained = {
          // Will be called by audioFocusRestore callback
        },
        onDuckAudio = { ducking ->
          MPVLib.command(arrayOf("multiply", "volume", ducking.toString()))
        },
        onRestoreAudio = { multiplier ->
          MPVLib.command(arrayOf("multiply", "volume", multiplier.toString()))
        },
        getIgnoreAudioFocus = { ignoreAudioFocus }
      )

      // Initialize media manager
      mediaManager = PlayerMediaManager(
        context = requireContext(),
        mediaPlaybackRepository = mediaPlaybackDao,
        onCommandQueued = { cmd ->
          Timber.v("Queued command: ${cmd.joinToString(" ")}")
        }
      )
      mediaManager.setPlayer(player)

      // Initialize dialog manager
      dialogManager = PlayerDialogManager(this, onDismissDialog = {
        enterFullscreen()
      })

      // Initialize PIP action manager (Android O+)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        pipActionManager = PipActionManager(
          context = requireContext(),
          onPlayPause = {
            togglePlayPause()
          },
          onPrevious = {
            if (playlistState?.hasPrevious() == true) {
              playPreviousInPlaylist()
            }
          },
          onNext = {
            if (playlistState?.hasNext() == true) {
              playNextInPlaylist()
            }
            // Note: Non-playlist "next episode" not supported in PIP
          },
          onRewind = {
            rewind()
          },
          onFastForward = {
            fastForward()
          }
        )
        pipActionManager?.register()
        Timber.tag(TAG).d("PIP action manager initialized and registered")
      }

      gestureHandler = PlayerGestureHandler(
        context = requireContext(),
        sWidth = sWidth,
        sHeight = sHeight,
        isLocked = { uiController.isLocked },
        isShowing = { uiController.isShowing },
        onBrightnessUpdate = { brightness, showUI ->
          if (showUI) {
            updateBrightnessOverlay(brightness)
          } else {
            hideBrightnessOverlay()
          }
        },
        onVolumeUpdate = { volumeRatio, showUI ->
          if (showUI) {
            updateVolumeOverlay(volumeRatio)
          } else {
            hideVolumeOverlay()
          }
        },
        onSeekUpdate = { position, text, showUI ->
          if (showUI) {
            updateSeekOverlay(position, text)
          } else {
            hideSeekOverlay()
          }
        },
        onSeekCommit = { seekTo ->
          player?.timePos = seekTo.toDouble()
        },
        onDoubleTapRewind = {
          doubleTapRewind()
        },
        onDoubleTapForward = {
          doubleTapForawd()
        },
        onSingleTap = {
          uiController.toggleControls()
        },
        onTogglePlayPause = {
          togglePlayPause()
        },
        hideUIForBrightness = {
          uiController.hide()
        },
        onTouchWhenLocked = {
          uiController.toggleControlsWhenLocked()
        }
      )
    }
  }

  /**
   * Load playlist from MPV after video is loaded
   * Called when MPV has loaded files and playlist info is available
   */
  private fun loadPlaylistFromMPV() {
    player?.let { mpv ->
      try {
        val items = mpv.loadPlaylist()
        if (items.size > 1) {
          val currentPos = MPVLib.getPropertyInt("playlist-pos") ?: 0
          playlistState = PlaylistState(
            items = items,
            currentIndex = currentPos,
            repeatMode = playlistState?.repeatMode ?: PlaylistState.RepeatMode.NONE
          )
          updatePlaylistUI()
          Timber.tag(TAG)
            .d("Loaded playlist from MPV: ${items.size} items, current index: $currentPos")
        }
      } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Failed to load playlist from MPV")
      }
    }
  }

  /**
   * Navigate to next item in playlist
   */
  private fun playNextInPlaylist() {
    val state = playlistState ?: return
    val nextIndex = state.getNextIndex()

    if (nextIndex != null) {
      playlistState = state.copy(currentIndex = nextIndex)

      // Use MPV playlist command to navigate
      MPVLib.command(arrayOf("playlist-play-index", nextIndex.toString()))

      updatePlaylistUI()

      // Update PIP actions with new playlist position
      updatePipActions()

      // Show toast feedback
      val nextItem = state.items.getOrNull(nextIndex)
      val itemTitle = nextItem?.title ?: "Item ${nextIndex + 1}"
      showToast("Playing: $itemTitle (${nextIndex + 1}/${state.items.size})", Toast.LENGTH_SHORT)

      Timber.tag(TAG).d("Playing next: index $nextIndex")
    } else {
      Timber.tag(TAG).d("End of playlist reached")
      finishPlaylist()
    }
  }

  /**
   * Navigate to previous item in playlist
   */
  private fun playPreviousInPlaylist() {
    val state = playlistState ?: return
    val prevIndex = state.getPreviousIndex()

    if (prevIndex != null) {
      playlistState = state.copy(currentIndex = prevIndex)

      // Use MPV playlist command to navigate
      MPVLib.command(arrayOf("playlist-play-index", prevIndex.toString()))

      updatePlaylistUI()

      // Update PIP actions with new playlist position
      updatePipActions()

      // Show toast feedback
      val prevItem = state.items.getOrNull(prevIndex)
      val itemTitle = prevItem?.title ?: "Item ${prevIndex + 1}"
      showToast("Playing: $itemTitle (${prevIndex + 1}/${state.items.size})", Toast.LENGTH_SHORT)

      Timber.tag(TAG).d("Playing previous: index $prevIndex")
    }
  }

  private fun updatePlaylistUI() {
    val state = playlistState ?: return

    // Ensure UI updates run on main thread
    eventUiHandler.post {
      playerBinding?.apply {
        // Update video title to include playlist position
        val currentItem = state.items.getOrNull(state.currentIndex)
        val titleText = if (currentItem?.title != null) {
          "${currentItem.title} (${state.currentIndex + 1}/${state.items.size})"
        } else {
          "Playing ${state.currentIndex + 1} of ${state.items.size}"
        }
        uiController.setTitle(titleText)

        // Update navigation buttons visibility based on playlist position
        playerSkipEpisode.isVisible = state.hasNext()
        playerPrevEpisode.isVisible = state.hasPrevious()
      }

      Timber.tag(TAG).d("Updated playlist UI: ${state.currentIndex + 1}/${state.items.size}")
    }
  }

  /**
   * Update repeat mode icon
   */
  private fun updateRepeatIcon() {
    val state = playlistState ?: return

    val modeText = when (state.repeatMode) {
      PlaylistState.RepeatMode.NONE -> "Repeat: Off"
      PlaylistState.RepeatMode.ALL -> "Repeat: All"
      PlaylistState.RepeatMode.ONE -> "Repeat: One"
    }

    Timber.tag(TAG).d("Repeat mode: ${state.repeatMode}")
    // TODO: Add repeat button to layout and update icon
    // playerBinding?.btnRepeat?.setImageResource(when (state.repeatMode) {
    //   PlaylistState.RepeatMode.NONE -> R.drawable.ic_repeat_off
    //   PlaylistState.RepeatMode.ALL -> R.drawable.ic_repeat_all
    //   PlaylistState.RepeatMode.ONE -> R.drawable.ic_repeat_one
    // })
  }

  /**
   * Handle playlist end - finish activity or fragment
   */
  private fun finishPlaylist() {
    Timber.tag(TAG).d("Playlist finished")
    playlistState = null
    CommonActivitty.activityResultEvent?.invoke(
      PlayBackResult(
        RESULT_OK,
        player?.timePos?.toLong()?.times(1000L) ?: 0L,
        "playlist_end"
      )
    )
    activity?.popCurrentPage()
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


  private fun isValidTouch(rawX: Float, rawY: Float): Boolean {
    val statusHeight = statusBarHeight ?: 0
    // val navHeight = navigationBarHeight ?: 0
    // nav height is removed because sWidth already takes into account that
    return rawY > statusHeight && rawX < sWidth //- navHeight
  }

  private fun handleKeyEvent(event: KeyEvent, hasNavigated: Boolean): Boolean {
    if (hasNavigated) {
    } else {
      event.keyCode.let { keyCode ->
        when (event.action) {
          KeyEvent.ACTION_DOWN -> {
            uiController.resetAutoHideTimer()
            when (keyCode) {
              KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (!uiController.isShowing) {
                  if (!uiController.isLocked) {
                    togglePlayPause()
                  }
                  uiController.toggleControls()

                  return true
                }
              }
              KeyEvent.KEYCODE_DPAD_DOWN,
              KeyEvent.KEYCODE_DPAD_UP -> {
                if (!uiController.isShowing) {
                  uiController.toggleControls()
                  if(context?.isTvOrEmulator() == false)
                    playerBinding?.playPauseToggle?.requestFocus()
                  else
                    playerBinding?.playerGoBack?.requestFocus()
                  return true
                }
              }

              KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!uiController.isShowing && !uiController.isLocked) {
                  player?.timePos =
                    player?.timePos?.plus(-androidTVInterfaceOffSeekTime / 1000L)
                  return true
                } else if (playerBinding?.playPauseToggle?.isFocused == true) {
                  player?.timePos =
                    player?.timePos?.plus(-androidTVInterfaceOnSeekTime / 1000L)
                  return true
                }
              }

              KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!uiController.isShowing && !uiController.isLocked) {
                  player?.timePos =
                    player?.timePos?.plus(androidTVInterfaceOffSeekTime / 1000L)
                  return true
                } else if (playerBinding?.playPauseToggle?.isFocused == true) {
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
            if (!uiController.isShowing) {
              return true
            } else {
            }
          }

          // netflix capture back and hide ~monke
          KeyEvent.KEYCODE_BACK -> {
            if (uiController.isShowing) {
              uiController.toggleControls()
              return true
            } else {
              player?.timePos
                ?.let {
                  CommonActivitty.activityResultEvent?.invoke(
                    PlayBackResult(
                      RESULT_OK,
                      it.toLong() * 1000L,
                      "cancel",
                      if (isSameEpisode) null else currentSelectedLink?.let { link ->
                        allLinks.indexOf(
                          link
                        ) + 1
                      }
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
    val hwdecActive = player?.hwdecActive ?: "auto"
    dialogManager.showCodecsDialog(hwdecActive) { codec ->
      MPVLib.setPropertyString("hwdec", codec)
      activity?.hideSystemUI()
    }
  }

  private fun updateDecoderButton(hwdecValue: String? = null) {
    if (playerBinding?.playerCodecBtt?.isVisible != true) return
    val hwdec = hwdecValue ?: try {
      player?.hwdecActive
    } catch (e: Exception) {
      null
    }

    playerBinding?.codecText?.text = when (hwdec) {
      "mediacodec" -> "HW+"
      "no" -> "SW"
      else -> "HW"
    }
  }


  private fun updateBrightnessOverlay(brightness: Float) {
    playerBinding?.apply {
      playerProgressbarRightHolder.isVisible = true
      playerProgressbarRight.max = 100_000
      playerProgressbarRight.progress = max(2_000, (brightness * 100_000f).toInt())
      playerProgressbarRightIcon.setImageResource(gestureHandler.getBrightnessIcon(brightness))
    }
  }

  private fun hideBrightnessOverlay() {
    playerBinding?.playerProgressbarRightHolder?.isVisible = false
  }

  private fun updateVolumeOverlay(volumeRatio: Float) {
    Timber.i("updateVolumeOverlay: $volumeRatio")
    playerBinding?.apply {
      playerProgressbarLeftHolder.isVisible = true
      playerProgressbarLeft.max = 100_000
      playerProgressbarLeft.progress = max(2_000, (volumeRatio * 100_000f).toInt())
      playerProgressbarLeftIcon.setImageResource(gestureHandler.getVolumeIcon(volumeRatio))
    }
  }

  private fun hideVolumeOverlay() {
    playerBinding?.playerProgressbarLeftHolder?.isVisible = false
  }

  private fun updateSeekOverlay(position: Long, text: String) {
    playerBinding?.apply {
      playerTimeText.text = text
      playerTimeText.isVisible = true
    }
  }

  private fun hideSeekOverlay() {
    playerBinding?.playerTimeText?.isVisible = false
  }


  private fun showVideoTracks() {
    try {
      val tracks = player?.tracks ?: return
      player?.paused = true

      dialogManager.showVideoTracksDialog(
        tracks = tracks,
        onTrackSelected = { videoIndex ->
          player?.vid = videoIndex
        },
        onDismiss = {
          player?.paused = false
          activity?.hideSystemUI()
        }
      )
    } catch (e: Exception) {
      logError(e)
    }
  }

  private fun showAudioTracksDialogue() {
    try {
      val tracks = player?.tracks ?: return
      player?.paused = true

      dialogManager.showAudioTracksDialog(
        tracks = tracks,
        onAudioSelected = { audioIndex ->
          player?.aid = audioIndex
          // Reload tracks to update the selected state
          player?.loadTracks()
        },
        onDismiss = {
          player?.paused = false
          activity?.hideSystemUI()
        }
      )
    } catch (e: Exception) {
      logError(e)
    }
  }

  private fun showSubtitleTracksDialogue() {
    try {
      val tracks = player?.tracks ?: return
      player?.paused = true

      dialogManager.showSubtitleTracksDialog(
        tracks = tracks,
        onSubtitleSelected = { subtitleIndex ->
          player?.sid = subtitleIndex
          // Reload tracks to update the selected state
          player?.loadTracks()
        },
        onLoadSubtitlesFromFile = {
          player?.paused = true
          openSubPicker()
        },
        onLoadSubtitlesOnline = {
          showToast("Not implemented yet", Toast.LENGTH_SHORT)
        },
        onDismiss = {
          //player?.paused = false
          activity?.hideSystemUI()
        }
      )
    } catch (e: Exception) {
      logError(e)
    }
  }


  private fun showSubtitleOffsetDialog() {
    dialogManager.showSubtitleOffsetDialog(
      currentOffset = subtitleDelay,
      onOffsetChanged = { time ->
        subtitleDelay = time
      },
      onReset = {
        subtitleDelay = 0
      },
      onCancel = { beforeOffset ->
        subtitleDelay = beforeOffset
      },
      onDismiss = {
        activity?.hideSystemUI()
      }
    )
  }

  private fun showSpeedDialog() {
    val currentSpeed = player?.playbackSpeed?.toFloat() ?: 1.0f
    dialogManager.showSpeedDialog(currentSpeed) { speed ->
      setPlayBackSpeed(speed)
      activity?.hideSystemUI()
    }
  }

  /**
   * Enter Picture-in-Picture mode
   */
  private fun enterPipMode() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      try {
        val activity = requireActivity()

        // Check if PIP is supported
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          if (activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            // Get current playback state
            val isPlaying = player?.paused == false
            val hasPlaylist = playlistState != null
            val hasPrevious = playlistState?.hasPrevious() ?: false
            val hasNext = playlistState?.hasNext() ?: false

            // Create PIP actions
            val pipActions = pipActionManager?.createPipActions(
              isPlaying = isPlaying,
              hasPlaylist = hasPlaylist,
              hasPrevious = hasPrevious,
              hasNext = hasNext
            ) ?: emptyList()

            // Build PIP params with actions
            val params = android.app.PictureInPictureParams.Builder()
              .apply {
                if (pipActions.isNotEmpty()) {
                  setActions(pipActions)
                }

                // Set aspect ratio with clamping to Android's acceptable range
                try {
                  val width = MPVLib.getPropertyInt("dwidth") ?: 16
                  val height = MPVLib.getPropertyInt("dheight") ?: 9

                  if (width > 0 && height > 0) {
                    // Calculate aspect ratio
                    var aspectRatio = width.toFloat() / height.toFloat()

                    // Android PIP aspect ratio limits: min ~0.4184 (5:12), max ~2.39 (12:5)
                    // Clamp to slightly safer range to avoid edge cases
                    val minAspectRatio = 0.42f  // ~5:12 (portrait limit)
                    val maxAspectRatio = 2.39f  // ~12:5 (landscape limit)

                    val originalRatio = aspectRatio
                    aspectRatio = aspectRatio.coerceIn(minAspectRatio, maxAspectRatio)

                    if (originalRatio != aspectRatio) {
                      Timber.tag(TAG).w(
                        "Video aspect ratio $originalRatio is outside PIP limits, " +
                          "clamped to $aspectRatio (min: $minAspectRatio, max: $maxAspectRatio)"
                      )
                    }

                    // Convert to rational and set
                    // Use scaled integers to maintain precision
                    val scale = 1000
                    val rationalWidth = (aspectRatio * scale).toInt()
                    val rationalHeight = scale

                    setAspectRatio(android.util.Rational(rationalWidth, rationalHeight))
                    Timber.tag(TAG)
                      .d("Set PIP aspect ratio: $aspectRatio ($rationalWidth:$rationalHeight)")
                  } else {
                    // Default to 16:9 if dimensions invalid
                    setAspectRatio(android.util.Rational(16, 9))
                    Timber.tag(TAG).d("Using default 16:9 aspect ratio")
                  }
                } catch (e: Exception) {
                  // Fallback to safe 16:9 aspect ratio
                  Timber.tag(TAG).e(e, "Failed to set aspect ratio, using default 16:9")
                  try {
                    setAspectRatio(android.util.Rational(16, 9))
                  } catch (e2: Exception) {
                    Timber.tag(TAG).e(e2, "Failed to set default aspect ratio")
                  }
                }
              }
              .build()

            @Suppress("DEPRECATION")
            activity.enterPictureInPictureMode(params)
            Timber.tag(TAG).d("Entered PIP mode with ${pipActions.size} actions")
          } else {
            showToast("Picture-in-Picture not supported on this device", Toast.LENGTH_SHORT)
          }
        } else {
          // Android N - simple PIP mode without actions
          @Suppress("DEPRECATION")
          activity.enterPictureInPictureMode()
          Timber.tag(TAG).d("Entered PIP mode (Android N - no actions)")
        }
      } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Failed to enter PIP mode")
        showToast("Failed to enter Picture-in-Picture mode", Toast.LENGTH_SHORT)
        logError(e)
      }
    } else {
      showToast("Picture-in-Picture requires Android 7.0+", Toast.LENGTH_SHORT)
    }
  }

  /**
   * Update PIP actions when playback state changes
   * Call this when play/pause state changes or playlist position changes
   */
  private fun updatePipActions() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      try {
        val activity = requireActivity()

        // Only update if currently in PIP mode
        if (!activity.isInPictureInPictureMode) return

        // Get current playback state
        val isPlaying = player?.paused == false
        val hasPlaylist = playlistState != null
        val hasPrevious = playlistState?.hasPrevious() ?: false
        val hasNext = playlistState?.hasNext() ?: false

        // Create updated PIP actions
        val pipActions = pipActionManager?.createPipActions(
          isPlaying = isPlaying,
          hasPlaylist = hasPlaylist,
          hasPrevious = hasPrevious,
          hasNext = hasNext
        ) ?: emptyList()

        // Update PIP params with new actions
        val params = android.app.PictureInPictureParams.Builder()
          .apply {
            if (pipActions.isNotEmpty()) {
              setActions(pipActions)
            }
          }
          .build()

        activity.setPictureInPictureParams(params)
        Timber.tag(TAG).d("Updated PIP actions (playing: $isPlaying, playlist: $hasPlaylist)")
      } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Failed to update PIP actions")
      }
    }
  }


  /**
   * Load multiple videos as MPV playlist
   * This enables MPV-level playlist mode with automatic navigation
   */
  private fun loadPlaylist(links: List<VideoLink>, startIndex: Int = 0) {
    if (links.isEmpty()) return

    Timber.tag(TAG).d("Loading playlist: ${links.size} videos, starting at index $startIndex")

    // Use mediaManager to load playlist
    mediaManager.loadPlaylist(
      links = links,
      startPosition = if (psc.position > 0) psc.position else (links.getOrNull(startIndex)?.position
        ?: 0L),
      startIndex = startIndex
    )

    playerBinding?.playerBuffering?.isVisible = true
    try {
      uiReset()
    } catch (e: Exception) {
      logError(e)
    }
  }

  private fun loadLink(
    link: VideoLink?,
    sub: SubtitleData? = null,
    nextEpisode: Boolean = false
  ) {
    if (link == null) return

    currentSelectedLink = link

    // Use mediaManager to handle media loading
    val startPosition = if (nextEpisode) {
      0L
    } else {
      if (psc.position > 0) psc.position else (link.position)
    }

    mediaManager.loadFile(
      link = link,
      startPosition = startPosition,
      title = link.name
    )

    playerBinding?.playerBuffering?.isVisible = true
    try {
      uiReset()
    } catch (e: Exception) {
      logError(e)
    }
  }

  fun togglePlayPause() {
    player?.paused = player?.paused?.not()

    // Update PIP actions if in PIP mode
    updatePipActions()
  }

  private fun rewind() {
    try {
      playerBinding?.apply {
        uiController.setCenterMenuVisible(true)
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
              uiController.setCenterMenuVisible(uiController.isShowing)
              playerRewHolder.alpha = if (uiController.isShowing) 1f else 0f
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
        uiController.setCenterMenuVisible(true)
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
              uiController.setCenterMenuVisible(uiController.isShowing)
              playerFfwdHolder.alpha = if (uiController.isShowing) 1f else 0f
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
    uiController.hide()
    // if nothing has loaded these buttons should not be visible
    playerBinding?.apply {
      //shadowOverlay.isVisible = false
      //playerSourcesBtt.isVisible = false
      setReizeIcon()
    }
    //updateLockUI()
    resetFastForwardText()
    updateDecoderButton()
    resetRewindText()
    updateMetadataDisplay()
  }

  private fun loadSavedSubStyle() {
    try {
      context?.let { ctx ->
        val savedStyle: SaveCaptionStyle? = try {
          ctx.getKey("subtitle_settings")
        } catch (_: Exception) {
          null
        }
        if (savedStyle != null) {
          MPVSubtitleFragment.applyToMPV(ctx, savedStyle)
        }
      }
    } catch (_: Throwable) {
    }
  }

  fun nextResize() {
    resizeMode = (resizeMode + 1) % PlayerResize.entries.size
    resize(resizeMode, true)
  }

  fun resize(resize: Int, showToast: Boolean) {
    resize(PlayerResize.entries[resize], showToast)
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


  private fun setPlayBackSpeed(speed: Float) {
    try {
      DataStore.playBackSpeed = speed
      playerBinding?.speedText?.text =
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

    // Set activityIsForeground BEFORE unpausing to ensure correct state
    activityIsForeground = true
    player?.paused = false

    uiReset()
    super.onResume()
  }

  override fun onPause() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      if (requireActivity().isInMultiWindowMode || requireActivity().isInPictureInPictureMode) {
        Timber.v("Going into multi-window mode")
        super.onPause()
        return
      }
    }
    onPauseImpl()
    super.onPause()

  }


  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)

    if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
      sWidth = screenWidth
      sHeight = screenHeight
    } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
      sWidth = screenHeight
      sHeight = screenWidth
    }

    // Update gesture handler with new dimensions
    gestureHandler.updateDimensions(sWidth, sHeight)

    // Hide gesture UI overlays on configuration change
    playerBinding?.apply {
      playerTimeText.isVisible = false
      playerProgressbarLeftHolder.isVisible = false
      playerProgressbarRightHolder.isVisible = false
      applyNotch(pipHide)
    }

    Timber.d("Configuration changed - orientation: ${newConfig.orientation}, dims: ${sWidth}x${sHeight}")
  }

  override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
    super.onPictureInPictureModeChanged(isInPictureInPictureMode)

    if (isInPictureInPictureMode) {
      // Entered PIP mode - use UI controller to hide all UI
      uiController.hide()
      Timber.tag(TAG).d("Entered PIP mode - controls hidden via UI controller")
    } else {
      // Exited PIP mode - use UI controller to restore UI
      uiController.show()
      Timber.tag(TAG).d("Exited PIP mode - controls restored via UI controller")
    }
  }

  override fun onDestroyView() {
    exitFullscreen()

    // Unregister PIP action manager (Android O+ only)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      pipActionManager?.unregister()
      pipActionManager = null
    }

    playerBinding = null
    Timber.v("Exiting.")

    // Suppress any further callbacks
    activityIsForeground = false

    // Release audio manager (handles media session and audio focus)
    playerAudioManager.release()

    // Reset media manager state
    mediaManager.reset()

    player?.removeObserver(this)
    player?.destroy()

    playerEventListener = null
    keyEventListener = null

    super.onDestroyView()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    // Save playback state to survive configuration changes
    outState.putLong(STATE_POSITION, psc.position)
    outState.putLong(STATE_DURATION, psc.duration)
    outState.putBoolean(STATE_PAUSED, player?.paused ?: false)
    outState.putInt(STATE_PLAYLIST_POS, psc.playlistPos)
    outState.putInt(STATE_RESIZE_MODE, resizeMode)
    outState.putLong(STATE_SUBTITLE_DELAY, subtitleDelay)
    Timber.v("Saved playback state - position: ${psc.position}, paused: ${player?.paused}")
  }


  private fun enterFullscreen() {
    activity?.hideSystemUI()
    // Use WindowCompat to allow drawing edge-to-edge (including display cutout)
    try {
      activity?.let { WindowCompat.setDecorFitsSystemWindows(it.window, false) }
    } catch (e: Exception) {
      logError(e)
    }
  }

  private fun exitFullscreen() {
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

  fun pushOption(key: String, value: String) {
    mediaManager.pushOption(key, value)
  }

  override fun eventProperty(property: String) {
    val metaUpdated = psc.update(property)
    if (metaUpdated)
      updateMediaSession()
    if (property == "loop-file" || property == "loop-playlist") {
      playerAudioManager.setRepeatMode(
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
    Timber.v(property)
    if (psc.update(property, value))
      updateMediaSession()

    if (!activityIsForeground) return
    eventUiHandler.post { eventPropertyUi(property, value) }
  }

  override fun eventProperty(property: String, value: Boolean) {
    if (psc.update(property, value))
      updateMediaSession()
    if (property == "shuffle") {
      playerAudioManager.setShuffleMode(
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
      Timber.tag(TAG).v("MPV_EVENT_SHUTDOWN")
    }

    if (eventId == MPVLib.mpvEventId.MPV_EVENT_START_FILE) {
      mediaManager.onPlaybackStarted()
      playbackHasStarted = true
    }

    // Auto-play when file is loaded and ready
    if (eventId == MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED) {
      // IMPORTANT: Only auto-play if activity is in foreground to prevent playing in background
      if (shouldAutoPlay && playbackHasStarted && activityIsForeground) {
        player?.paused = false
        Timber.tag(TAG).v("Auto-playing video on file loaded")
      } else if (!activityIsForeground) {
        Timber.tag(TAG).v("Skipping auto-play on file loaded: activity is in background")
      }

      // Try to load playlist info from MPV if not already in playlist mode
      loadPlaylistFromMPV()
    }

    if (eventId == MPVLib.mpvEventId.MPV_EVENT_PLAYBACK_RESTART) {
      // Post UI update to main thread to avoid CalledFromWrongThreadException
      eventUiHandler.post {
        playerBinding?.playerBuffering?.isVisible = false
      }

      // Auto-play when ready if shouldAutoPlay is true
      // IMPORTANT: Only auto-play if activity is in foreground to prevent playing in background
      if (shouldAutoPlay && playbackHasStarted && activityIsForeground) {
        player?.paused = false
        Timber.tag(TAG).v("Auto-playing video on playback restart")
      } else if (!activityIsForeground) {
        Timber.tag(TAG).v("Skipping auto-play: activity is in background")
      }
    }
  }

  private fun eventPropertyUi(property: String, dummy: Any?, metaUpdated: Boolean) {
    if (!activityIsForeground) return
    when (property) {
      "video-format" -> {
        //updateAudioUI()
      }

      "hwdec-current" -> {
        // property-only notification: refresh UI by querying current value (safe)
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
        uiController.setPlaying(!value)
      }

      "paused-for-cache" -> {
        playerBinding?.playerBuffering?.isVisible = value
      }
    }
  }

  private fun eventPropertyUi(property: String, value: Long) {
    if (!activityIsForeground) return
    when (property) {
      "time-pos" -> updatePlaybackPos(value)
      "playlist-pos" -> {
        val newIndex = value.toInt()
        if (newIndex >= 0 && playlistState?.currentIndex != newIndex) {
          playlistState = playlistState?.copy(currentIndex = newIndex)
          updatePlaylistUI()

          // Update current media URI in mediaManager for state tracking
          // This will also update the original path for thumbnail extraction
          playlistState?.items?.getOrNull(newIndex)?.let { item ->
            mediaManager.updateCurrentMediaUri(item.filename)
          }

          Timber.tag(TAG).d("Playlist position changed to: $newIndex")
        }
      }

      "playlist-count" -> {
        // Playlist count changed - reload if needed
        Timber.tag(TAG).d("Playlist count: $value")
      }

      "track-list/count" -> {
        // Track list count changed - tracks are now available, load them
        if (value > 0) {
          Timber.tag(TAG).d("track-list/count changed to $value - loading tracks")
          player?.loadTracks()

          // CRITICAL FIX: Auto-enable first subtitle track if available
          // This ensures subtitles are visible after being loaded
          try {
            val subTracks = player?.tracks?.get("sub")
            if (subTracks != null && subTracks.size > 1) { // > 1 because index 0 is "no subs"
              // Check if no subtitle is currently selected
              val currentSid = MPVLib.getPropertyInt("sid") ?: -1
              if (currentSid <= 0) {
                // Auto-select first real subtitle track (index 1, since 0 is "off")
                val firstSubTrack = subTracks.getOrNull(1)
                if (firstSubTrack != null && firstSubTrack.mpvId > 0) {
                  player?.sid = firstSubTrack.mpvId
                  Timber.tag(TAG)
                    .v("Auto-enabled subtitle track: ${firstSubTrack.name} (id=${firstSubTrack.mpvId})")
                }
              }
            }
          } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to auto-enable subtitle track")
          }
        }
      }

      "end_file_reason" -> {
        Timber.i("Playback ended, reason: $value")
        uiController.show(false)
        Timber.i("Playback psc.duration: ${psc.durationSec}")
        updatePlaybackPos(psc.durationSec)

        finishWithResult(
          if (playbackHasStarted) RESULT_OK else RESULT_CANCELED,
          true,
          value.toInt()
        )
      }

      "video-params/w" -> {
        videoWidth = value.toInt()
        updateVideoResolution()
      }

      "video-params/h" -> {
        videoHeight = value.toInt()
        updateVideoResolution()
      }
    }
  }

  private fun updatePlaybackPos(position: Long) {
    playerBinding?.exoPosition?.text = MPVUtils.prettyTime(position.toLong())
    val diff = psc.durationSec - position
    playerBinding?.exoDuration?.text = if (diff <= 0)
      "-00:00"
    else
      MPVUtils.prettyTime(-diff, true)
    playerBinding?.exoProgress?.setPosition((position).toLong())

    // Save position periodically (every update, which is ~1 second)
    // MediaManager will handle debouncing and background thread
    mediaManager.updatePosition(position * 1000L)

    // Note: do NOT add other update functions here just because this is called every second.
    // Use property observation instead.
    //updateStats()
  }

  /**
   * Update video resolution display
   */
  private fun updateVideoResolution() {
    if (videoWidth > 0 && videoHeight > 0) {
      val resolutionText = "${videoWidth}x${videoHeight}"
      uiController.setResolution(resolutionText)
      Timber.tag(TAG).d("Video resolution: $resolutionText")
    }
  }

  private fun eventPropertyUi(property: String, value: Double) {
    if (!activityIsForeground) return
    when (property) {
      "duration/full" -> {
        playerBinding?.exoDuration?.text = MPVUtils.prettyTime(psc.durationSec)
        playerBinding?.exoProgress?.setDuration(psc.durationSec.toLong())
      }

      "video-params/aspect" -> {
        //updatePiPParams()
      }

      "video-params/rotate" -> {
        updateOrientation(value)
      }
    }
  }

  private fun eventPropertyUi(property: String, value: String, metaUpdated: Boolean) {
    if (!activityIsForeground) return
    when (property) {
      "speed" -> {
        //updateSpeedButton()
      }

      "hwdec-current" -> {
        // Use the value delivered by the event instead of querying MPV synchronously
        updateDecoderButton(value)
      }
    }
    if (metaUpdated)
      updateMetadataDisplay()
  }

  private fun playlistPrev() = MPVLib.command(arrayOf("playlist-prev"))
  private fun playlistNext() = MPVLib.command(arrayOf("playlist-next"))

  private fun updateOrientation(rotate: Double) {

    activity?.requestedOrientation = if (rotate < 90f)
      ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    else
      ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT

  }

  private fun toggleRotate() {
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


  private fun updateMetadataDisplay() {
    uiController.setTitle(psc.meta.formatTitle())
    val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireActivity())
    val limitTitle =
      settingsManager.getInt(requireActivity().getString(R.string.prefer_limit_title_key), 0)
//    currentSelectedLink?.url?.let { playerVideoTitle ->
//      //Hide title, if set in setting
//      if (limitTitle <= 0) {
//        uiController.setTitle(playerVideoTitle)
//      } else {
//        //Truncate video title if it exceeds limit
//        val differenceInLength = playerVideoTitle.length - limitTitle
//        val margin = 3 //If the difference is smaller than or equal to this value, ignore it
//        if (differenceInLength > margin) {
//          uiController.setTitle(
//            playerVideoTitle.take(limitTitle - 1) + "..."
//          )
//        }
//      }
//    }
  }

//  private fun playNext() {
//    val sourceIndex = currentSelectedLink?.let { allLinks.indexOf(it) } ?: -1
//    CommonActivitty.activityResultEvent?.invoke(
//      PlayBackResult(
//        RESULT_OK,
//        psc.positionSec,
//        resources.getString(R.string.end_of_file),
//        sourceIndex + 1
//      )
//    )
//
//    if (sourceIndex >= allLinks.size - 1) {
//      playerBinding?.playerSkipEpisode?.isVisible = false
//    } else {
//      try {
//        MPVLib.command(arrayOf("stop"))
//        val link = allLinks.getOrNull(sourceIndex + 1)
//        loadLink(link, currentSelectedSubtitles, true)
//        showToast(resources.getString(R.string.next_episode))
//      } catch (e: Exception) {
//        logError(e)
//        showToast(resources.getString(R.string.next_episode))
//      }
//    }
//
//  }

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

    // Detect if launched from external intent (ACTION_VIEW or has data).
    // If launched via intent we should finish the activity and rely on the activity result.
    // If launched from inside the app (feed/app/navigation) we should close the fragment
    // and deliver the playback callback via CommonActivitty.activityResultEvent.
    val launchedFromIntent = try {
      val actIntent = requireActivity().intent
      (actIntent?.action == Intent.ACTION_VIEW) || (actIntent?.data != null)
    } catch (e: Exception) {
      false
    }
    when (endFileReason) {
      MPVLib.mpvEndFileReason.MPV_END_FILE_REASON_EOF -> {
        // Priority 1: Check if in playlist mode and auto-advance
        if (playlistState != null) {
          val state = playlistState!!
          if (state.hasNext()) {
            Timber.tag(TAG).d("EOF in playlist mode - auto-playing next item")
            playNextInPlaylist()
            return // Continue playing, don't finish
          } else {
            // End of playlist
            Timber.tag(TAG).d("EOF - end of playlist reached")
            finishPlaylist()
            return
          }
        }

        // Priority 2: Handle episode navigation (existing logic)
        if (isSameEpisode) {
          //todo playNextInAllLink()
        } else {
          // End of file for a standalone item - mark as finished
          mediaManager.markFinished()

          player?.timePos?.let {
            val playbackResult = PlayBackResult(
              RESULT_OK,
              it.toLong() * 1000L,
              "finish"
            )

            if (launchedFromIntent) {
              // External intent consumer expects activity to finish with result
              activity?.finish()
            } else {
              // In-app consumer expects a callback and fragment close
              CommonActivitty.activityResultEvent?.invoke(playbackResult)
              activity?.popCurrentPage()
            }
          }
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
        val errorMsg =
          mpvError?.takeIf { it.isNotBlank() } ?: resources.getString(R.string.source_error)
        // Fallback: if hardware decoder error and not yet retried, try software decoding
        if (!triedSwDecFallback && errorMsg.contains("mediacodec", true)) {
          triedSwDecFallback = true
          decodeMode = DecodeMode.swDec
          showToast(getString(R.string.fallback_to_software_decoding))
          // Reload the file with software decoding
          currentSelectedLink?.let { loadLink(it) }
        } else {
          val playbackResult = PlayBackResult(
            code,
            psc.positionSec,
            errorMsg,
            if (isSameEpisode) null else currentSelectedLink?.let { allLinks.indexOf(it) + 1 }
          )

          if (launchedFromIntent) {
            // Finish activity; result was already set above
            activity?.finish()
          } else {
            // Invoke in-app callback and close fragment
            CommonActivitty.activityResultEvent?.invoke(playbackResult)
            activity?.popCurrentPage()
          }

          showToast(errorMsg)
          Timber.e("mpv playback error: $errorMsg")
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
      val title = playerBinding?.playerVideoTitle?.text?.toString()
      val durationMs = if (psc.duration > 0) psc.duration else null
      val positionMs = if (psc.position >= 0) psc.position else null
      val isPlaying = !psc.pause

      playerAudioManager.updateMediaSession(
        title = title,
        artist = null,
        durationMs = durationMs,
        positionMs = positionMs,
        isPlaying = isPlaying
      )
    }
  }

  private fun onPauseImpl() {
    val fmt = MPVLib.getPropertyString("video-format")
    val shouldBackground = shouldBackground()

    // media session uses the same thumbnail
    updateMediaSession()

    // Save full playback state with all settings
    try {
      val position = (MPVLib.getPropertyInt("time-pos") ?: 0) * 1000L
      val speed = MPVLib.getPropertyDouble("speed")?.toFloat() ?: 1.0f
      val audioTrack = MPVLib.getPropertyInt("aid") ?: -1
      val subtitleTrack = MPVLib.getPropertyInt("sid") ?: -1

      mediaManager.savePlaybackState(
        position = position,
        speed = speed,
        audioTrackIndex = audioTrack,
        textTrackIndex = subtitleTrack,
        subtitles = null, //todo need to save subtitles here
        isFinished = false
      )
    } catch (e: Exception) {
      Timber.tag(TAG).e(e, "Failed to save playback state on pause")
    }

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
      Timber.d("player indicates EOF, not saving watch-later config")
      return
    }
    MPVLib.command(arrayOf("write-watch-later-config"))
  }

  private fun initMediaSession(): MediaSessionCompat {
    /*
        https://developer.android.com/guide/topics/media-apps/working-with-a-media_session
        https://developer.android.com/guide/topics/media-apps/audio-app/mediasession-callbacks
        https://developer.android.com/reference/android/support/v4/media/session/MediaSessionCompat
     */
    val session = MediaSessionCompat(requireActivity(), TAG)
    initMediaSessionCallbacks(session)
    return session
  }

  private fun initMediaSessionCallbacks(session: MediaSessionCompat) {
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
  }

  fun showSourcesDialog() {
    try {
      player?.paused = true

      dialogManager.showSourcesDialog(
        allLinks = allLinks,
        currentSelectedLink = currentSelectedLink,
        currentSubs = currentSubs,
        onSourceSelected = { link, subtitleData ->
          loadLink(link, subtitleData)
        },
        onDismiss = {
          player?.paused = false
          activity?.hideSystemUI()
        }
      )
    } catch (e: Exception) {
      logError(e)
    }
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

        val file = File(requireNotNull(uri.toString()))// SafeFile.fromUri(ctx, uri)
        val fileName = file.name
        Timber.i("Loaded subtitle file. Selected URI path: $uri - Name: $fileName")
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
        player?.paused = false
      }
    }

  private fun addAndSelectSubtitles(
    vararg subtitleData: SubtitleData
  ) {
    if (subtitleData.isEmpty()) return
    val selectedSubtitle = subtitleData.first()
    val ctx = context ?: return

    // Add subtitles to MPV using mediaManager
    subtitleData.forEachIndexed { index, sub ->
      try {
        // Use "select" flag for first subtitle, "auto" for others
        val select = index == 0
        mediaManager.addSubtitle(sub, select)
        Timber.d("Added subtitle to MPV: ${sub.name}")
      } catch (e: Exception) {
        Timber.e(e, "Failed to add subtitle to MPV: ${sub.name}")
      }
    }

    viewModel.addSubtitles(subtitleData.toSet())

    dialogManager.selectTrackDialog?.dismissSafe(activity)

    showToast(
      String.format(ctx.getString(R.string.player_loaded_subtitles), selectedSubtitle.name),
      Toast.LENGTH_LONG
    )
  }

  // Helper methods for gesture handler callbacks

  companion object {
    private const val TAG = "mpv"

    // State saving keys for configuration changes
    private const val STATE_POSITION = "state_position"
    private const val STATE_DURATION = "state_duration"
    private const val STATE_PAUSED = "state_paused"
    private const val STATE_PLAYLIST_POS = "state_playlist_pos"
    private const val STATE_RESIZE_MODE = "state_resize_mode"
    private const val STATE_SUBTITLE_DELAY = "state_subtitle_delay"

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
