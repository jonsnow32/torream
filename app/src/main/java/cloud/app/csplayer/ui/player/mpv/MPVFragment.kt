package cloud.app.csplayer.ui.player.mpv

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
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity.RESULT_CANCELED
import androidx.appcompat.app.AppCompatActivity.RESULT_OK
import androidx.core.view.WindowCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
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
import cloud.app.csplayer.ui.player.youtube.YouTubeOverlay
import cloud.app.csplayer.utils.CommonActivitty
import cloud.app.csplayer.utils.CommonActivitty.keyEventListener
import cloud.app.csplayer.utils.CommonActivitty.playerEventListener
import cloud.app.csplayer.utils.CommonActivitty.screenHeight
import cloud.app.csplayer.utils.CommonActivitty.screenWidth
import cloud.app.csplayer.utils.DataStore
import cloud.app.csplayer.model.SaveCaptionStyle
import cloud.app.csplayer.ui.subtitles.MPVSubtitleFragment
import cloud.app.csplayer.utils.DataStore.getKey
import cloud.app.csplayer.utils.ExtractorLink
import cloud.app.csplayer.utils.ExtractorUri
import cloud.app.csplayer.utils.UIHelper.dismissSafe
import cloud.app.csplayer.utils.UIHelper.getNavigationBarHeight
import cloud.app.csplayer.utils.UIHelper.getStatusBarHeight
import cloud.app.csplayer.utils.UIHelper.popCurrentPage
import cloud.app.csplayer.utils.UIHelper.showSystemUI
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
import androidx.core.net.toUri
import cloud.app.csplayer.model.SubtitleData
import cloud.app.csplayer.model.SubtitleOrigin
import cloud.app.csplayer.ui.player.SUBTITLE_DELAY_BUNDLE_KEY
import cloud.app.csplayer.utils.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber


const val MINIMUM_SEEK_TIME = 7L         // when swipe seeking
const val MINIMUM_VERTICAL_SWIPE = 2.0f     // in percentage
const val MINIMUM_HORIZONTAL_SWIPE = 2.0f   // in percentage
const val VERTICAL_MULTIPLIER = 2.0f
const val HORIZONTAL_MULTIPLIER = 2.0f
const val DOUBLE_TAB_MAXIMUM_HOLD_TIME = 200L
const val DOUBLE_TAB_MINIMUM_TIME_BETWEEN = 200L    // this also affects the UI show response time
const val DOUBLE_TAB_PAUSE_PERCENTAGE = 0.15        // in both directions


// when the player should switch skip op to next episode
const val SKIP_OP_VIDEO_PERCENTAGE = 50

// when the player should preload the next episode for faster loading
const val PRELOAD_NEXT_EPISODE_PERCENTAGE = 80

// when the player should mark the episode as watched and resume watching the next
const val NEXT_WATCH_EPISODE_PERCENTAGE = 90

// when the player should sync the progress of "watched", TODO MAKE SETTING
const val UPDATE_SYNC_PROGRESS_PERCENTAGE = 80


enum class PlayerResize(@StringRes val nameRes: Int) {
  Fit(R.string.resize_fit),
  Fill(R.string.resize_fill),
  Zoom(R.string.resize_zoom),
}


enum class DecodeMode {
  hwDec,
  swDec
}

enum class TouchAction {
  Brightness,
  Volume,
  Time,
}

/**
 * Playlist state management for MPV player
 * Manages playlist playback including repeat modes and navigation
 */
@OptIn(UnstableApi::class)
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

@OptIn(UnstableApi::class)
class MPVFragment : Fragment(), MPVLib.EventObserver {

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

  // Manager components for cleaner architecture
  private lateinit var gestureHandler: PlayerGestureHandler
  private lateinit var uiController: PlayerUIController
  private lateinit var playerAudioManager: PlayerAudioManager
  private lateinit var mediaManager: PlayerMediaManager
  private lateinit var dialogManager: PlayerDialogManager

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

  // Track if video should auto-play when ready
  private var shouldAutoPlay = true
  private var playbackHasStarted = false
  private var onloadCommands = mutableListOf<Array<String>>()


