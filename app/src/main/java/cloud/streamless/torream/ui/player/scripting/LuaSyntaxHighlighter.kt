package cloud.streamless.torream.ui.player.scripting

import android.graphics.Typeface
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.EditText

/**
 * Lightweight regex-based Lua syntax highlighter for a plain EditText — comments/strings/numbers/
 * keywords only, no tokenizer. Group index tells us which token kind matched (comment=1, string=2,
 * number=3, keyword=4); alternation order means comments/strings are matched whole before keyword
 * matching can look inside them, so e.g. "end" inside a string is never colored as a keyword.
 */
object LuaSyntaxHighlighter {

  private const val KEYWORDS =
    "and|break|do|else|elseif|end|false|for|function|goto|if|in|local|nil|not|or|repeat|return|then|true|until|while"

  private val TOKEN_REGEX = Regex(
    "(--\\[\\[[\\s\\S]*?\\]\\]|--[^\\n]*)" + // 1: comment
      "|(\\[\\[[\\s\\S]*?\\]\\]|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*')" + // 2: string
      "|\\b(0[xX][0-9A-Fa-f]+|\\d+\\.?\\d*(?:[eE][+-]?\\d+)?)\\b" + // 3: number
      "|\\b($KEYWORDS)\\b" // 4: keyword
  )

  private const val COMMENT_COLOR = 0xFF868E96.toInt()
  private const val STRING_COLOR = 0xFF2B8A3E.toInt()
  private const val NUMBER_COLOR = 0xFF9C36B5.toInt()
  private const val KEYWORD_COLOR = 0xFF3B5BDB.toInt()

  fun attach(editText: EditText) {
    highlight(editText.text)
    editText.addTextChangedListener(object : TextWatcher {
      override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
      override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
      override fun afterTextChanged(s: Editable) = highlight(s)
    })
  }

  private fun highlight(editable: Editable) {
    editable.getSpans(0, editable.length, ForegroundColorSpan::class.java).forEach { editable.removeSpan(it) }
    editable.getSpans(0, editable.length, StyleSpan::class.java).forEach { editable.removeSpan(it) }

    TOKEN_REGEX.findAll(editable).forEach { match ->
      val start = match.range.first
      val end = match.range.last + 1
      if (start >= end) return@forEach

      when {
        match.groups[1] != null -> {
          editable.setSpan(ForegroundColorSpan(COMMENT_COLOR), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
          editable.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        match.groups[2] != null ->
          editable.setSpan(ForegroundColorSpan(STRING_COLOR), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        match.groups[3] != null ->
          editable.setSpan(ForegroundColorSpan(NUMBER_COLOR), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        match.groups[4] != null -> {
          editable.setSpan(ForegroundColorSpan(KEYWORD_COLOR), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
          editable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
      }
    }
  }
}
