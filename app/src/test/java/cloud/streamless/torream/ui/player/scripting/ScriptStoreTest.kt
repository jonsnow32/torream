package cloud.streamless.torream.ui.player.scripting

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class ScriptStoreTest {

  private lateinit var dir: File

  @Before
  fun setUp() {
    dir = File.createTempFile("scripts", "").apply {
      delete()
      mkdirs()
    }
  }

  @After
  fun tearDown() {
    dir.deleteRecursively()
  }

  @Test
  fun `create sanitizes unsafe characters and path traversal attempts`() {
    val file = ScriptStore.create(dir, "../../etc/my script!.lua").getOrThrow()
    assertEquals(dir, file.parentFile)
    assertEquals("my_script.lua", file.name)
  }

  @Test
  fun `create rejects a name that sanitizes to empty`() {
    val result = ScriptStore.create(dir, "///")
    assertTrue(result.isFailure)
  }

  @Test
  fun `create dedupes collisions with a numeric suffix`() {
    val first = ScriptStore.create(dir, "test").getOrThrow()
    val second = ScriptStore.create(dir, "test").getOrThrow()
    assertEquals("test.lua", first.name)
    assertEquals("test (1).lua", second.name)
  }

  @Test
  fun `setEnabled toggles the disabled suffix`() {
    val file = ScriptStore.create(dir, "toggle").getOrThrow()
    val disabled = ScriptStore.setEnabled(file, enabled = false)
    assertEquals("toggle.lua.disabled", disabled.name)
    assertFalse(disabled.name.endsWith(".lua"))

    val reenabled = ScriptStore.setEnabled(disabled, enabled = true)
    assertEquals("toggle.lua", reenabled.name)
  }

  @Test
  fun `list reflects enabled and disabled scripts with clean display names`() {
    val enabled = ScriptStore.create(dir, "alpha").getOrThrow()
    val toDisable = ScriptStore.create(dir, "beta").getOrThrow()
    ScriptStore.setEnabled(toDisable, enabled = false)

    val scripts = ScriptStore.list(dir)

    assertEquals(2, scripts.size)
    assertEquals("alpha", scripts[0].name)
    assertTrue(scripts[0].enabled)
    assertEquals("beta", scripts[1].name)
    assertFalse(scripts[1].enabled)
    assertTrue(enabled.exists())
  }
}
