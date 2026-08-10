package cloud.streamless.torream.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import cloud.streamless.torream.R
import cloud.streamless.torream.databinding.DialogAddNetworkShareBinding
import cloud.streamless.torream.media.repository.NetworkShareRepository
import cloud.streamless.torream.model.NetworkShare
import cloud.streamless.torream.model.NetworkShareProtocol
import cloud.streamless.torream.ui.browse.network.FtpBrowseClient
import cloud.streamless.torream.ui.browse.network.SmbBrowseClient
import cloud.streamless.torream.ui.browse.network.WebDavBrowseClient
import cloud.streamless.torream.utils.AutoClearedValue.Companion.autoCleared
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class AddNetworkShareDialog : DockingDialog() {
  private var binding by autoCleared<DialogAddNetworkShareBinding>()

  @Inject
  lateinit var networkShareRepository: NetworkShareRepository

  private val webDavBrowseClient = WebDavBrowseClient()
  private val ftpBrowseClient = FtpBrowseClient()
  private val smbBrowseClient = SmbBrowseClient()

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
  ): View {
    binding = DialogAddNetworkShareBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    binding.apply {
      protocolSmb.isChecked = true
      updatePortHint(NetworkShareProtocol.SMB)

      protocolToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
        if (isChecked) updatePortHint(protocolFromCheckedId(checkedId))
      }

      cancelBtt.setOnClickListener { dialog?.dismiss() }
      saveBtt.setOnClickListener { attemptSave() }
    }
  }

  private fun protocolFromCheckedId(checkedId: Int): NetworkShareProtocol = when (checkedId) {
    R.id.protocolFtp -> NetworkShareProtocol.FTP
    R.id.protocolWebdav -> NetworkShareProtocol.WEBDAV
    else -> NetworkShareProtocol.SMB
  }

  private fun updatePortHint(protocol: NetworkShareProtocol) {
    if (binding.portInput.text.isNullOrBlank()) {
      val defaultPort = when (protocol) {
        NetworkShareProtocol.SMB -> "445"
        NetworkShareProtocol.FTP -> "21"
        NetworkShareProtocol.WEBDAV -> "80"
      }
      binding.portInput.setText(defaultPort)
    }
  }

  private fun attemptSave() {
    val protocol = protocolFromCheckedId(binding.protocolToggleGroup.checkedButtonId)
    val name = binding.nameInput.text?.toString().orEmpty()
    val host = binding.hostInput.text?.toString().orEmpty().trim()
    val port = binding.portInput.text?.toString()?.toIntOrNull()
    val path = binding.pathInput.text?.toString().orEmpty()
    val username = binding.usernameInput.text?.toString()?.takeUnless { it.isBlank() }
    val password = binding.passwordInput.text?.toString()?.takeUnless { it.isBlank() }
    val useTls = binding.tlsSwitch.isChecked

    if (host.isEmpty() || port == null) {
      showError(getString(R.string.network_share_host) + "/" + getString(R.string.network_share_port))
      return
    }

    val share = NetworkShare(
      protocol = protocol,
      displayName = name.ifBlank { host },
      host = host,
      port = port,
      basePath = path,
      username = username,
      password = password,
      useTls = useTls
    )

    binding.progressBar.isVisible = true
    binding.saveBtt.isEnabled = false
    binding.errorText.isVisible = false

    viewLifecycleOwner.lifecycleScope.launch {
      val result = withContext(Dispatchers.IO) {
        runCatching {
          when (share.protocol) {
            NetworkShareProtocol.SMB -> smbBrowseClient.list(share, "")
            NetworkShareProtocol.FTP -> ftpBrowseClient.list(share, "")
            NetworkShareProtocol.WEBDAV -> webDavBrowseClient.list(share, "")
          }
        }
      }
      binding.progressBar.isVisible = false
      binding.saveBtt.isEnabled = true
      result.onSuccess {
        networkShareRepository.saveShare(share)
        dialog?.dismiss()
      }.onFailure { e ->
        showError(e.message ?: e.toString())
      }
    }
  }

  private fun showError(message: String) {
    binding.errorText.text = getString(R.string.network_share_connect_failed, message)
    binding.errorText.isVisible = true
  }
}
