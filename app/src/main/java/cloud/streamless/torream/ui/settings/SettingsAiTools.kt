package cloud.streamless.torream.ui.settings

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import cloud.streamless.torream.R
import cloud.streamless.torream.ai.AiKeyStore
import cloud.streamless.torream.ai.AiSettings
import cloud.streamless.torream.ai.providers.AiProvider
import cloud.streamless.torream.ui.dialog.SelectionDialog
import cloud.streamless.torream.ui.settings.SettingsFragment.Companion.getPref
import cloud.streamless.torream.ui.settings.SettingsFragment.Companion.setToolBarScrollFlags
import cloud.streamless.torream.ui.settings.SettingsFragment.Companion.setUpToolbar
import cloud.streamless.torream.utils.CommonActivitty.hideKeyboard
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SettingsAiTools : PreferenceFragmentCompat() {

    @Inject lateinit var keyStore: AiKeyStore

    private val providerKeyPrefs = mapOf(
        AiProvider.ProviderType.OPENAI to R.string.ai_key_openai_key,
        AiProvider.ProviderType.ANTHROPIC to R.string.ai_key_anthropic_key,
        AiProvider.ProviderType.GROQ to R.string.ai_key_groq_key,
        AiProvider.ProviderType.OPENROUTER to R.string.ai_key_openrouter_key,
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.settings_title_top, container, false)
        val listContainer = view.findViewById<ViewGroup>(android.R.id.list_container)
        val preferenceView = super.onCreateView(inflater, listContainer, savedInstanceState)
        listContainer.removeAllViews()
        listContainer.addView(preferenceView)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpToolbar(R.string.ai_tools)
        setToolBarScrollFlags()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        activity?.hideKeyboard()
        setPreferencesFromResource(R.xml.settings_ai_tools, rootKey)

        providerKeyPrefs.forEach { (type, keyRes) ->
            getPref(keyRes)?.let { pref ->
                updateKeySummary(pref, type)
                pref.setOnPreferenceClickListener {
                    showApiKeyDialog(pref, type)
                    true
                }
            }
        }

        getPref(R.string.ai_default_provider_key)?.let { pref ->
            updateDefaultProviderSummary(pref)
            pref.setOnPreferenceClickListener {
                showDefaultProviderDialog(pref)
                true
            }
        }

        getPref(R.string.ai_openrouter_model_key)?.let { pref ->
            updateOpenRouterModelSummary(pref)
            pref.setOnPreferenceClickListener {
                showOpenRouterModelDialog(pref)
                true
            }
        }
    }

    private fun updateKeySummary(pref: Preference, type: AiProvider.ProviderType) {
        val key = keyStore.getKey(type)
        pref.summary = if (key.isNullOrBlank()) {
            getString(R.string.ai_key_not_set)
        } else {
            getString(R.string.ai_key_set_masked, maskKey(key))
        }
    }

    private fun maskKey(key: String): String =
        if (key.length <= 4) "••••" else "••••${key.takeLast(4)}"

    private fun showApiKeyDialog(pref: Preference, type: AiProvider.ProviderType) {
        val ctx = context ?: return
        val editText = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(keyStore.getKey(type).orEmpty())
            setSelection(text.length)
        }

        AlertDialog.Builder(ctx, R.style.BaseMaterialDialogTheme)
            .setTitle(pref.title)
            .setMessage(R.string.ai_enter_api_key)
            .setView(editText)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                keyStore.setKey(type, editText.text.toString().trim())
                updateKeySummary(pref, type)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateDefaultProviderSummary(pref: Preference) {
        val ctx = context ?: return
        val type = AiSettings.getDefaultProvider(ctx)
        pref.summary = type?.name ?: getString(R.string.ai_key_not_set)
    }

    private fun showDefaultProviderDialog(pref: Preference) {
        val ctx = context ?: return
        val types = AiProvider.ProviderType.entries
        val current = AiSettings.getDefaultProvider(ctx)

        SelectionDialog.single(
            types.map { it.name },
            types.indexOf(current),
            getString(R.string.ai_default_provider),
            true
        ).show(parentFragmentManager) { bundle ->
            bundle?.getIntegerArrayList(SelectionDialog.ITEMS_SELECTED)?.get(0)?.let { index ->
                AiSettings.setDefaultProvider(ctx, types[index])
                updateDefaultProviderSummary(pref)
            }
        }
    }

    private fun updateOpenRouterModelSummary(pref: Preference) {
        val ctx = context ?: return
        val model = AiSettings.getModel(ctx, AiProvider.ProviderType.OPENROUTER)
        pref.summary = model.ifBlank { getString(R.string.ai_openrouter_model_des) }
    }

    private fun showOpenRouterModelDialog(pref: Preference) {
        val ctx = context ?: return
        val editText = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(AiSettings.getModel(ctx, AiProvider.ProviderType.OPENROUTER))
            setSelection(text.length)
        }

        AlertDialog.Builder(ctx, R.style.BaseMaterialDialogTheme)
            .setTitle(R.string.ai_openrouter_model)
            .setView(editText)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                AiSettings.setModel(ctx, AiProvider.ProviderType.OPENROUTER, editText.text.toString().trim())
                updateOpenRouterModelSummary(pref)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
