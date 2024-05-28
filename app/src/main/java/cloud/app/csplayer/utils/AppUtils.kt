package cloud.app.csplayer.utils

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Activity.RESULT_CANCELED
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.*
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.tv.TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.text.Spanned
import android.util.Log
import android.view.View
import android.view.View.LAYOUT_DIRECTION_LTR
import android.view.View.LAYOUT_DIRECTION_RTL
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.text.toSpanned
import androidx.core.widget.ContentLoadingProgressBar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cloud.app.csplayer.utils.Utils.normalSafeApiCall
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Cache
import java.io.*
import java.net.URL
import java.net.URLDecoder

object AppUtils {
    fun RecyclerView.setMaxViewPoolSize(maxViewTypeId: Int, maxPoolSize: Int) {
        for (i in 0..maxViewTypeId)
            recycledViewPool.setMaxRecycledViews(i, maxPoolSize)
    }

    fun RecyclerView.isRecyclerScrollable(): Boolean {
        val layoutManager =
            this.layoutManager as? LinearLayoutManager?
        val adapter = adapter
        return if (layoutManager == null || adapter == null) false else layoutManager.findLastCompletelyVisibleItemPosition() < adapter.itemCount - 7 // bit more than 1 to make it more seamless
    }

    fun View.isLtr() = this.layoutDirection == LAYOUT_DIRECTION_LTR
    fun View.isRtl() = this.layoutDirection == LAYOUT_DIRECTION_RTL

    @SuppressLint("Range")
    fun getVideoContentUri(context: Context, videoFilePath: String): Uri? {
        val cursor = context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Video.Media._ID),
            MediaStore.Video.Media.DATA + "=? ", arrayOf(videoFilePath), null
        )
        return if (cursor != null && cursor.moveToFirst()) {
            val id = cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID))
            cursor.close()
            Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "" + id)
        } else {
            val values = ContentValues()
            values.put(MediaStore.Video.Media.DATA, videoFilePath)
            context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
            )
        }
    }

    abstract class DiffAdapter<T>(
        open val items: MutableList<T>,
        val comparison: (first: T, second: T) -> Boolean = { first, second ->
            first.hashCode() == second.hashCode()
        }
    ) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount(): Int {
            return items.size
        }

        fun updateList(newList: List<T>) {
            val diffResult = DiffUtil.calculateDiff(
                GenericDiffCallback(this.items, newList)
            )

            items.clear()
            items.addAll(newList)

            diffResult.dispatchUpdatesTo(this)
        }

        inner class GenericDiffCallback(
            private val oldList: List<T>,
            private val newList: List<T>
        ) :
            DiffUtil.Callback() {
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                comparison(oldList[oldItemPosition], newList[newItemPosition])

            override fun getOldListSize() = oldList.size

            override fun getNewListSize() = newList.size

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                oldList[oldItemPosition] == newList[newItemPosition]
        }
    }


    private fun Context.hasWebView(): Boolean {
        return this.packageManager.hasSystemFeature("android.software.webview")
    }


    fun Context.isNetworkAvailable(): Boolean {
        val manager = this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetworkInfo = manager.activeNetworkInfo
        return activeNetworkInfo != null && activeNetworkInfo.isConnected || manager.allNetworkInfo?.any { it.isConnected } ?: false
    }

    fun splitQuery(url: URL): Map<String, String> {
        val queryPairs: MutableMap<String, String> = LinkedHashMap()
        val query: String = url.query
        val pairs = query.split("&").toTypedArray()
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            queryPairs[URLDecoder.decode(pair.substring(0, idx), "UTF-8")] =
                URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
        }
        return queryPairs
    }

    fun Context.isCastApiAvailable(): Boolean {
        val isCastApiAvailable =
            GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(applicationContext) == ConnectionResult.SUCCESS
        try {
            applicationContext?.let { CastContext.getSharedInstance(it) }
        } catch (e: Exception) {
            println(e)
            // track non-fatal
            return false
        }
        return isCastApiAvailable
    }

    fun Context.isConnectedToChromecast(): Boolean {
        if (isCastApiAvailable()) {
            val castContext = CastContext.getSharedInstance(this)
            if (castContext.castState == CastState.CONNECTED) {
                return true
            }
        }
        return false
    }
  fun Context.getCastContext(): CastContext? {
    if (isCastApiAvailable()) {
      val castContext = CastContext.getSharedInstance(this)
      if (castContext.castState == CastState.CONNECTED) {
        return castContext;
      }
    }
    return null
  }

}
