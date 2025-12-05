package cloud.app.csplayer.utils

import android.annotation.SuppressLint
import android.content.*
import android.net.ConnectivityManager
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.view.View
import android.view.View.LAYOUT_DIRECTION_LTR
import android.view.View.LAYOUT_DIRECTION_RTL
import androidx.core.net.toUri
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cloud.app.csplayer.R
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import java.io.*
import java.net.URL
import java.net.URLDecoder
import kotlin.text.compareTo
import kotlin.text.insert

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

  fun Context.getDefaultPath(): UnifiedFile? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      // API 29+: Use app-specific downloads directory
      return UnifiedFileFactory.getDownloadsDirectory(this)
    } else {
      // API 28 and below: Use public downloads directory
      return UnifiedFileFactory.getDownloadsDirectoryPublic(this)
    }
  }

  fun Context.getDownloadPath(): UnifiedFile? {
    val settingsManager = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
    val path = settingsManager.getString(
      this.getString(R.string.download_path_key),
      null
    )
    return if (path.isNullOrEmpty()) {
      getDefaultPath()
    } else {
      UnifiedFileFactory.fromUri(this, path.toUri())
    }
  }
}
