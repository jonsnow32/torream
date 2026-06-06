package cloud.streamless.torream.ui.dialog

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * Activity with dialog theme to handle ACTION_SEND intents from other apps.
 * This allows Torream to appear in the "Share via" menu and receive URLs
 * from browsers and other apps, similar to ADM (Advanced Download Manager).
 */
@AndroidEntryPoint
class ShareReceiverActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Don't set content view - we'll show a dialog fragment instead
    handleIntent(intent)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleIntent(intent)
  }

  private fun handleIntent(intent: Intent?) {
    if (intent == null) {
      finish()
      return
    }

    // Debug logging to see what's in the intent
    Timber.d("=== ShareReceiverActivity Intent Debug ===")
    Timber.d("Action: ${intent.action}")
    Timber.d("Type: ${intent.type}")
    Timber.d("Data: ${intent.data}")
    Timber.d("EXTRA_TEXT: ${intent.getStringExtra(Intent.EXTRA_TEXT)}")
    Timber.d("EXTRA_SUBJECT: ${intent.getStringExtra(Intent.EXTRA_SUBJECT)}")
    Timber.d("EXTRA_TITLE: ${intent.getStringExtra(Intent.EXTRA_TITLE)}")
    Timber.d("ClipData: ${intent.clipData}")
    if (intent.clipData != null && intent.clipData!!.itemCount > 0) {
      Timber.d("ClipData text: ${intent.clipData!!.getItemAt(0).text}")
    }
    Timber.d("Extras keys: ${intent.extras?.keySet()?.joinToString(", ")}")
    Timber.d("==========================================")

    when (intent.action) {
      Intent.ACTION_SEND -> {
        // Handle shared text (URL)
        if (intent.type == "text/plain") {
          // Try EXTRA_TEXT first, then fall back to ClipData
          var sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)

          // If EXTRA_TEXT is null, try to get text from ClipData
          if (sharedText == null && intent.clipData != null && intent.clipData!!.itemCount > 0) {
            sharedText = intent.clipData!!.getItemAt(0).text?.toString()
            Timber.d("Extracted URL from ClipData: $sharedText")
          }

          val sharedTitle = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            ?: intent.getStringExtra(Intent.EXTRA_TITLE)
            ?: intent.getStringExtra("title")
          val description = intent.getStringExtra("description")
          val headers = extractHeadersFromIntent(intent)
          val hasAds = intent.getBooleanExtra("hasAds", true)

          if (sharedText != null) {
            showUrlInputDialog(sharedText, sharedTitle ?: description, headers, hasAds)
          } else {
            Timber.w("Received ACTION_SEND but no text found in EXTRA_TEXT or ClipData")
            finish()
          }
        } else {
          Timber.w("Received ACTION_SEND with unsupported type: ${intent.type}")
          finish()
        }
      }

      Intent.ACTION_VIEW -> {
        // Handle direct URL view intents (http://, https://, magnet:)
        val url = intent.dataString
        val title = intent.getStringExtra(Intent.EXTRA_TITLE)
          ?: intent.getStringExtra("title")
        val headers = extractHeadersFromIntent(intent)
        val hasAds = intent.getBooleanExtra("hasAds", true)

        if (url != null) {
          showUrlInputDialog(url, title, headers, hasAds)
        } else {
          Timber.w("Received ACTION_VIEW but no data found")
          finish()
        }
      }

      else -> {
        Timber.w("Received unsupported action: ${intent.action}")
        finish()
      }
    }
  }

  /**
   * Extract headers from intent extras.
   * Supports multiple formats:
   * 1. HashMap<String, String> - Direct map
   * 2. String array with alternating key-value pairs - ["User-Agent", "Mozilla/5.0", "Referer", "https://example.com"]
   * 3. Bundle with string key-value pairs
   */
  @Suppress("DEPRECATION")
  private fun extractHeadersFromIntent(intent: Intent): Map<String, String> {
    val headers = mutableMapOf<String, String>()

    // Try to get headers as HashMap (most direct format)
    try {
      @Suppress("UNCHECKED_CAST")
      val headersMap = intent.getSerializableExtra("headers") as? HashMap<String, String>
      if (headersMap != null) {
        headers.putAll(headersMap)
        Timber.d("Extracted ${headers.size} headers from HashMap")
        headersMap.forEach { (key, value) ->
          Timber.d("Header: $key = $value")
        }
        return headers
      }
    } catch (e: Exception) {
      Timber.d("Headers not in HashMap format: ${e.message}")
    }

    // Try to get headers as String array (key-value pairs)
    try {
      val headersArray = intent.getStringArrayExtra("headers")
      if (headersArray != null && headersArray.size % 2 == 0) {
        for (i in headersArray.indices step 2) {
          if (i + 1 < headersArray.size) {
            val key = headersArray[i]
            val value = headersArray[i + 1]
            headers[key] = value
            Timber.d("Extracted header from array: $key = $value")
          }
        }
        if (headers.isNotEmpty()) {
          Timber.d("Total headers extracted from array: ${headers.size}")
          return headers
        }
      }
    } catch (e: Exception) {
      Timber.d("Headers not in String[] format: ${e.message}")
    }

    // Try Bundle format (for backward compatibility)
    try {
      val headersBundle = intent.getBundleExtra("headers")
      if (headersBundle != null) {
        for (key in headersBundle.keySet()) {
          val value = headersBundle.getString(key)
          if (value != null) {
            headers[key] = value
            Timber.d("Extracted header from bundle: $key = $value")
          }
        }
        if (headers.isNotEmpty()) {
          Timber.d("Total headers extracted from Bundle: ${headers.size}")
          return headers
        }
      }
    } catch (e: Exception) {
      Timber.d("Headers not in Bundle format: ${e.message}")
    }

    if (headers.isEmpty()) {
      Timber.d("No headers found in intent")
    }

    return headers
  }

  private fun showUrlInputDialog(url: String, title: String?, headers: Map<String, String> = emptyMap(), hasAds: Boolean = true) {
    Timber.d("Showing UrlInputDialog for URL: $url, title: $title, headers: ${headers.size}")

    // Check if dialog is already showing to avoid duplicate
    val existingDialog = supportFragmentManager.findFragmentByTag("UrlInputDialog")
    if (existingDialog != null) {
      Timber.d("Dialog already showing, finishing activity")
      finish()
      return
    }

    // Show UrlInputDialog
    val dialog = UrlInputDialog.newInstance(
      url = url,
      name = title,
      headers = headers.ifEmpty { null },
      simpleDownload = true,
      hasAds = hasAds
    )

    // Set dismiss listener before showing
    dialog.dialog?.setOnDismissListener {
      Timber.d("Dialog dismissed, finishing activity")
      finish()
    }

    dialog.show(supportFragmentManager, "UrlInputDialog")

    // Backup: Monitor fragment lifecycle to finish activity when dialog is removed
    supportFragmentManager.registerFragmentLifecycleCallbacks(
      object : androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentDetached(fm: androidx.fragment.app.FragmentManager, f: androidx.fragment.app.Fragment) {
          if (f is UrlInputDialog) {
            Timber.d("UrlInputDialog detached, finishing activity")
            supportFragmentManager.unregisterFragmentLifecycleCallbacks(this)
            if (!isFinishing) {
              finish()
            }
          }
        }
      },
      false
    )
  }

  override fun onDestroy() {
    super.onDestroy()
    Timber.d("ShareReceiverActivity destroyed")
  }
}
