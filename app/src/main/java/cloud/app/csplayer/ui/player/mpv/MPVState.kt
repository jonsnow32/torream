package cloud.app.csplayer.ui.player.mpv

import java.util.concurrent.atomic.AtomicBoolean

object MPVState {
    private val initialized = AtomicBoolean(false)

    fun setInitialized(value: Boolean) {
        initialized.set(value)
    }

    fun isInitialized(): Boolean = initialized.get()
}

