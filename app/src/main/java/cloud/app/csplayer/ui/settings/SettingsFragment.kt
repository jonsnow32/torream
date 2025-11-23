package cloud.app.csplayer.ui.settings

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import cloud.app.csplayer.BuildConfig
import cloud.app.csplayer.MainActivityViewModel.Companion.applyContentRect
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.FragmentSettingsBinding
import cloud.app.csplayer.model.PlaybackData
import cloud.app.csplayer.model.VideoLink
import cloud.app.csplayer.ui.dialog.UrlInputDialog
import cloud.app.csplayer.utils.AutoClearedValue.Companion.autoCleared
import cloud.app.csplayer.utils.LayoutMode
import cloud.app.csplayer.utils.PlaybackDataHelper
import cloud.app.csplayer.utils.UIHelper.clipboardHelper
import cloud.app.csplayer.utils.UIHelper.navigate
//import cloud.app.csplayer.utils.UIHelper.navigate
import cloud.app.csplayer.utils.UIHelper.toPx
import cloud.app.csplayer.utils.Utils.logError
import cloud.app.csplayer.utils.Utils.normalSafeApiCall
import cloud.app.csplayer.utils.isLayout
import cloud.app.csplayer.utils.isTvOrEmulator
import cloud.app.csplayer.utils.txt
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SettingsFragment : Fragment() {

  private var binding by autoCleared<FragmentSettingsBinding>()

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    val localBinding = FragmentSettingsBinding.inflate(inflater, container, false)
    binding = localBinding
    return localBinding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    binding.apply {
      applyContentRect(null, root)

      listOf(
        settingsGeneral to R.id.action_navigation_global_to_navigation_settings_general,
        settingsPlayer to R.id.action_navigation_global_to_navigation_settings_player,
        settingsUpdates to R.id.action_navigation_global_to_navigation_settings_updates,
        settingsDownload to R.id.action_navigation_global_to_navigation_settings_download,
      ).forEach { (view, navigationId) ->
        view.apply {
          setOnClickListener {
            activity?.navigate(navigationId, Bundle())
          }
          if (context?.isLayout(LayoutMode.Tv.id) == true) {
            isFocusable = true
            isFocusableInTouchMode = true
          }
        }
      }

      // Default focus on TV
      if (context?.isLayout(LayoutMode.Tv.id) == true) {
        settingsGeneral.requestFocus()
      }

      urlBtn.setOnClickListener {

        val list = listOf(
          // Magnet Links
          "magnet:?xt=urn:btih:dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c",
          "magnet:?xt=urn:btih:08ada5c11c42e4a0c83cc8521d04e6723d12fa27&dn=Sintel",
          "magnet:?xt=urn:btih:7c3fcd16e27e49e243ec97465ea1b19e5bbd73d2&dn=Big+Buck+Bunny",

          // HTTP Video Links - Short Duration
          "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
          "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
          "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
          "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
          "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
          "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4",
          "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMolecules.mp4",
          "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",

          // Public Domain/Creative Commons
          "https://archive.org/download/BigBuckBunny_124/Content/Big_Buck_Bunny_1080_10s_30MB.mp4",

          // More Magnet Links (Various Torrents)
          "magnet:?xt=urn:btih:6a9759bffd5c0af65319979fb7832189f4f3c35d&dn=Tears+of+Steel&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A80%2Fannounce",
          "magnet:?xt=urn:btih:1e8fbd02b98722a0b3192873f5e322b945d12157&dn=Blender+Foundation&tr=udp%3A%2F%2Ftracker.publicbt.com%3A80%2Fannounce"
        )
        var text = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"//list.random()

        // Optionally read from clipboard
        (activity?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager?)?.primaryClip?.getItemAt(
          0
        )?.text?.toString()?.let { copy ->
          if (copy.isNotEmpty() && (copy.contains("http") || copy.contains("magnet")))
            text = copy
        }

        UrlInputDialog.newInstance(text).show(parentFragmentManager)
      }

      openLocal.setOnClickListener {
        openLocalVideo(videoResultLauncher)
//        activity?.navigate(R.id.feedFragment)
      }
    }
    val appVersion = BuildConfig.VERSION_NAME
    val commitInfo = getString(R.string.commit_hash)
    val buildTimestamp = SimpleDateFormat.getDateTimeInstance(
      DateFormat.LONG, DateFormat.LONG,
      Locale.getDefault()
    ).apply {
      timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(BuildConfig.BUILD_DATE)).replace("UTC", "")
    binding.versionInfo.text = "v${BuildConfig.VERSION_NAME}"
    binding.buildDate.text = buildTimestamp
    binding.appVersionInfo.setOnLongClickListener {
      clipboardHelper(txt(R.string.extension_version), "v$appVersion $commitInfo $buildTimestamp")
      true
    }


  }

  private fun openLocalVideo(videoResultLauncher: ActivityResultLauncher<Intent>) {
    val intent = Intent().apply {
      action = Intent.ACTION_GET_CONTENT
      type = "video/*"
      addCategory(Intent.CATEGORY_OPENABLE)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Grant temporary read permission
    }

    // For Android versions before API 19, use Intent.ACTION_PICK
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
      intent.action = Intent.ACTION_PICK
      intent.data = MediaStore.Video.Media.INTERNAL_CONTENT_URI
    }

    // Launch the intent to open the file chooser
    normalSafeApiCall {
      videoResultLauncher.launch(
        Intent.createChooser(
          intent,
          getString(R.string.open_local_video)
        )
      )
    }
  }

  private val videoResultLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
    val selectedVideoUri = result.data?.data ?: return@registerForActivityResult

    // Create PlaybackData for local video file
    val playbackData = PlaybackData(
      title = "Local Video",
      videoLinks = listOf(
        VideoLink(
          url = selectedVideoUri.toString(),
          name = "Local Video",
          headers = emptyMap(),
          position = 0L,
          subtitles = emptyList()
        )
      ),
      subtitles = emptyList(),
      videoStartIndex = 0,
      subtitleStartIndex = 0,
      isSameEpisode = true,
      hasAd = false
    )

    val bundle = PlaybackDataHelper.createBundle(playbackData)
    activity?.navigate(R.id.global_to_navigation_mpv_player, bundle)
  }

  companion object {

    fun PreferenceFragmentCompat?.getPref(id: Int): Preference? {
      if (this == null) return null

      return try {
        findPreference(getString(id))
      } catch (e: Exception) {
        logError(e)
        null
      }
    }

    fun PreferenceFragmentCompat.setToolBarScrollFlags() {
      if (isTvOrEmulator()) {
        val settingsAppbar = view?.findViewById<MaterialToolbar>(R.id.general_toolbar)

        settingsAppbar?.updateLayoutParams<AppBarLayout.LayoutParams> {
          scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL
        }
      }
    }

    fun Fragment?.setToolBarScrollFlags() {
      if (this?.isTvOrEmulator() == true) {
        val settingsAppbar = view?.findViewById<MaterialToolbar>(R.id.general_toolbar)

        settingsAppbar?.updateLayoutParams<AppBarLayout.LayoutParams> {
          scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL
        }
      }
    }


    fun PreferenceFragmentCompat?.setUpToolbar(@StringRes title: Int) {
      if (this == null) return
      val settingsToolbar = view?.findViewById<MaterialToolbar>(R.id.general_toolbar) ?: return
      val settingsAppBar = view?.findViewById<AppBarLayout>(R.id.general_appbar) ?: return

      settingsToolbar.apply {
        setTitle(title)
        setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
        children.firstOrNull { it is ImageView }?.tag = getString(R.string.tv_no_focus_tag)
        setNavigationOnClickListener {
          activity?.onBackPressedDispatcher?.onBackPressed()
        }
      }

      applyContentRect(settingsAppBar, listView)
    }

    fun getFolderSize(dir: File): Long {
      var size: Long = 0
      dir.listFiles()?.let {
        for (file in it) {
          size += if (file.isFile) {
            // System.out.println(file.getName() + " " + file.length());
            file.length()
          } else getFolderSize(file)
        }
      }

      return size
    }
  }
}
