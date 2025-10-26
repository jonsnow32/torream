package cloud.app.csplayer.utils

import android.view.View
import android.widget.TextView
import java.util.Locale
import java.util.concurrent.TimeUnit

// Formats milliseconds to a fixed-width hh:mm:ss string (always shows hours)
fun formatDurationMs(durationMs: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
}

// Extension to set duration on a TextView (hides when null/zero/negative)
@Suppress("unused")
fun TextView.setDurationMs(durationMs: Long?) {
    if (durationMs == null || durationMs <= 0L) {
        this.visibility = View.GONE
    } else {
        this.text = formatDurationMs(durationMs)
        this.visibility = View.VISIBLE
    }
}

// Usage example (comment):
// In your adapter's onBindViewHolder:
// val durationMs = video.durationMs // Long? in milliseconds
// holder.itemView.findViewById<TextView>(R.id.tvDuration).setDurationMs(durationMs)