  private var allLinks: Set<Pair<ExtractorLink?, ExtractorUri?>> = setOf()
  private var currentSubs: Set<SubtitleData> = mutableSetOf()
  private var currentSelectedLink: Pair<ExtractorLink?, ExtractorUri?>? = null
  private var currentSelectedSubtitles: SubtitleData? = null

  // Track if we've already retried with software decoding
  private var triedSwDecFallback = false

  // Playlist management
  private var playlistState: PlaylistState? = null
  private var isPlaylistMode = false

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

  private fun initializeManagers() {
    playerBinding?.let { binding ->
      // Initialize gesture handler
      gestureHandler = PlayerGestureHandler(
        context = requireContext(),
        onBrightnessChange = { brightness, text ->
          updateBrightnessOverlay(brightness, text)
        },
        onVolumeChange = { volume, text ->
          updateVolumeOverlay(volume, text)
        },
        onSeek = { position, text ->
          updateSeekOverlay(position, text)
        }
      )

      // Initialize UI controller
      uiController = PlayerUIController(binding)

      // Initialize audio manager
      playerAudioManager = PlayerAudioManager(
        context = requireContext(),
        onAudioFocusLost = {
          player?.paused = true
        },
        onAudioFocusGained = {
          if (!ignoreAudioFocus) {
            player?.paused = false
          }
        }
      )
      playerAudioManager.initialize()

      // Initialize media manager
      mediaManager = PlayerMediaManager(
        context = requireContext(),
        onCommandQueued = { cmd ->
          Timber.v("Queued command: ${cmd.joinToString(" ")}")
        }
      )
      mediaManager.setPlayer(player)

      // Initialize dialog manager
      dialogManager = PlayerDialogManager(requireContext())
    }
  }

