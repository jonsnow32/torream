package cloud.streamless.torream.ui.player.scripting

import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.children
import androidx.fragment.app.Fragment
import cloud.streamless.torream.MainActivityViewModel.Companion.applyContentRect
import cloud.streamless.torream.R
import cloud.streamless.torream.ui.player.mpv.MPVUtils
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import java.io.File

/**
 * Lists user Lua scripts (ui/player/scripting/ScriptStore) and lets the user create, toggle,
 * delete, and edit them. Editing reuses the same AlertDialog+EditText shape as
 * SettingsPlayer.showMpvConfEditor() rather than a separate full-screen editor.
 */
class ScriptManagerFragment : Fragment() {

  private val scriptsDir by lazy { ScriptStore.scriptsDir(requireContext()) }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View = inflater.inflate(R.layout.fragment_lua_scripts, container, false)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val toolbar = view.findViewById<MaterialToolbar>(R.id.general_toolbar)
    val appBar = view.findViewById<AppBarLayout>(R.id.general_appbar)
    toolbar.apply {
      setTitle(R.string.lua_scripts_settings)
      setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
      children.firstOrNull { it is ImageView }?.tag = getString(R.string.tv_no_focus_tag)
      setNavigationOnClickListener { activity?.onBackPressedDispatcher?.onBackPressed() }
      inflateMenu(R.menu.lua_scripts_menu)
      setOnMenuItemClickListener { item ->
        if (item.itemId == R.id.addScript) {
          showCreateScriptDialog()
          true
        } else {
          false
        }
      }
    }
    applyContentRect(appBar, view.findViewById(R.id.scriptsList))

    refreshList()
  }

  private fun refreshList() {
    val root = view ?: return
    val container = root.findViewById<LinearLayout>(R.id.scriptsList)
    val emptyState = root.findViewById<TextView>(R.id.emptyState)
    container.removeAllViews()

    val scripts = ScriptStore.list(scriptsDir)
    emptyState.visibility = if (scripts.isEmpty()) View.VISIBLE else View.GONE

    val inflater = LayoutInflater.from(requireContext())
    scripts.forEach { script ->
      val row = inflater.inflate(R.layout.item_lua_script, container, false)
      row.findViewById<TextView>(R.id.scriptName).text = script.name

      row.findViewById<SwitchCompat>(R.id.scriptEnabledSwitch).apply {
        isChecked = script.enabled
        setOnCheckedChangeListener { _, checked ->
          if (checked != script.enabled) {
            ScriptStore.setEnabled(script.file, checked)
            refreshList()
          }
        }
      }

      row.findViewById<View>(R.id.scriptDeleteButton).setOnClickListener {
        confirmDelete(script)
      }

      row.setOnClickListener { showScriptEditor(script.file) }

      container.addView(row)
    }
  }

  private fun confirmDelete(script: ScriptStore.LuaScript) {
    AlertDialog.Builder(requireContext(), R.style.BaseMaterialDialogTheme)
      .setTitle(script.name)
      .setMessage(R.string.lua_scripts_delete_confirm)
      .setPositiveButton(R.string.delete) { _, _ ->
        ScriptStore.delete(script.file)
        refreshList()
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun showCreateScriptDialog() {
    val ctx = requireContext()
    val editText = EditText(ctx).apply {
      inputType = InputType.TYPE_CLASS_TEXT
      hint = getString(R.string.lua_scripts_name_hint)
    }

    AlertDialog.Builder(ctx, R.style.BaseMaterialDialogTheme)
      .setTitle(R.string.lua_scripts_new)
      .setView(editText)
      .setPositiveButton(android.R.string.ok) { _, _ ->
        ScriptStore.create(scriptsDir, editText.text.toString())
          .onSuccess { file ->
            refreshList()
            showScriptEditor(file)
          }
          .onFailure {
            Toast.makeText(ctx, R.string.lua_scripts_invalid_name, Toast.LENGTH_SHORT).show()
          }
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun showScriptEditor(file: File) {
    val ctx = requireContext()
    val dp8 = MPVUtils.convertDp(ctx, 8f)

    val editText = EditText(ctx).apply {
      setText(ScriptStore.read(file))
      typeface = Typeface.MONOSPACE
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
      gravity = Gravity.TOP or Gravity.START
      inputType = InputType.TYPE_CLASS_TEXT or
        InputType.TYPE_TEXT_FLAG_MULTI_LINE or
        InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
      isSingleLine = false
      maxLines = 999
      isVerticalScrollBarEnabled = true
      setHorizontallyScrolling(false)
      setLines(20)
      setPadding(dp8, dp8, dp8, dp8)
    }
    LuaSyntaxHighlighter.attach(editText)

    val layout = LinearLayout(ctx).apply {
      orientation = LinearLayout.VERTICAL
      addView(editText)
    }

    AlertDialog.Builder(ctx, R.style.BaseMaterialDialogTheme)
      .setTitle(file.name.removeSuffix(ScriptStore.DISABLED_SUFFIX))
      .setView(layout)
      .setPositiveButton(android.R.string.ok) { _, _ ->
        runCatching {
          ScriptStore.write(file, editText.text.toString())
        }.onSuccess {
          Toast.makeText(ctx, R.string.lua_scripts_saved, Toast.LENGTH_SHORT).show()
        }.onFailure {
          Toast.makeText(ctx, "Failed to save: ${it.message}", Toast.LENGTH_LONG).show()
        }
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }
}
