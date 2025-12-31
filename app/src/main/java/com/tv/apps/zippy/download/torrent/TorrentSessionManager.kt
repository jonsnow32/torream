package com.tv.apps.zippy.download.torrent

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TorrentSessionManager - Singleton session manager based on LibreTorrent architecture
 * Reference: https://github.com/proninyaroslav/libretorrent
 *
 * Key improvements:
 * - Single session instance shared across all downloads
 * - Proper session lifecycle management
 * - Optimized settings for mobile networks
 * - Port mapping (UPnP/NAT-PMP) for incoming connections
 */
@Singleton
class TorrentSessionManager @Inject constructor(
  @ApplicationContext private val context: Context
) {

  private var session: SessionManager? = null
  private val mutex = Mutex()
  private val listeners = mutableListOf<TorrentSessionListener>()

  companion object {
    // Public trackers list (same as LibreTorrent)
    val PUBLIC_TRACKERS = listOf(
      "udp://tracker.opentrackr.org:1337/announce",
      "udp://open.stealth.si:80/announce",
      "udp://tracker.torrent.eu.org:451/announce",
      "udp://tracker.moeking.me:6969/announce",
      "udp://exodus.desync.com:6969/announce",
      "udp://tracker.tiny-vps.com:6969/announce",
      "udp://opentracker.i2p.rocks:6969/announce",
      "http://tracker.openbittorrent.com:80/announce",
      "udp://tracker.openbittorrent.com:6969/announce",
      "udp://tracker.coppersurfer.tk:6969/announce",
      "udp://tracker.leechers-paradise.org:6969/announce",
      "udp://9.rarbg.to:2710/announce",
      "udp://9.rarbg.me:2710/announce"
    )
  }

  /**
   * Initialize and start the torrent session if not already started
   */
  suspend fun start() = mutex.withLock {
    if (session != null && session?.isRunning == true) {
      Timber.d("Session already running")
      return
    }

    Timber.i("Starting TorrentSessionManager...")

    val sessionDir = File(context.filesDir, "libtorrent_session")
    if (!sessionDir.exists()) {
      sessionDir.mkdirs()
    }

    val settingsPack = createOptimizedSettings()
    val params = SessionParams(settingsPack)

    session = SessionManager(false).apply {
      start(params)

      // Add alert listener for monitoring
      addListener(TorrentAlertListener())

      // Start DHT for magnet link support
      startDht()
    }

    Timber.i("✅ TorrentSessionManager started with optimized settings")
  }

  /**
   * Stop the session
   */
  suspend fun stop() = mutex.withLock {
    session?.let { s ->
      try {
        Timber.i("Stopping TorrentSessionManager...")
        s.stop()
        session = null
        Timber.i("✅ TorrentSessionManager stopped")
      } catch (e: Exception) {
        Timber.e(e, "Error stopping session")
      }
    }
  }

  /**
   * Get the current session instance
   */
  suspend fun getSession(): SessionManager? = mutex.withLock {
    if (session == null || session?.isRunning == false) {
      start()
    }
    session
  }

  /**
   * Find torrent handle by info hash
   */
  suspend fun findTorrent(infoHash: org.libtorrent4j.Sha1Hash): TorrentHandle? = withContext(Dispatchers.IO) {
    try {
      session?.find(infoHash)
    } catch (e: Exception) {
      Timber.w(e, "Error finding torrent")
      null
    }
  }

  /**
   * Add listener for session events
   */
  fun addListener(listener: TorrentSessionListener) {
    listeners.add(listener)
  }

  /**
   * Remove listener
   */
  fun removeListener(listener: TorrentSessionListener) {
    listeners.remove(listener)
  }

  /**
   * Create optimized settings based on LibreTorrent
   */
  private fun createOptimizedSettings(): SettingsPack {
    val settings = SettingsPack()

    // Connection limits (optimized for mobile)
    settings.connectionsLimit(200)
    settings.activeDownloads(4)
    settings.activeSeeds(5)
    settings.activeLimit(15)

    // Listen interfaces - IPv4 primary, IPv6 secondary
    settings.setString(
      org.libtorrent4j.swig.settings_pack.string_types.listen_interfaces.swigValue(),
      "0.0.0.0:6881,[::]:6881"
    )

    // Enable DHT for magnet links and peer discovery
    settings.setInteger(
      org.libtorrent4j.swig.settings_pack.bool_types.enable_dht.swigValue(),
      1
    )

    // Enable LSD (Local Service Discovery)
    settings.setInteger(
      org.libtorrent4j.swig.settings_pack.bool_types.enable_lsd.swigValue(),
      1
    )

    // Enable incoming connections
    settings.setInteger(
      org.libtorrent4j.swig.settings_pack.bool_types.enable_incoming_tcp.swigValue(),
      1
    )
    settings.setInteger(
      org.libtorrent4j.swig.settings_pack.bool_types.enable_incoming_utp.swigValue(),
      1
    )

    // Enable outgoing connections
    settings.setInteger(
      org.libtorrent4j.swig.settings_pack.bool_types.enable_outgoing_tcp.swigValue(),
      1
    )
    settings.setInteger(
      org.libtorrent4j.swig.settings_pack.bool_types.enable_outgoing_utp.swigValue(),
      1
    )

    // CRITICAL: Enable UPnP and NAT-PMP for port mapping
    // This allows incoming connections through NAT/firewall
    settings.setInteger(
      org.libtorrent4j.swig.settings_pack.bool_types.enable_upnp.swigValue(),
      1
    )
    settings.setInteger(
      org.libtorrent4j.swig.settings_pack.bool_types.enable_natpmp.swigValue(),
      1
    )

    // Disable anonymous mode to allow peer exchange
    settings.setInteger(
      org.libtorrent4j.swig.settings_pack.bool_types.anonymous_mode.swigValue(),
      0
    )

    // Allow seeding while downloading
    settings.setInteger(
      org.libtorrent4j.swig.settings_pack.bool_types.seeding_outgoing_connections.swigValue(),
      1
    )

    // Rate limits (0 = unlimited)
    settings.setInteger(
      org.libtorrent4j.swig.settings_pack.int_types.download_rate_limit.swigValue(),
      0
    )
    settings.setInteger(
      org.libtorrent4j.swig.settings_pack.int_types.upload_rate_limit.swigValue(),
      0
    )

    // Announce to all trackers
    settings.setInteger(
      org.libtorrent4j.swig.settings_pack.bool_types.announce_to_all_trackers.swigValue(),
      1
    )

    // Announce to all tiers
    settings.setInteger(
      org.libtorrent4j.swig.settings_pack.bool_types.announce_to_all_tiers.swigValue(),
      1
    )

    // Auto-manage torrents
    settings.setInteger(
      org.libtorrent4j.swig.settings_pack.bool_types.auto_manage_prefer_seeds.swigValue(),
      1
    )

    // Prefer encryption
    settings.setInteger(
      org.libtorrent4j.swig.settings_pack.int_types.out_enc_policy.swigValue(),
      org.libtorrent4j.swig.settings_pack.enc_policy.pe_enabled.swigValue()
    )
    settings.setInteger(
      org.libtorrent4j.swig.settings_pack.int_types.in_enc_policy.swigValue(),
      org.libtorrent4j.swig.settings_pack.enc_policy.pe_enabled.swigValue()
    )

    // Connection settings
    settings.setInteger(
      org.libtorrent4j.swig.settings_pack.int_types.connection_speed.swigValue(),
      500  // Fast connection establishment
    )

    // Piece timeout
    settings.setInteger(
      org.libtorrent4j.swig.settings_pack.int_types.piece_timeout.swigValue(),
      20  // 20 seconds
    )

    return settings
  }

  /**
   * Alert listener for monitoring session events
   */
  private inner class TorrentAlertListener : org.libtorrent4j.AlertListener {
    override fun types(): IntArray {
      return intArrayOf(
        AlertType.PORTMAP.swig(),
        AlertType.PORTMAP_ERROR.swig(),
        AlertType.PORTMAP_LOG.swig(),
        AlertType.DHT_BOOTSTRAP.swig(),
        AlertType.DHT_ERROR.swig(),
        AlertType.TRACKER_REPLY.swig(),
        AlertType.TRACKER_ERROR.swig(),
        AlertType.TRACKER_WARNING.swig(),
        AlertType.METADATA_RECEIVED.swig(),
        AlertType.STATE_CHANGED.swig(),
        AlertType.TORRENT_ERROR.swig(),
        AlertType.TORRENT_FINISHED.swig()
      )
    }

    override fun alert(alert: Alert<*>) {
      when (alert.type()) {
        AlertType.PORTMAP -> {
          Timber.i("🔓 Port mapped: ${alert.message()}")
        }
        AlertType.PORTMAP_ERROR -> {
          Timber.w("🔒 Port mapping error: ${alert.message()}")
        }
        AlertType.DHT_BOOTSTRAP -> {
          Timber.i("🌐 DHT bootstrapped: ${alert.message()}")
        }
        AlertType.DHT_ERROR -> {
          Timber.w("🌐 DHT error: ${alert.message()}")
        }
        AlertType.TRACKER_REPLY -> {
          Timber.d("📡 Tracker reply: ${alert.message()}")
        }
        AlertType.TRACKER_ERROR -> {
          Timber.w("📡 Tracker error: ${alert.message()}")
        }
        AlertType.METADATA_RECEIVED -> {
          Timber.i("📦 Metadata received: ${alert.message()}")
        }
        AlertType.TORRENT_FINISHED -> {
          Timber.i("✅ Torrent finished: ${alert.message()}")
        }
        AlertType.TORRENT_ERROR -> {
          Timber.e("❌ Torrent error: ${alert.message()}")
        }
        else -> {
          // Other alerts
        }
      }

      // Notify listeners
      listeners.forEach { listener ->
        try {
          listener.onAlert(alert)
        } catch (e: Exception) {
          Timber.w(e, "Error notifying listener")
        }
      }
    }
  }
}

/**
 * Listener interface for session events
 */
interface TorrentSessionListener {
  fun onAlert(alert: Alert<*>)
}

