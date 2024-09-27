package cloud.app.csplayer.ui.settings

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import cloud.app.csplayer.BuildConfig
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.FragmentSettingsBinding
import cloud.app.csplayer.ui.player.EXTRA_VIDEO_URLS_NAME_HEADERS

import cloud.app.csplayer.utils.LayoutMode
import cloud.app.csplayer.utils.SingleSelectionHelper.showNginxTextInputDialog
import cloud.app.csplayer.utils.UIHelper
import cloud.app.csplayer.utils.UIHelper.clipboardHelper
import cloud.app.csplayer.utils.UIHelper.navigate
import cloud.app.csplayer.utils.UIHelper.toPx
import cloud.app.csplayer.utils.Utils.isARM
import cloud.app.csplayer.utils.Utils.logError
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

    /**
     * On TV you cannot properly scroll to the bottom of settings, this fixes that.
     * */
    fun PreferenceFragmentCompat.setPaddingBottom() {
      if (isTvOrEmulator()) {
        listView?.setPadding(0, 0, 0, 100.toPx)
      }
    }

    fun PreferenceFragmentCompat.setToolBarScrollFlags() {
      if (isTvOrEmulator()) {
        val settingsAppbar = view?.findViewById<MaterialToolbar>(R.id.settings_toolbar)

        settingsAppbar?.updateLayoutParams<AppBarLayout.LayoutParams> {
          scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL
        }
      }
    }

    fun Fragment?.setToolBarScrollFlags() {
      if (this?.isTvOrEmulator() == true) {
        val settingsAppbar = view?.findViewById<MaterialToolbar>(R.id.settings_toolbar)

        settingsAppbar?.updateLayoutParams<AppBarLayout.LayoutParams> {
          scrollFlags = AppBarLayout.LayoutParams.SCROLL_FLAG_NO_SCROLL
        }
      }
    }

    fun Fragment?.setUpToolbar(title: String) {
      if (this == null) return
      val settingsToolbar = view?.findViewById<MaterialToolbar>(R.id.settings_toolbar) ?: return

      settingsToolbar.apply {
        setTitle(title)
        if (isTvOrEmulator()) {
          setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
          setNavigationOnClickListener {
            activity?.onBackPressedDispatcher?.onBackPressed()
          }
        }
      }
      UIHelper.fixPaddingStatusbar(settingsToolbar)
    }

    fun Fragment?.setUpToolbar(@StringRes title: Int) {
      if (this == null) return
      val settingsToolbar = view?.findViewById<MaterialToolbar>(R.id.settings_toolbar) ?: return
      val settingsAppBar = view?.findViewById<AppBarLayout>(R.id.settings_appbar) ?: return

      settingsToolbar.apply {
        setTitle(title)
        setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
        children.firstOrNull { it is ImageView }?.tag = getString(R.string.tv_no_focus_tag)
        setNavigationOnClickListener {
          activity?.onBackPressedDispatcher?.onBackPressed()
        }
      }
      UIHelper.fixPaddingStatusbar(settingsAppBar)
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

  override fun onDestroyView() {
    binding = null
    super.onDestroyView()
  }

  var binding: FragmentSettingsBinding? = null
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
    binding?.apply {
      listOf(
        settingsGeneral to R.id.action_navigation_global_to_navigation_settings_general,
        settingsPlayer to R.id.action_navigation_global_to_navigation_settings_player,
        settingsUpdates to R.id.action_navigation_global_to_navigation_settings_updates,
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
        var text =
          if(BuildConfig.DEBUG) "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4" else "";

        (activity?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager?)?.primaryClip?.getItemAt(
          0
        )?.text?.toString()?.let { copy ->
          if(copy.isNotEmpty() && copy.contains("http"))
            text = copy;
        }

        var id = R.id.global_to_navigation_player;
        if(isARM()) {
          id = R.id.global_to_navigation_mpv_player;
        }

        activity?.showNginxTextInputDialog("Your Link", text, 16, {
        }, {
          activity?.navigate(
            id,
            bundleOf(EXTRA_VIDEO_URLS_NAME_HEADERS to arrayListOf<String>(it,"user_url_1", "").toTypedArray())
          )
        });
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
    binding?.versionInfo?.text = "v${BuildConfig.VERSION_NAME}"
    binding?.buildDate?.text = buildTimestamp
    binding?.appVersionInfo?.setOnLongClickListener {
      clipboardHelper(txt(R.string.extension_version), "v$appVersion $commitInfo $buildTimestamp")
      true
    }

    UIHelper.fixPaddingStatusbar(binding?.settingsProfile)
  }
}
