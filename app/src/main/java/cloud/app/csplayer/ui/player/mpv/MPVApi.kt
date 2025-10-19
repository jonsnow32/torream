package cloud.app.csplayer.ui.player.mpv

// Safe wrappers around MPVLib that avoid calling native functions when MPV isn't initialized
object MPVApi {
    fun command(cmd: Array<String>) {
        if (!MPVState.isInitialized()) return
        try {
            MPVLib.command(cmd)
        } catch (_: Throwable) {
            // ignore
        }
    }

    fun setPropertyString(property: String, value: String) {
        if (!MPVState.isInitialized()) return
        try {
            MPVLib.setPropertyString(property, value)
        } catch (_: Throwable) {
            // ignore
        }
    }

    fun getPropertyInt(property: String): Int? {
        if (!MPVState.isInitialized()) return null
        return try {
            MPVLib.getPropertyInt(property)
        } catch (_: Throwable) {
            null
        }
    }

    fun getPropertyString(property: String): String? {
        if (!MPVState.isInitialized()) return null
        return try {
            MPVLib.getPropertyString(property)
        } catch (_: Throwable) {
            null
        }
    }

    // Add other wrappers as needed
    fun setOptionString(name: String, value: String) {
        if (!MPVState.isInitialized()) return
        try {
            MPVLib.setOptionString(name, value)
        } catch (_: Throwable) {
        }
    }

    fun setPropertyInt(property: String, value: Int) {
        if (!MPVState.isInitialized()) return
        try {
            MPVLib.setPropertyInt(property, value)
        } catch (_: Throwable) {
        }
    }

    fun attachSurface(surface: android.view.Surface) {
        if (!MPVState.isInitialized()) return
        try {
            MPVLib.attachSurface(surface)
        } catch (_: Throwable) {
        }
    }

    fun detachSurface() {
        if (!MPVState.isInitialized()) return
        try {
            MPVLib.detachSurface()
        } catch (_: Throwable) {
        }
    }

    fun getPropertyBoolean(property: String): Boolean? {
        if (!MPVState.isInitialized()) return null
        return try {
            MPVLib.getPropertyBoolean(property)
        } catch (_: Throwable) {
            null
        }
    }

    fun getPropertyDouble(property: String): Double? {
        if (!MPVState.isInitialized()) return null
        return try {
            MPVLib.getPropertyDouble(property)
        } catch (_: Throwable) {
            null
        }
    }
}