  /**
   * Initialize playlist from fragment arguments if provided
   * Optimized: Load first file immediately, resolve others in background
   */
  private fun initializePlaylistFromArguments() {
    arguments?.let { args ->
      val urls = args.getStringArrayList(ARG_PLAYLIST_URLS)
      val titles = args.getStringArrayList(ARG_PLAYLIST_TITLES)
      val startIndex = args.getInt(ARG_PLAYLIST_START_INDEX, 0)

      if (!urls.isNullOrEmpty()) {
        // Create playlist items with original URIs
        val items = urls.mapIndexed { index, url ->
          MPVView.PlaylistItem(
            index = index,
            filename = url,
            title = titles?.getOrNull(index)?.takeIf { it.isNotEmpty() }
          )
        }

        if (items.size > 1) {
          isPlaylistMode = true
          playlistState = PlaylistState(
            items = items,
            currentIndex = startIndex.coerceIn(0, items.size - 1)
          )

          // Resolve ONLY the first file URI to play immediately
          val firstUrl = try {
            resolveUri(Uri.parse(urls[0])) ?: urls[0]
          } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to resolve first URI: ${urls[0]}")
            urls[0]
          }

          // Set title for first file
          val firstTitle = titles?.getOrNull(0)?.takeIf { it.isNotEmpty() }
          if (firstTitle != null) {
            pushOption("force-media-title", firstTitle)
          }

          // Load first file immediately (this triggers MPV_EVENT_START_FILE)
          player?.playFile(firstUrl, null)

          // Resolve remaining URIs in background and add to playlist
          // This prevents main thread blocking while still ensuring URIs are resolved properly
          lifecycleScope.launch(Dispatchers.IO) {
            val remainingUrls = urls.drop(1)
            val resolvedUrls = remainingUrls.mapIndexed { index, url ->
              try {
                val resolved = resolveUri(Uri.parse(url)) ?: url
                val actualIndex = index + 1
                val title = titles?.getOrNull(actualIndex)?.takeIf { it.isNotEmpty() }
                Pair(resolved, title)
              } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to resolve URI: $url")
                Pair(url, titles?.getOrNull(index + 1))
              }
            }

            // Add resolved files to MPV playlist on main thread
            withContext(Dispatchers.Main) {
              if (MPVState.isInitialized()) {
                resolvedUrls.forEach { (url, title) ->
                  if (title != null) {
                    MPVLib.command(arrayOf("set", "file-local-options/force-media-title", title))
                  }
                  MPVLib.command(arrayOf("loadfile", url, "append"))
                }

                // Set starting position if not the first item
                if (startIndex > 0) {
                  MPVLib.command(arrayOf("playlist-play-index", startIndex.toString()))
                }

                Timber.tag(TAG).d("Added ${resolvedUrls.size} files to playlist")
              } else {
                // MPV not ready yet, add to onloadCommands
                resolvedUrls.forEach { (url, title) ->
                  if (title != null) {
                    onloadCommands.add(
                      arrayOf(
                        "set",
                        "file-local-options/force-media-title",
                        title
                      )
                    )
                  }
                  onloadCommands.add(arrayOf("loadfile", url, "append"))
                }

                if (startIndex > 0) {
                  onloadCommands.add(arrayOf("playlist-play-index", startIndex.toString()))
                }
              }
            }
          }

          // Enable auto-play
          shouldAutoPlay = true

          Timber.tag(TAG)
            .d("Initialized playlist with ${items.size} items (background URI resolution), starting at index $startIndex")

          // Update UI immediately
          updatePlaylistUI()
        } else if (items.size == 1) {
          // Single file - resolve and load normally
          val url = try {
            resolveUri(Uri.parse(urls[0])) ?: urls[0]
          } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to resolve URI: ${urls[0]}")
            urls[0]
          }

          val title = titles?.getOrNull(0)?.takeIf { it.isNotEmpty() }
          if (title != null) {
            pushOption("force-media-title", title)
          }
          player?.playFile(url, null)
          shouldAutoPlay = true
        }
      }
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
          val currentPos = MPVApi.getPropertyInt("playlist-pos") ?: 0
          isPlaylistMode = true
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

      // Show toast feedback
      val prevItem = state.items.getOrNull(prevIndex)
      val itemTitle = prevItem?.title ?: "Item ${prevIndex + 1}"
      showToast("Playing: $itemTitle (${prevIndex + 1}/${state.items.size})", Toast.LENGTH_SHORT)

      Timber.tag(TAG).d("Playing previous: index $prevIndex")
    }
  }

  /**
   * Cycle through repeat modes: NONE -> ALL -> ONE -> NONE
   */
  private fun cycleRepeatMode() {
    val state = playlistState ?: return
    val newMode = when (state.repeatMode) {
      PlaylistState.RepeatMode.NONE -> PlaylistState.RepeatMode.ALL
      PlaylistState.RepeatMode.ALL -> PlaylistState.RepeatMode.ONE
      PlaylistState.RepeatMode.ONE -> PlaylistState.RepeatMode.NONE
    }

    playlistState = state.copy(repeatMode = newMode)
    updateRepeatIcon()

    val modeText = when (newMode) {
      PlaylistState.RepeatMode.NONE -> "Off"
      PlaylistState.RepeatMode.ALL -> "All"
      PlaylistState.RepeatMode.ONE -> "One"
    }
    showToast("Repeat: $modeText", Toast.LENGTH_SHORT)
    Timber.tag(TAG).d("Repeat mode changed to: $newMode")
  }

  /**
   * Update playlist UI elements
   */
  private fun updatePlaylistUI() {
    val state = playlistState ?: return

    playerBinding?.apply {
      // Update video title to include playlist position
      val currentItem = state.items.getOrNull(state.currentIndex)
      val titleText = if (currentItem?.title != null) {
        "${currentItem.title} (${state.currentIndex + 1}/${state.items.size})"
      } else {
        "Playing ${state.currentIndex + 1} of ${state.items.size}"
      }
      playerVideoTitle?.text = titleText

      // Update skip button visibility
      playerSkipEpisode?.isGone = !state.hasNext()

      // Update skip button text to indicate playlist mode
      playerSkipEpisode?.text = "Next (${state.currentIndex + 1}/${state.items.size})"
    }

    Timber.tag(TAG).d("Updated playlist UI: ${state.currentIndex + 1}/${state.items.size}")
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
    isPlaylistMode = false

    // Determine if launched from intent
    val launchedFromIntent = arguments?.getBoolean(ARG_STARTED_FROM_INTENT)
      ?: (requireActivity().intent.action == Intent.ACTION_VIEW
        || requireActivity().intent.data != null)

    if (launchedFromIntent) {
      activity?.finish()
    } else {
      CommonActivitty.activityResultEvent?.invoke(
        PlayBackResult(
          RESULT_OK,
          player?.timePos?.toLong()?.times(1000L) ?: 0L,
          "playlist_end"
        )
      )
      activity?.popCurrentPage()
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    player?.addObserver(this)
    player?.initialize(requireActivity().filesDir.path, requireActivity().cacheDir.path)

    // Initialize manager components
    initializeManagers()

    // Initialize playlist if provided via arguments
    initializePlaylistFromArguments()

    observe(viewModel.allLinks) {
      allLinks = it
      //currentSelectedLink = allLinks.first()
    }
    observe(viewModel.isSameEpisode) {
      isSameEpisode = it;
    }
    observe(viewModel.currentLinkIndex) {
      // Skip loading if in playlist mode (playlist already loaded in initializePlaylistFromArguments)
      if (!isPlaylistMode) {
        normalSafeApiCall {
          loadLink(allLinks.elementAt(it))
        }
      }
    }
    observe(viewModel.currentSubs) { set ->
      for (sub in set) {
        val url = resolveUri(Uri.parse(sub.url)) ?: continue
        val flag = "select"
        Timber.v("Adding subtitles from intent extras: $url")
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
      shouldAutoPlay = true // Auto-play when ready
    } else {
      Timber.v("Audio focus not granted")
      if (!ignoreAudioFocus) {
        onloadCommands.add(arrayOf("set", "pause", "yes"))
        shouldAutoPlay = false // Don't auto-play without audio focus
      } else {
        shouldAutoPlay = true // Auto-play even without audio focus if ignoring
      }
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
          // Check if in playlist mode first
          if (isPlaylistMode && playlistState?.hasNext() == true) {
            playNextInPlaylist()
          } else {
            // Use existing episode navigation
            playNext()
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
          if (isPlaylistMode && playlistState?.hasPrevious() == true) {
            playPreviousInPlaylist()
          }
          // Note: No existing "play previous" logic for episodes
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
            true
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
        // Check if in playlist mode first
        if (isPlaylistMode && playlistState?.hasNext() == true) {
          playNextInPlaylist()
        } else {
          playNext()
        }
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
                if (isSameEpisode) null else allLinks.indexOf(currentSelectedLink) + 1
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
                          "${newMs.formatDuration()} [${
                            (if (abs(skipMs) < 0) "" else (if (skipMs > 0) "+" else "-"))
                          }${(abs(skipMs)).formatDuration()}]"
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
                      if (isSameEpisode) null else allLinks.indexOf(currentSelectedLink) + 1
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

    playerBinding?.playerCodecBtt?.text = when (hwdec) {
      "mediacodec" -> "HW+"
      "no" -> "SW"
      else -> "HW"
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


          val loadFromNetworkFooter: TextView =
            layoutInflater.inflate(
              R.layout.sort_bottom_footer_add_choice,
              null
            ) as TextView

          loadFromNetworkFooter.text = ctx.getString(R.string.player_load_subtitles_online)
          loadFromNetworkFooter.setOnClickListener {
            showToast("Not implemented yet", Toast.LENGTH_SHORT)
          }
          subtitleList.addFooterView(loadFromNetworkFooter)

          val subsArrayAdapter =
            ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)
          subsArrayAdapter.addAll(currentSubtitleTracks.map { it.name })

          subtitleList.adapter = subsArrayAdapter
          subtitleList.choiceMode = AbsListView.CHOICE_MODE_SINGLE

          subtitleList.setSelection(subtitleIndex)
          subtitleList.setItemChecked(subtitleIndex, true)

          subtitleList.setOnItemClickListener { _, _, which, _ ->
            if (which > currentSubtitleTracks.size - 1) {
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

          // Reload tracks to update the selected state
          player?.loadTracks()

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
    val currentSpeed = player?.playbackSpeed?.toFloat() ?: 1.0f
    dialogManager.showSpeedDialog(currentSpeed) { speed ->
      setPlayBackSpeed(speed)
      activity?.hideSystemUI()
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

    // Enable auto-play for playlist
    shouldAutoPlay = true

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

  @SuppressLint("UseKtx")
  private fun loadLink(
    link: Pair<ExtractorLink?, ExtractorUri?>,
    sub: SubtitleData? = null,
    nextEpisode: Boolean = false
  ) {
    currentSelectedLink = link

    // Force software decoding on emulator or if already in swDec mode
//    if (decodeMode == DecodeMode.swDec || context?.isTvOrEmulator() == true) {
//      pushOption("hwdec", "no")
//      // Additional options to ensure software decoding on emulator
//      pushOption("vd-lavc-software-fallback", "yes")
//      pushOption("ad-lavc-downmix", "yes")
//      // Force software video decoder to avoid goldfish decoder issues
//      pushOption("vd", "lavc:h264")
//      // Disable hardware accelerated codecs that might trigger goldfish
//      pushOption("hwdec-codecs", "")
//    }
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

    val uri = currentSelectedLink?.first?.url?.toUri()!!

    // Enable auto-play for new video
    shouldAutoPlay = true

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
    Timber.v("Exiting.")

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
    uiController.toggleControls()
    isShowing = uiController.isShowing
    playerBinding?.playerPausePlay?.requestFocus()
  }

  protected fun animateLayoutChanges() {
    uiController.animateLayoutChanges()
    isShowing = uiController.isShowing
  }

  fun updateUIVisibility() {
    uiController.updateUIVisibility(
      isSameEpisode = isSameEpisode,
      allLinksSize = allLinks.size,
      currentLinkIndex = allLinks.indexOf(currentSelectedLink)
    )
    isShowing = uiController.isShowing
    isLocked = uiController.isLocked

    // Update skip episode button based on playlist or episode state
    playerBinding?.playerSkipEpisode?.isGone = when {
      isPlaylistMode -> playlistState?.hasNext() ?: true
      !isSameEpisode -> allLinks.indexOf(currentSelectedLink) >= allLinks.size - 1
      else -> true
    }

    // Handle title visibility based on preferences
    var togglePlayerTitleGone = isLocked || !isShowing
    context?.let {
      val settingsManager = PreferenceManager.getDefaultSharedPreferences(it)
      val limitTitle = settingsManager.getInt(getString(R.string.prefer_limit_title_key), 0)
      if (limitTitle < 0) {
        togglePlayerTitleGone = true
      }
    }
    playerBinding?.playerVideoTitle?.isGone = togglePlayerTitleGone
  }

  private fun toggleLock() {
    uiController.toggleLock()
    isLocked = uiController.isLocked

    if (isLocked && isShowing) {
      playerBinding?.playerHolder?.postDelayed({
        if (isLocked && uiController.isShowing) {
          toggleControls()
        }
      }, 200)
    }
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
      Timber.tag(TAG).v("MPV_EVENT_SHUTDOWN")
    }

    if (eventId == MPVLib.mpvEventId.MPV_EVENT_START_FILE) {
      for (c in onloadCommands)
        MPVLib.command(c)
      onloadCommands.clear()
      playbackHasStarted = true
    }

    // Auto-play when file is loaded and ready
    if (eventId == MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED) {
      if (shouldAutoPlay && playbackHasStarted) {
        player?.paused = false
        autoHide() // Auto-hide controls when video starts playing
        Timber.tag(TAG).v("Auto-playing video on file loaded")
      }

      // Try to load playlist info from MPV if not already in playlist mode
      if (!isPlaylistMode) {
        loadPlaylistFromMPV()
      }
    }

    if (eventId == MPVLib.mpvEventId.MPV_EVENT_PLAYBACK_RESTART) {
      playerBinding?.playerBuffering?.isVisible = false

      // Auto-play when ready if shouldAutoPlay is true
      if (shouldAutoPlay && playbackHasStarted) {
        player?.paused = false
        autoHide() // Auto-hide controls when playback restarts
        Timber.tag(TAG).v("Auto-playing video on playback restart")
      }
    }
  }


  // mpv events

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
      "playlist-pos" -> {
        // Update playlist state when MPV changes position
        if (isPlaylistMode) {
          val newIndex = value.toInt()
          if (newIndex >= 0 && playlistState?.currentIndex != newIndex) {
            playlistState = playlistState?.copy(currentIndex = newIndex)
            updatePlaylistUI()
            Timber.tag(TAG).d("Playlist position changed to: $newIndex")
          }
        }
      }

      "playlist-count" -> {
        // Playlist count changed - reload if needed
        if (isPlaylistMode) {
          Timber.tag(TAG).d("Playlist count: $value")
        }
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
              val currentSid = MPVApi.getPropertyInt("sid") ?: -1
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

      "hwdec-current" -> {
        // Use the value delivered by the event instead of querying MPV synchronously
        updateDecoderButton(value)
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
        if (isPlaylistMode && playlistState != null) {
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
        if (!isSameEpisode) {
          playNext()
        } else {
          // End of file for a standalone item
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
            if (isSameEpisode) null else allLinks.indexOf(currentSelectedLink) + 1
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
          Timber.e(TAG, "mpv playback error: $errorMsg")
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
        Timber.v("Going into multi-window mode")
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
      Timber.d(TAG, "player indicates EOF, not saving watch-later config")
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
    Timber.v("Audio focus changed: $type")
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
      Timber.e(TAG, "unknown scheme: ${data.scheme}")
    return filepath
  }

  private fun openContentFd(uri: Uri): String? {
    val resolver = requireActivity().applicationContext.contentResolver
    Timber.v("Resolving content URI: $uri")
    val fd = try {
      val desc = resolver.openFileDescriptor(uri, "r")
      desc!!.detachFd()
    } catch (e: Exception) {
      Timber.e(TAG, "Failed to open content fd: $e")
      return null
    }
    // See if we skip the indirection and read the real file directly
    val path = MPVUtils.findRealPath(fd)
    if (path != null) {
      Timber.v("Found real file path: $path")
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

  // Helper methods for gesture handler callbacks
  private fun updateBrightnessOverlay(brightness: Float, text: String) {
    playerBinding?.apply {
      playerProgressbarRightHolder.isVisible = true
      playerProgressbarRight.max = 100_000
      playerProgressbarRight.progress = max(2_000, (brightness * 100_000f).toInt())

      playerProgressbarRightIcon.setImageResource(
        brightnessIcons[min(
          brightnessIcons.size - 1,
          max(0, round(brightness * (brightnessIcons.size - 1)).toInt())
        )]
      )
    }
  }

  private fun updateVolumeOverlay(volume: Int, text: String) {
    playerBinding?.apply {
      val maxVol = playerAudioManager.getMaxVolume() ?: 100
      val volumePercent = volume.toFloat() / maxVol.toFloat()

      playerProgressbarLeftHolder.isVisible = true
      playerProgressbarLeft.max = 100_000
      playerProgressbarLeft.progress = max(2_000, (volumePercent * 100_000f).toInt())

      playerProgressbarLeftIcon.setImageResource(
        volumeIcons[min(
          volumeIcons.size - 1,
          max(0, round(volumePercent * (volumeIcons.size - 1)).toInt())
        )]
      )
    }
  }

  private fun updateSeekOverlay(position: Long, text: String) {
    playerBinding?.apply {
      playerTimeText.text = text
      playerTimeText.isVisible = true
      playerProgressbarLeftHolder.isVisible = false
      playerProgressbarRightHolder.isVisible = false
    }
  }

  private fun hideGestureOverlays() {
    playerBinding?.apply {
      playerTimeText.isVisible = false
      playerProgressbarLeftHolder.isVisible = false
      playerProgressbarRightHolder.isVisible = false
    }
  }

  companion object {
    private const val TAG = "mpv"

    // Playlist arguments
    private const val ARG_STARTED_FROM_INTENT = "started_from_intent"
    private const val ARG_PLAYLIST_URLS = "playlist_urls"
    private const val ARG_PLAYLIST_TITLES = "playlist_titles"
    private const val ARG_PLAYLIST_START_INDEX = "playlist_start_index"

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

    /**
     * Create fragment instance with playlist support
     */
    fun newInstanceWithPlaylist(
      urls: List<String>,
      titles: List<String?> = emptyList(),
      startIndex: Int = 0,
      fromIntent: Boolean = false
    ): MPVFragment {
      return MPVFragment().apply {
        arguments = Bundle().apply {
          putStringArrayList(ARG_PLAYLIST_URLS, ArrayList(urls))
          putStringArrayList(ARG_PLAYLIST_TITLES, ArrayList(titles.map { it ?: "" }))
          putInt(ARG_PLAYLIST_START_INDEX, startIndex)
          putBoolean(ARG_STARTED_FROM_INTENT, fromIntent)
        }
      }
    }
  }
}
