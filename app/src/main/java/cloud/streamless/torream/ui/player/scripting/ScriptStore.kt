package cloud.streamless.torream.ui.player.scripting

import android.content.Context
import java.io.File

/**
 * Manages the on-disk collection of user Lua scripts loaded into mpv (see [ScriptStore.DISABLED_SUFFIX]
 * for how enable/disable is represented). Scripts live under `filesDir/scripts` since that's already
 * mpv's config-dir for this app (see BaseMPVView.initialize).
 */
object ScriptStore {

  const val DISABLED_SUFFIX = ".disabled"
  private const val EXTENSION = ".lua"
  private val NAME_PATTERN = Regex("[^A-Za-z0-9_-]")

  data class LuaScript(val file: File, val name: String, val enabled: Boolean)

  fun scriptsDir(context: Context): File = File(context.filesDir, "scripts")

  fun list(scriptsDir: File): List<LuaScript> {
    val files = scriptsDir.listFiles() ?: return emptyList()
    return files
      .filter { it.isFile && (it.name.endsWith(EXTENSION) || it.name.endsWith(EXTENSION + DISABLED_SUFFIX)) }
      .map { it.toLuaScript() }
      .sortedBy { it.name.lowercase() }
  }

  fun list(context: Context): List<LuaScript> = list(scriptsDir(context))

  // Commented out so a script left untouched is still a no-op when loaded into mpv.
  private fun template(name: String) = """
    |-- $name — mpv Lua script (see https://mpv.io/manual/stable/#lua-scripting)
    |
    |-- mp.add_key_binding("ctrl+n", "my_binding_name", function()
    |--   mp.osd_message("Hello from Lua!")
    |-- end)
    |
    |-- mp.observe_property("pause", "bool", function(name, value)
    |--   mp.msg.info("pause changed: " .. tostring(value))
    |-- end)
  """.trimMargin()

  /** Sanitizes [rawName] to a safe basename, dedupes against existing files, and creates a starter script. */
  fun create(scriptsDir: File, rawName: String): Result<File> {
    val sanitized = sanitize(rawName)
    if (sanitized.isEmpty()) return Result.failure(IllegalArgumentException("Invalid script name"))

    scriptsDir.mkdirs()
    var candidate = sanitized
    var suffix = 1
    while (File(scriptsDir, "$candidate$EXTENSION").exists() ||
      File(scriptsDir, "$candidate$EXTENSION$DISABLED_SUFFIX").exists()
    ) {
      candidate = "$sanitized ($suffix)"
      suffix++
    }

    val file = File(scriptsDir, "$candidate$EXTENSION")
    file.writeText(template(candidate))
    return Result.success(file)
  }

  fun create(context: Context, rawName: String): Result<File> = create(scriptsDir(context), rawName)

  fun delete(file: File) {
    file.delete()
  }

  /** Renames between the `.lua` (enabled) and `.lua.disabled` (disabled) extension. Returns the new file. */
  fun setEnabled(file: File, enabled: Boolean): File {
    val target = if (enabled) {
      File(file.parentFile, file.name.removeSuffix(DISABLED_SUFFIX))
    } else {
      if (file.name.endsWith(DISABLED_SUFFIX)) return file
      File(file.parentFile, file.name + DISABLED_SUFFIX)
    }
    if (target == file) return file
    file.renameTo(target)
    return target
  }

  fun read(file: File): String = if (file.exists()) file.readText() else ""

  fun write(file: File, content: String) {
    file.parentFile?.mkdirs()
    file.writeText(content)
  }

  private fun sanitize(rawName: String): String {
    val base = File(rawName.trim()).name // strip any path separators
      .removeSuffix(EXTENSION)
    return NAME_PATTERN.replace(base, "_").trim('_')
  }

  private fun File.toLuaScript(): LuaScript {
    val enabled = !name.endsWith(DISABLED_SUFFIX)
    val displayName = name.removeSuffix(DISABLED_SUFFIX).removeSuffix(EXTENSION)
    return LuaScript(this, displayName, enabled)
  }
}
