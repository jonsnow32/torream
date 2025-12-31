package com.tv.apps.zippy.ui.settings

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.tv.apps.zippy.BuildConfig
import com.tv.apps.zippy.MainActivityViewModel.Companion.applyContentRect
import com.tv.apps.zippy.R
import com.tv.apps.zippy.databinding.FragmentSettingsBinding
import com.tv.apps.zippy.model.PlaybackData
import com.tv.apps.zippy.model.VideoLink
import com.tv.apps.zippy.ui.dialog.UrlInputDialog
import com.tv.apps.zippy.utils.AutoClearedValue.Companion.autoCleared
import com.tv.apps.zippy.utils.CommonActivitty.setLocale
import com.tv.apps.zippy.utils.LayoutMode
import com.tv.apps.zippy.utils.PlaybackDataHelper
import com.tv.apps.zippy.utils.UIHelper.clipboardHelper
import com.tv.apps.zippy.utils.UIHelper.navigate
import com.tv.apps.zippy.utils.UnifiedFile
import com.tv.apps.zippy.utils.UnifiedFileFactory
import com.tv.apps.zippy.utils.Utils.logError
import com.tv.apps.zippy.utils.Utils.normalSafeApiCall
import com.tv.apps.zippy.utils.isLayout
import com.tv.apps.zippy.utils.isTvOrEmulator
import com.tv.apps.zippy.utils.txt
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

  override fun onAttach(context: Context) {
    val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)
    val localeCode = settingsManager.getString(getString(R.string.locale_key), Locale.getDefault().language)
    val wrappedContext = context.let {
      setLocale(it, localeCode)
      it
    }
    super.onAttach(wrappedContext)
  }

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
      val context = context ?: return
      urlBtn.setOnClickListener {

        val list = listOf(
          // Magnet Links
//          "magnet:?xt=urn:btih:dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c",
//          "magnet:?xt=urn:btih:08ada5c11c42e4a0c83cc8521d04e6723d12fa27&dn=Sintel",
//          "magnet:?xt=urn:btih:7c3fcd16e27e49e243ec97465ea1b19e5bbd73d2&dn=Big+Buck+Bunny",

          // HTTP Video Links - Short Duration
          "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
          "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
          "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
          "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
          "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
          "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4",
          "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",

        )

        var text = if(BuildConfig.DEBUG) list.random() else ""

        // Optionally read from clipboard
        (activity?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager?)?.primaryClip?.getItemAt(
          0
        )?.text?.toString()?.let { copy ->
          if (copy.isNotEmpty() && (copy.startsWith("http") || copy.startsWith("magnet") || UnifiedFileFactory.fromUri(context, copy.toUri())?.exists() == true))
            text = copy
        }

        UrlInputDialog.newInstance("magnet:?xt=urn:btih:55C3737267AC920ED18ECA2FB1A94DDF18A12397&dn=Eminem+-+Kamikaze+%282018%29+Mp3+%28320kbps%29+%5BHunter%5D&tr=udp%3A%2F%2Ftracker.leechers-paradise.org%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.open-internet.nl%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.coppersurfer.tk%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.pirateparty.gr%3A6969%2Fannounce&tr=udp%3A%2F%2Fpublic.popcorn-tracker.org%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce&tr=udp%3A%2F%2Feddie4.nl%3A6969%2Fannounce&tr=udp%3A%2F%2Feddie4.nl%3A6969%2Fannounce&tr=udp%3A%2F%2Fopen.demonii.si%3A1337%2Fannounce&tr=udp%3A%2F%2Finferno.demonoid.pw%3A3418%2Fannounce&tr=udp%3A%2F%2F9.rarbg.com%3A2710%2Fannounce&tr=udp%3A%2F%2Fexodus.desync.com%3A6969%2Fannounce&tr=udp%3A%2F%2Fzephir.monocul.us%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.uw0.xyz%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce&tr=http%3A%2F%2Ftracker.openbittorrent.com%3A80%2Fannounce&tr=udp%3A%2F%2Fopentracker.i2p.rocks%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.internetwarriors.net%3A1337%2Fannounce&tr=udp%3A%2F%2Ftracker.leechers-paradise.org%3A6969%2Fannounce&tr=udp%3A%2F%2Fcoppersurfer.tk%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.zer0day.to%3A1337%2Fannounce").show(parentFragmentManager)
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
    // Use string resource for version info
    binding.versionInfo.text = getString(R.string.version_info, BuildConfig.VERSION_NAME)
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
    /*
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
      intent.action = Intent.ACTION_PICK
      intent.data = MediaStore.Video.Media.INTERNAL_CONTENT_URI
    }
    */

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

    // Commented unused function to avoid warning
    /*
    fun Fragment?.setToolBarScrollFlags() {
      if (this?.isTvOrEmulator() == true) {
        val settingsAppbar = view?.findViewById<MaterialToolbar>(R.id.general_toolbar)

        settingsAppbar?.updateLayoutParams<AppBarLayout.LayoutParams> {
          scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL
        }
      }
    }
    */


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
