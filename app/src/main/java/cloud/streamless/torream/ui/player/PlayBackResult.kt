package cloud.streamless.torream.ui.player

data class PlayBackResult(val code: Int, val position: Long, val reason: String? = null, val episode: Int? = null)
