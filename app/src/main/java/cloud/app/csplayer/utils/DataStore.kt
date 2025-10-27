package cloud.app.csplayer.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import cloud.app.csplayer.utils.Utils.logError
import cloud.app.csplayer.datastore.Serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

const val PREFERENCES_NAME = "rebuild_preference"
const val USER_PROVIDER_API = "user_custom_sites"

/** When inserting many keys use this function, this is because apply for every key is very expensive on memory */
data class Editor(
    val editor : SharedPreferences.Editor
) {
    /** Always remember to call apply after */
    fun<T> setKeyRaw(path: String, value: T) {
        when (value) {
            is Boolean -> editor.putBoolean(path, value)
            is Int -> editor.putInt(path, value)
            is String -> editor.putString(path, value)
            is Float -> editor.putFloat(path, value)
            is Long -> editor.putLong(path, value)
            (value as? Set<String> != null) -> editor.putStringSet(path, value as Set<String>)
        }
    }

    fun apply() {
        editor.apply()
        System.gc()
    }
}

object DataStore {
    val mapper: Json = Serializer.json

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    fun Context.getSharedPrefs(): SharedPreferences {
        return getPreferences(this)
    }

    fun getFolderName(folder: String, path: String): String {
        return "${folder}/${path}"
    }

    fun editor(context : Context, isEditingAppSettings: Boolean = false) : Editor {
        val editor: SharedPreferences.Editor =
            if (isEditingAppSettings) context.getDefaultSharedPrefs().edit() else context.getSharedPrefs().edit()
        return Editor(editor)
    }

    fun Context.getDefaultSharedPrefs(): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(this)
    }

    fun Context.getKeys(folder: String): List<String> {
        return this.getSharedPrefs().all.keys.filter { it.startsWith(folder) }
    }

    fun Context.removeKey(folder: String, path: String) {
        removeKey(getFolderName(folder, path))
    }

    fun Context.containsKey(folder: String, path: String): Boolean {
        return containsKey(getFolderName(folder, path))
    }

    fun Context.containsKey(path: String): Boolean {
        val prefs = getSharedPrefs()
        return prefs.contains(path)
    }

    fun Context.removeKey(path: String) {
        try {
            val prefs = getSharedPrefs()
            if (prefs.contains(path)) {
                val editor: SharedPreferences.Editor = prefs.edit()
                editor.remove(path)
                editor.apply()
            }
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun Context.removeKeys(folder: String): Int {
        val keys = getKeys("$folder/")
        keys.forEach { value ->
            removeKey(value)
        }
        return keys.size
    }

    fun <T> Context.setKey(path: String, value: T) {
        try {
            val editor: SharedPreferences.Editor = getSharedPrefs().edit()
            @Suppress("UNCHECKED_CAST")
            val json = mapper.encodeToString(kotlinx.serialization.serializer(value!!::class.java) as kotlinx.serialization.KSerializer<T>, value)
            editor.putString(path, json)
            editor.apply()
        } catch (e: Exception) {
            logError(e)
        }
    }

    fun <T> Context.getKey(path: String, valueType: Class<T>): T? {
        try {
            val json: String = getSharedPrefs().getString(path, null) ?: return null
            return json.toKotlinObject(valueType)
        } catch (e: Exception) {
            return null
        }
    }

    fun <T> Context.setKey(folder: String, path: String, value: T) {
        setKey(getFolderName(folder, path), value)
    }

    inline fun <reified T : Any> String.toKotlinObject(): T {
        return mapper.decodeFromString<T>(this)
    }

    fun <T> String.toKotlinObject(valueType: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return mapper.decodeFromString(kotlinx.serialization.serializer(valueType) as kotlinx.serialization.KSerializer<T>, this)
    }

    // GET KEY GIVEN PATH AND DEFAULT VALUE, NULL IF ERROR
    inline fun <reified T : Any> Context.getKey(path: String, defVal: T?): T? {
        try {
            val json: String = getSharedPrefs().getString(path, null) ?: return defVal
            return json.toKotlinObject()
        } catch (e: Exception) {
            return null
        }
    }

    inline fun <reified T : Any> Context.getKey(path: String): T? {
        return getKey(path, null)
    }

    inline fun <reified T : Any> Context.getKey(folder: String, path: String): T? {
        return getKey(getFolderName(folder, path), null)
    }

    inline fun <reified T : Any> Context.getKey(folder: String, path: String, defVal: T?): T? {
        return getKey(getFolderName(folder, path), defVal) ?: defVal
    }


  private const val FILE_DELETE_KEY = "FILES_TO_DELETE_KEY"

  /**
   * Transient files to delete on application exit.
   * Deletes files on onDestroy().
   */
  private var Context.filesToDelete: Set<String>
    // This needs to be persistent because the application may exit without calling onDestroy.
    get() = getKey<Set<String>>(FILE_DELETE_KEY) ?: setOf()
    private set(value) = setKey(FILE_DELETE_KEY, value)

  /**
   * Add file to delete on Exit.
   */
  fun Context.deleteFileOnExit(file: File) {
    filesToDelete = filesToDelete + file.path
  }


  class UserPreferenceDelegate<T : Any>(
    private val key: String, private val default: T //, private val klass: KClass<T>
  ) {
    private val klass: KClass<out T> = default::class
    private val realKey get() = "$$key"
    operator fun getValue(self: Any?, property: KProperty<*>) =
      Utils.activity?.getKey(realKey, klass.java) ?: default


    operator fun setValue(
      self: Any?,
      property: KProperty<*>,
      t: T?
    ) {
      if (t == null) {
        Utils.activity?.removeKey(realKey)
      } else {
        Utils.activity?.setKey(realKey,t)
      }
    }
  }

  var playBackSpeed : Float by UserPreferenceDelegate("playback_speed", 1.0f)
  var resizeMode : Int by UserPreferenceDelegate("resize_mode", 0)


  const val VIDEO_POS_DUR = "video_pos_dur"
  @kotlinx.serialization.Serializable
  data class PosDur(
    @kotlinx.serialization.SerialName("position") val position: Long,
    @kotlinx.serialization.SerialName("duration") val duration: Long
  )

  fun PosDur.fixVisual(): PosDur {
    if (duration <= 0) return PosDur(0, duration)
    val percentage = position * 100 / duration
    if (percentage <= 1) return PosDur(0, duration)
    if (percentage <= 5) return PosDur(5 * duration / 100, duration)
    if (percentage >= 95) return PosDur(duration, duration)
    return this
  }
  fun setViewPos(id: Int?, pos: Long, dur: Long) {
    if (id == null) return
    if (dur < 30_000) return // too short
    Utils.activity?.setKey("$VIDEO_POS_DUR", id.toString(), PosDur(pos, dur))
  }

  fun getViewPos(id: Int?): PosDur? {
    if (id == null) return null
    return Utils.activity?.getKey("$VIDEO_POS_DUR", id.toString(), null)
  }
}
