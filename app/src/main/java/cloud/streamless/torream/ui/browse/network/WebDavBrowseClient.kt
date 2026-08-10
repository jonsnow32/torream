package cloud.streamless.torream.ui.browse.network

import android.util.Xml
import cloud.streamless.torream.model.NetworkShare
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.StringReader
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Minimal WebDAV client: lists a directory via PROPFIND (Depth: 1) and parses the multistatus XML response. */
class WebDavBrowseClient {

  private val client = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()

  private val propfindBody = """
    <?xml version="1.0" encoding="utf-8" ?>
    <D:propfind xmlns:D="DAV:">
      <D:prop>
        <D:displayname/>
        <D:resourcetype/>
        <D:getcontentlength/>
        <D:getlastmodified/>
      </D:prop>
    </D:propfind>
  """.trimIndent()

  fun list(share: NetworkShare, relativePath: String): List<BrowseEntry> {
    val scheme = if (share.useTls) "https" else "http"
    val basePath = share.basePath.trim('/')
    val subPath = relativePath.trim('/')
    val fullPath = listOf(basePath, subPath).filter { it.isNotEmpty() }.joinToString("/")
    val url = "$scheme://${share.host}:${share.port}/$fullPath/"

    val requestBuilder = Request.Builder()
      .url(url)
      .method("PROPFIND", propfindBody.toRequestBody("application/xml".toMediaType()))
      .header("Depth", "1")

    if (!share.username.isNullOrEmpty()) {
      requestBuilder.header("Authorization", Credentials.basic(share.username, share.password.orEmpty()))
    }

    client.newCall(requestBuilder.build()).execute().use { response ->
      if (!response.isSuccessful) throw IOException("PROPFIND failed: HTTP ${response.code}")
      val body = response.body?.string() ?: return emptyList()
      return parseMultistatus(body, subPath)
    }
  }

  /** WebDAV Depth:1 PROPFIND always includes the requested collection itself as the first <response> entry. */
  private fun parseMultistatus(xml: String, parentRelativePath: String): List<BrowseEntry> {
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
    parser.setInput(StringReader(xml))

    val entries = mutableListOf<BrowseEntry>()
    var responseIndex = -1
    var href: String? = null
    var isCollection = false
    var size = 0L
    var lastModified = 0L
    var inResponse = false

    var eventType = parser.eventType
    while (eventType != XmlPullParser.END_DOCUMENT) {
      when (eventType) {
        XmlPullParser.START_TAG -> {
          when (parser.name) {
            "response" -> {
              inResponse = true
              responseIndex++
              href = null; isCollection = false; size = 0L; lastModified = 0L
            }
            "href" -> if (inResponse) href = parser.nextText()
            "collection" -> if (inResponse) isCollection = true
            "getcontentlength" -> if (inResponse) size = parser.nextText().toLongOrNull() ?: 0L
            "getlastmodified" -> if (inResponse) lastModified = parseHttpDate(parser.nextText())
          }
        }

        XmlPullParser.END_TAG -> {
          if (parser.name == "response" && inResponse) {
            if (responseIndex > 0) {
              val decodedHref = href?.let { URLDecoder.decode(it, "UTF-8") }?.trimEnd('/')
              val name = decodedHref?.substringAfterLast('/')
              if (!name.isNullOrEmpty()) {
                entries.add(
                  BrowseEntry(
                    name = name,
                    isDirectory = isCollection,
                    size = size,
                    lastModified = lastModified,
                    relativePath = if (parentRelativePath.isEmpty()) name else "$parentRelativePath/$name"
                  )
                )
              }
            }
            inResponse = false
          }
        }
      }
      eventType = parser.next()
    }
    return entries
  }

  private fun parseHttpDate(value: String): Long = try {
    SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(value)?.time ?: 0L
  } catch (_: Exception) {
    0L
  }
}
