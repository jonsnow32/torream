package cloud.streamless.torream.utils

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.IntentSanitizer
import cloud.streamless.torream.R
import cloud.streamless.torream.utils.Coroutines.main
import cloud.streamless.torream.utils.Utils.logError
import java.io.InputStream

const val INSTALL_ACTION = "ApkInstaller.INSTALL_ACTION"


class ApkInstaller(private val service: PackageInstallerService) {

    companion object {
        /**
         * Used for postponed installations
         **/
        var delayedInstaller: DelayedInstaller? = null
    }

    inner class DelayedInstaller(
        private val session: PackageInstaller.Session,
        private val intent: IntentSender
    ) {
        fun startInstallation(): Boolean {
            return try {
                session.commit(intent)
                true
            } catch (e: Exception) {
                false
            }.also { delayedInstaller = null }
        }
    }

    private val packageInstaller = service.packageManager.packageInstaller

    enum class InstallProgressStatus {
        Preparing,
        Downloading,
        Installing,
        Failed,
    }

    private val installActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE
            )) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    // Fix for Intent Redirection vulnerability using IntentSanitizer
                    val userAction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    }

                    // Sanitize and validate the intent before launching
                    userAction?.let { action ->
                        // Validate the intent first
                        if (!isValidPackageInstallerIntent(action)) {
                            logError(SecurityException("Rejected untrusted intent redirection attempt"))
                            return
                        }

                        try {
                            // Use IntentSanitizer to ensure the intent is safe
                            val sanitizedIntent = IntentSanitizer.Builder()
                                .allowAction(Intent.ACTION_VIEW)
                                .allowData { true } // Allow any data URI for package installer
                                .allowType { true } // Allow any MIME type
                                .allowFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                .allowFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                .allowComponent { component ->
                                    // Only allow system package installers
                                    component.packageName == "com.android.packageinstaller" ||
                                    component.packageName == "com.google.android.packageinstaller" ||
                                    component.packageName == "com.android.settings"
                                }
                                .build()
                                .sanitizeByFiltering(action)

                            sanitizedIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(sanitizedIntent)
                        } catch (e: Exception) {
                            logError(e)
                            // If sanitization fails, create a new safe intent with only the data
                            // Create a new safe intent instead of using the untrusted one
                            val safeIntent = Intent(Intent.ACTION_VIEW).apply {
                                data = action.data
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                            }
                            try {
                                context.startActivity(safeIntent)
                            } catch (ex: Exception) {
                                logError(ex)
                            }
                        }
                    }
                }
            }
        }

        /**
         * Validates that the intent is a legitimate system PackageInstaller intent
         * to prevent Intent Redirection attacks
         */
        private fun isValidPackageInstallerIntent(intent: Intent): Boolean {
            // Only allow intents with no component set (system will resolve it)
            // or intents explicitly targeting the system package installer
            val component = intent.component
            val isSystemComponent = component == null ||
                   component.packageName == "com.android.packageinstaller" ||
                   component.packageName == "com.google.android.packageinstaller" ||
                   component.packageName == "com.android.settings"

            // Also validate the action is safe
            val hasSafeAction = intent.action == Intent.ACTION_VIEW || intent.action == null

            return isSystemComponent && hasSafeAction
        }
    }

    fun installApk(
        context: Context,
        inputStream: InputStream,
        size: Long,
        installProgress: (bytesRead: Int) -> Unit,
        installProgressStatus: (InstallProgressStatus) -> Unit
    ) {
        installProgressStatus.invoke(InstallProgressStatus.Preparing)
        var activeSession: Int? = null

        try {
            val installParams =
                PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                installParams.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }

            activeSession = packageInstaller.createSession(installParams)
            installParams.setSize(size)

            val session = packageInstaller.openSession(activeSession)
            installProgressStatus.invoke(InstallProgressStatus.Downloading)

            session.openWrite(context.packageName, 0, size)
                .use { outputStream ->
                    val buffer = ByteArray(4 * 1024)
                    var bytesRead = inputStream.read(buffer)

                    while (bytesRead >= 0) {
                        outputStream.write(buffer, 0, bytesRead)
                        bytesRead = inputStream.read(buffer)
                        installProgress.invoke(bytesRead)
                    }

                    session.fsync(outputStream)
                    inputStream.close()
                }


            val intentSender = PendingIntent.getBroadcast(
                service,
                activeSession,
                Intent(INSTALL_ACTION),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0,
            ).intentSender

            // Use delayed installations on android 13 and only if "allow from unknown sources" is enabled
            // if the app lacks installation permission it cannot ask for the permission when it's closed.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                context.packageManager.canRequestPackageInstalls()
            ) {
                // Save for later installation since it's more jarring to have the app exit abruptly
                delayedInstaller = DelayedInstaller(session, intentSender)
                main {
                    // Use real toast since it should show even if app is exited
                    Toast.makeText(context, R.string.delayed_update_notice, Toast.LENGTH_LONG)
                        .show()
                }
            } else {
                installProgressStatus.invoke(InstallProgressStatus.Installing)
                session.commit(intentSender)
            }
        } catch (e: Exception) {
            logError(e)

            service.unregisterReceiver(installActionReceiver)
            installProgressStatus.invoke(InstallProgressStatus.Failed)

            activeSession?.let { sessionId ->
                packageInstaller.abandonSession(sessionId)
            }
        }
    }

    init {
        // Register receiver with proper export flag using ContextCompat for all Android versions
        ContextCompat.registerReceiver(
            service,
            installActionReceiver,
            IntentFilter(INSTALL_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        service.receivers.add(installActionReceiver)
    }
}
