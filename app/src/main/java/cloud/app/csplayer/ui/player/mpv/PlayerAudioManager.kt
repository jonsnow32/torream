package cloud.app.csplayer.ui.player.mpv

import android.content.Context
import android.media.AudioManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import timber.log.Timber

/**
 * Manages audio focus and media session for the player
 */
@Suppress("unused")
class PlayerAudioManager(
    private val context: Context,
    private val onAudioFocusLost: () -> Unit,
    private val onAudioFocusGained: () -> Unit,
    private val onDuckAudio: (Float) -> Unit = {},
    private val onRestoreAudio: (Float) -> Unit = {},
    private val getIgnoreAudioFocus: () -> Boolean = { false }
) {
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequestCompat? = null
    private var audioFocusRestore: () -> Unit = {}
    private var mediaSession: MediaSessionCompat? = null

    companion object {
        private const val TAG = "PlayerAudioManager"
        private const val STREAM_TYPE = AudioManager.STREAM_MUSIC
        private const val AUDIO_FOCUS_DUCKING = 0.5f
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { type ->
        Timber.tag(TAG).v("Audio focus changed: $type")
        if (getIgnoreAudioFocus()) {
            return@OnAudioFocusChangeListener
        }
        when (type) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // loss can occur in addition to ducking, so remember the old callback
                val oldRestore = audioFocusRestore
                audioFocusRestore = {
                    oldRestore()
                    onAudioFocusGained()
                }
                onAudioFocusLost()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                onDuckAudio(AUDIO_FOCUS_DUCKING)
                audioFocusRestore = {
                    onRestoreAudio(1f / AUDIO_FOCUS_DUCKING)
                }
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                audioFocusRestore()
                audioFocusRestore = {}
            }
        }
    }

    fun initialize(): Boolean {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        val req = with(AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)) {
            setAudioAttributes(with(AudioAttributesCompat.Builder()) {
                setUsage(AudioAttributesCompat.USAGE_MEDIA)
                setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                build()
            })
            setOnAudioFocusChangeListener(audioFocusChangeListener)
            build()
        }

        val res = audioManager?.let { AudioManagerCompat.requestAudioFocus(it, req) }
        return if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            audioFocusRequest = req
            true
        } else {
            Timber.tag(TAG).v("Audio focus not granted")
            false
        }
    }

    fun initMediaSession(packageName: String): MediaSessionCompat {
        val session = MediaSessionCompat(context, packageName)
        mediaSession = session
        return session
    }

    fun updateMediaSession(
        title: String?,
        artist: String?,
        durationMs: Long?,
        positionMs: Long?,
        isPlaying: Boolean
    ) {
        mediaSession?.apply {
            val metadataBuilder = android.support.v4.media.MediaMetadataCompat.Builder()
            title?.let { metadataBuilder.putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, it) }
            artist?.let { metadataBuilder.putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, it) }
            durationMs?.let { metadataBuilder.putLong(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION, it) }

            setMetadata(metadataBuilder.build())

            val stateBuilder = PlaybackStateCompat.Builder()
                .setState(
                    if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    positionMs ?: 0,
                    1.0f
                )
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_PLAY_PAUSE or
                            PlaybackStateCompat.ACTION_SEEK_TO or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )

            setPlaybackState(stateBuilder.build())
        }
    }

    fun getVolume(): Int? = audioManager?.getStreamVolume(STREAM_TYPE)

    fun getMaxVolume(): Int? = audioManager?.getStreamMaxVolume(STREAM_TYPE)

    fun setVolume(volume: Int) {
        audioManager?.setStreamVolume(STREAM_TYPE, volume, 0)
    }

    fun adjustVolume(direction: Int) {
        audioManager?.adjustStreamVolume(STREAM_TYPE, direction, 0)
    }

    fun setRepeatMode(repeatMode: Int) {
        mediaSession?.setRepeatMode(repeatMode)
    }

    fun setShuffleMode(shuffleMode: Int) {
        mediaSession?.setShuffleMode(shuffleMode)
    }

    fun release() {
        audioFocusRequest?.let { request ->
            audioManager?.let { AudioManagerCompat.abandonAudioFocusRequest(it, request) }
        }
        mediaSession?.release()
        audioFocusRequest = null
        mediaSession = null
        audioManager = null
    }
}


