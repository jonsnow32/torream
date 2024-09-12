package cloud.app.csplayer.ui.player.exo

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.core.animation.addListener
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.media3.common.Format.NO_VALUE
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.preference.PreferenceManager
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.FragmentPlayerBinding
import cloud.app.csplayer.databinding.PlayerSelectSourceAndSubsBinding
import cloud.app.csplayer.databinding.PlayerSelectTracksBinding
import cloud.app.csplayer.databinding.PlayerSelectVideoTracksBinding
import cloud.app.csplayer.ui.player.CSPlayerEvent
import cloud.app.csplayer.ui.player.CSPlayerViewModel
import cloud.app.csplayer.ui.player.PlayerEventSource
import cloud.app.csplayer.ui.player.SkipStamp
import cloud.app.csplayer.ui.player.exo.CSPlayer.Companion.preferredAudioTrackLanguage
import cloud.app.csplayer.ui.player.exo.CustomDecoder.Companion.updateForcedEncoding
import cloud.app.csplayer.ui.subtitles.SUBTITLE_AUTO_SELECT_KEY
import cloud.app.csplayer.ui.subtitles.SubtitlesFragment.Companion.getAutoSelectLanguageISO639_1
import cloud.app.csplayer.utils.CommonActivitty
import cloud.app.csplayer.utils.DataStore.setKey
import cloud.app.csplayer.utils.ExtractorLink
import cloud.app.csplayer.utils.ExtractorUri
import cloud.app.csplayer.utils.SingleSelectionHelper.showDialog
import cloud.app.csplayer.utils.SubtitleData
import cloud.app.csplayer.utils.SubtitleHelper.fromTwoLettersToLanguage
import cloud.app.csplayer.utils.SubtitleHelper.languages
import cloud.app.csplayer.utils.SubtitleOrigin
import cloud.app.csplayer.utils.UIHelper.dismissSafe
import cloud.app.csplayer.utils.UIHelper.popCurrentPage
import cloud.app.csplayer.utils.UIHelper.toPx
import cloud.app.csplayer.utils.Utils
import cloud.app.csplayer.utils.Utils.logError
import cloud.app.csplayer.utils.Utils.normalSafeApiCall
import cloud.app.csplayer.utils.Utils.showToast
import cloud.app.csplayer.utils.Utils.sortSubs
import cloud.app.csplayer.utils.Utils.toSubtitleMimeType
import cloud.app.csplayer.utils.hideSystemUI
import cloud.app.csplayer.utils.isTvOrEmulator
import cloud.app.csplayer.utils.observe
import kotlinx.coroutines.Job
import java.io.File

class CSPlayerFragment : FullScreenPlayer() {

  private val viewModel by viewModels<CSPlayerViewModel>()
  private var titleRez = 3
  private var limitTitle = 0
  private var allLinks: Set<Pair<ExtractorLink?, ExtractorUri?>> = setOf()
  private var currentSubs: Set<SubtitleData> = mutableSetOf()
  private var currentSelectedLink: Pair<ExtractorLink?, ExtractorUri?>? = null
  private var currentSelectedSubtitles: SubtitleData? = null

  private var isActive: Boolean = false
  private var isNextEpisode: Boolean = false
  private var preferredAutoSelectSubtitles: String? = null // null means do nothing, "" means none

  private var binding: FragmentPlayerBinding? = null

  private fun startLoading() {
    player.release()
    currentSelectedSubtitles = null
    binding?.overlayLoadingSkipButton?.isVisible = false
    binding?.playerLoadingOverlay?.isVisible = true
  }

  private fun setSubtitles(subtitle: SubtitleData?): Boolean {
    // If subtitle is changed -> Save the language
    if (subtitle != currentSelectedSubtitles) {
      val subtitleLanguage639 = if (subtitle == null) {
        // "" is No Subtitles
        ""
      } else if (subtitle.languageCode != null) {
        // Could be "English 4" which is why it is trimmed.
        val trimmedLanguage = subtitle.languageCode.replace(Regex("\\d"), "").trim()

        languages.firstOrNull { language ->
          language.languageName.equals(trimmedLanguage, ignoreCase = true) ||
            language.ISO_639_1 == subtitle.languageCode
        }?.ISO_639_1
      } else {
        null
      }

      if (subtitleLanguage639 != null) {
        requireActivity().setKey(SUBTITLE_AUTO_SELECT_KEY, subtitleLanguage639)
        preferredAutoSelectSubtitles = subtitleLanguage639
      }
    }

    currentSelectedSubtitles = subtitle
    //Log.i(TAG, "setSubtitles = $subtitle")
    return player.setPreferredSubtitles(subtitle)
  }

  override fun embeddedSubtitlesFetched(subtitles: List<SubtitleData>) {
    viewModel.addSubtitles(subtitles.toSet())
  }

  override fun onTracksInfoChanged() {
    val tracks = player.getVideoTracks()
//    playerBinding?.playerTracksBtt?.isVisible =
//      tracks.allVideoTracks.size > 1 || tracks.allAudioTracks.size > 1 || currentSubs.size > 0
    // Only set the preferred language if it is available.
    // Otherwise it may give some users audio track init failed!
    if (tracks.allAudioTracks.any { it.language == preferredAudioTrackLanguage }) {
      player.setPreferredAudioTrack(preferredAudioTrackLanguage)
    }
  }

  override fun playerStatusChanged() {
    if (player.getIsPlaying()) {

    }
  }

  private fun noSubtitles(): Boolean {
    return setSubtitles(null)
  }

  private var currentVerifyLink: Job? = null
  private fun loadLink(link: Pair<ExtractorLink?, ExtractorUri?>?, sameEpisode: Boolean) {
    if (link == null) return
    // manage UI
    //binding?.playerLoadingOverlay?.isVisible = false
    uiReset()
    currentSelectedLink = link
    setPlayerDimen(null)
    setTitle()
    if (!sameEpisode)
      hasRequestedStamps = false

    // load player
    context?.let { ctx ->
      val (url, uri) = link
      player.loadPlayer(
        ctx,
        sameEpisode,
        url,
        uri,
        startPosition = if (sameEpisode) null else if (isNextEpisode) 0L else link.first?.position,
        currentSubs,
        (if (sameEpisode) currentSelectedSubtitles else null) ?: getAutoSelectSubtitle(
          currentSubs, settings = true, downloads = false
        ),
        preview = isFullScreenPlayer
      )
    }

    if (!sameEpisode)
      player.addTimeStamps(listOf()) // clear stamps
  }


  override fun openOnlineSubPicker(
    context: Context, loadResponse: List<SubtitleData>?, dismissCallback: (() -> Unit)
  ) {
//    val providers = subsProviders
//    val isSingleProvider = subsProviders.size == 1
//
//    val dialog = Dialog(context, R.style.AlertDialogCustomBlack)
//    val binding =
//      DialogOnlineSubtitlesBinding.inflate(LayoutInflater.from(context), null, false)
//    dialog.setContentView(binding.root)
//
//    var currentSubtitles: List<AbstractSubtitleEntities.SubtitleEntity> = emptyList()
//    var currentSubtitle: AbstractSubtitleEntities.SubtitleEntity? = null
//
//    fun getName(entry: AbstractSubtitleEntities.SubtitleEntity, withLanguage: Boolean): String {
//      if (entry.lang.isBlank() || !withLanguage) {
//        return entry.name
//      }
//      val language = fromTwoLettersToLanguage(entry.lang.trim()) ?: entry.lang
//      return "$language ${entry.name}"
//    }
//
//    val layout = R.layout.sort_bottom_single_choice_double_text
//    val arrayAdapter =
//      object : ArrayAdapter<AbstractSubtitleEntities.SubtitleEntity>(dialog.context, layout) {
//        fun setHearingImpairedIcon(
//          imageViewEnd: ImageView?, position: Int
//        ) {
//          if (imageViewEnd == null) return
//          val isHearingImpaired =
//            currentSubtitles.getOrNull(position)?.isHearingImpaired ?: false
//
//          val drawableEnd = if (isHearingImpaired) {
//            ContextCompat.getDrawable(
//              context, R.drawable.ic_baseline_hearing_24
//            )?.apply {
//              setTint(
//                ContextCompat.getColor(
//                  context, R.color.textColor
//                )
//              )
//            }
//          } else null
//
//          imageViewEnd.setImageDrawable(drawableEnd)
//        }
//
//        @SuppressLint("SetTextI18n")
//        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
//          val view = convertView ?: LayoutInflater.from(context).inflate(layout, null)
//
//          val item = getItem(position)
//
//          val mainTextView = view.findViewById<TextView>(R.id.main_text)
//          val secondaryTextView = view.findViewById<TextView>(R.id.secondary_text)
//          val drawableEnd = view.findViewById<ImageView>(R.id.drawable_end)
//
//          mainTextView?.text = item?.let { getName(it, false) }
//
//          val language =
//            item?.let { fromTwoLettersToLanguage(it.lang.trim()) ?: it.lang } ?: ""
//          val providerSuffix =
//            if (isSingleProvider || item == null) "" else " · ${item.source}"
//          secondaryTextView?.text = language + providerSuffix
//
//          setHearingImpairedIcon(drawableEnd, position)
//          return view
//        }
//      }
//
//    dialog.show()
//    binding.cancelBtt.setOnClickListener {
//      dialog.dismissSafe(activity)
//    }
//
//    binding.subtitleAdapter.choiceMode = AbsListView.CHOICE_MODE_SINGLE
//    binding.subtitleAdapter.adapter = arrayAdapter
//    val adapter =
//      binding.subtitleAdapter.adapter as? ArrayAdapter<AbstractSubtitleEntities.SubtitleEntity>
//
//    binding.subtitleAdapter.setOnItemClickListener { _, _, position, _ ->
//      currentSubtitle = currentSubtitles.getOrNull(position) ?: return@setOnItemClickListener
//    }
//
//    var currentLanguageTwoLetters: String = context.getAutoSelectLanguageISO639_1()
//
//
//    fun setSubtitlesList(list: List<AbstractSubtitleEntities.SubtitleEntity>) {
//      currentSubtitles = list
//      adapter?.clear()
//      adapter?.addAll(currentSubtitles)
//    }
//
//    val currentTempMeta = getMetaData()
//
//    // bruh idk why it is not correct
//    val color = ColorStateList.valueOf(context.colorFromAttribute(R.attr.colorAccent))
//    binding.searchLoadingBar.progressTintList = color
//    binding.searchLoadingBar.indeterminateTintList = color
//
//    observeNullable(viewModel.currentSubtitleYear) {
//      // When year is changed search again
//      binding.subtitlesSearch.setQuery(binding.subtitlesSearch.query, true)
//      binding.yearBtt.text = it?.toString() ?: txt(R.string.none).asString(context)
//    }
//
//    binding.yearBtt.setOnClickListener {
//      val none = txt(R.string.none).asString(context)
//      val currentYear = Calendar.getInstance().get(Calendar.YEAR)
//      val earliestYear = 1900
//
//      val years = (currentYear downTo earliestYear).toList()
//      val options = listOf(none) + years.map {
//        it.toString()
//      }
//
//      val selectedIndex = viewModel.currentSubtitleYear.value
//        ?.let {
//          // + 1 since none also takes a space
//          years.indexOf(it) + 1
//        }
//        ?.takeIf { it >= 0 } ?: 0
//
//      activity?.showDialog(
//        options,
//        selectedIndex,
//        txt(R.string.year).asString(context),
//        true, {
//        }, { index ->
//          viewModel.setSubtitleYear(years.getOrNull(index - 1))
//        }
//      )
//    }
//
//    binding.subtitlesSearch.setOnQueryTextListener(object :
//      androidx.appcompat.widget.SearchView.OnQueryTextListener {
//      override fun onQueryTextSubmit(query: String?): Boolean {
//        binding.searchLoadingBar.show()
//        ioSafe {
//          val search =
//            AbstractSubtitleEntities.SubtitleSearch(
//              query = query ?: return@ioSafe,
//              imdbId = loadResponse?.getImdbId(),
//              tmdbId = loadResponse?.getTMDbId()?.toInt(),
//              malId = loadResponse?.getMalId()?.toInt(),
//              aniListId = loadResponse?.getAniListId()?.toInt(),
//              epNumber = currentTempMeta.episode,
//              seasonNumber = currentTempMeta.season,
//              lang = currentLanguageTwoLetters.ifBlank { null },
//              year = viewModel.currentSubtitleYear.value
//            )
//          val results = providers.amap {
//            try {
//              it.search(search)
//            } catch (e: Exception) {
//              null
//            }
//          }.filterNotNull()
//          val max = results.maxOfOrNull { it.size } ?: return@ioSafe
//
//          // very ugly
//          val items = ArrayList<AbstractSubtitleEntities.SubtitleEntity>()
//          val arrays = results.size
//          for (index in 0 until max) {
//            for (i in 0 until arrays) {
//              items.add(results[i].getOrNull(index) ?: continue)
//            }
//          }
//
//          // ugly ik
//          activity?.runOnUiThread {
//            setSubtitlesList(items)
//            binding.searchLoadingBar.hide()
//          }
//        }
//
//        return true
//      }
//
//      override fun onQueryTextChange(newText: String?): Boolean {
//        return true
//      }
//    })
//
//    binding.searchFilter.setOnClickListener { view ->
//      val lang639_1 = languages.map { it.ISO_639_1 }
//      activity?.showDialog(languages.map { it.languageName },
//        lang639_1.indexOf(currentLanguageTwoLetters),
//        view?.context?.getString(R.string.subs_subtitle_languages)
//          ?: return@setOnClickListener,
//        true,
//        { }) { index ->
//        currentLanguageTwoLetters = lang639_1[index]
//        binding.subtitlesSearch.setQuery(binding.subtitlesSearch.query, true)
//      }
//    }
//
//    binding.applyBtt.setOnClickListener {
//      currentSubtitle?.let { currentSubtitle ->
//        providers.firstOrNull { it.idPrefix == currentSubtitle.idPrefix }?.let { api ->
//          ioSafe {
//            val subtitles =
//              api.getResource(currentSubtitle).getSubtitles().map { resource ->
//                SubtitleData(
//                  name = resource.name ?: getName(currentSubtitle, true),
//                  url = resource.url,
//                  origin = resource.origin,
//                  mimeType = resource.url.toSubtitleMimeType(),
//                  headers = currentSubtitle.headers,
//                  currentSubtitle.lang
//                )
//              }
//            if (subtitles.isNotEmpty()) {
//              runOnMainThread {
//                addAndSelectSubtitles(*subtitles.toTypedArray())
//              }
//            }
//          }
//        }
//      }
//      dialog.dismissSafe()
//    }
//
//    dialog.setOnDismissListener {
//      dismissCallback.invoke()
//    }
//
//    dialog.show()
//    binding.subtitlesSearch.setQuery(currentTempMeta.name, true)
//    //TODO: Set year text from currently loaded movie on Player
//    //dialog.subtitles_search_year?.setText(currentTempMeta.year)
  }

  @OptIn(UnstableApi::class)
  private fun openSubPicker() {
    try {
      subsPathPicker.launch(
        arrayOf(
          "text/plain",
          "text/str",
          "application/octet-stream",
          MimeTypes.TEXT_UNKNOWN,
          MimeTypes.TEXT_VTT,
          MimeTypes.TEXT_SSA,
          MimeTypes.APPLICATION_TTML,
          MimeTypes.APPLICATION_MP4VTT,
          MimeTypes.APPLICATION_SUBRIP,
        )
      )
    } catch (e: Exception) {
      logError(e)
    }
  }

  private fun addAndSelectSubtitles(
    vararg subtitleData: SubtitleData
  ) {
    if (subtitleData.isEmpty()) return
    val selectedSubtitle = subtitleData.first()
    val ctx = context ?: return

    val subs = currentSubs + subtitleData

    // this is used instead of observe(viewModel._currentSubs), because observe is too slow
    player.setActiveSubtitles(subs)

    // Save current time as to not reset player to 00:00
    player.saveData()
    player.reloadPlayer(ctx)

    setSubtitles(selectedSubtitle)

    viewModel.addSubtitles(subtitleData.toSet())

    selectTrackDialog?.dismissSafe(activity)

    showToast(
      String.format(ctx.getString(R.string.player_loaded_subtitles), selectedSubtitle.name),
      Toast.LENGTH_LONG
    )
  }

  // Open file picker
  private val subsPathPicker =
    registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      normalSafeApiCall {
        // It lies, it can be null if file manager quits.
        if (uri == null) return@normalSafeApiCall
        val ctx = context ?: Utils.activity ?: return@normalSafeApiCall
        // RW perms for the path
        ctx.contentResolver.takePersistableUriPermission(
          uri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )

        val file = File(requireNotNull(uri.path))// SafeFile.fromUri(ctx, uri)
        val fileName = file.name
        println("Loaded subtitle file. Selected URI path: $uri - Name: $fileName")
        // DO NOT REMOVE THE FILE EXTENSION FROM NAME, IT'S NEEDED FOR MIME TYPES
        val name = fileName ?: uri.toString()

        val subtitleData = SubtitleData(
          name,
          uri.toString(),
          SubtitleOrigin.DOWNLOADED_FILE,
          name.toSubtitleMimeType(),
          emptyMap(),
          null
        )

        addAndSelectSubtitles(subtitleData)
      }
    }

  var selectSourceDialog: Dialog? = null

  @OptIn(UnstableApi::class)
  override fun showMirrorsDialogue() {
    try {

      //println("CURRENT SELECTED :$currentSelectedSubtitles of $currentSubs")
      context?.let { ctx ->
        val isPlaying = player.getIsPlaying()
        player.handleEvent(CSPlayerEvent.Pause, PlayerEventSource.UI)
        val currentSubtitles = sortSubs(currentSubs)

        val sourceDialog = Dialog(ctx, R.style.AlertDialogCustom)
        val binding =
          PlayerSelectSourceAndSubsBinding.inflate(LayoutInflater.from(ctx), null, false)
        sourceDialog.setContentView(binding.root)

        selectSourceDialog = sourceDialog

        sourceDialog.show()
        val providerList = binding.sortProviders
        var shouldDismiss = true

        fun dismiss() {
          if (isPlaying) {
            player.handleEvent(CSPlayerEvent.Play)
          }
          activity?.hideSystemUI()
        }

        var startSource = 0
        var sortedUrls = allLinks
        var sourceIndex = allLinks.indexOf(currentSelectedLink)
        val sourcesArrayAdapter =
          ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)

        sourcesArrayAdapter.addAll(sortedUrls.mapIndexed { index, (link, uri) ->
          "${index + 1}. " + (link?.source ?: uri?.name ?: "NULL")
        })

        providerList.choiceMode = AbsListView.CHOICE_MODE_SINGLE
        providerList.adapter = sourcesArrayAdapter
        providerList.setSelection(sourceIndex)
        providerList.setItemChecked(sourceIndex, true)

        providerList.setOnItemClickListener { _, _, which, _ ->
          sourceIndex = which
          providerList.setItemChecked(which, true)
        }

        sourceDialog.setOnDismissListener {
          if (shouldDismiss) dismiss()
          selectSourceDialog = null
        }

        binding.cancelBtt.setOnClickListener {
          sourceDialog.dismissSafe(activity)
        }

        binding.applyBtt.setOnClickListener {
          sortedUrls.elementAt(sourceIndex).let {
            loadLink(it, true)
          }
          sourceDialog.dismissSafe(activity)
        }
      }
    } catch (e: Exception) {
      logError(e)
    }
  }

  var selectVideoDialog: Dialog? = null
  override fun showVideoDialogue() {
    try {
      //println("CURRENT SELECTED :$currentSelectedSubtitles of $currentSubs")
      context?.let { ctx ->
        val tracks = player.getVideoTracks()

        val isPlaying = player.getIsPlaying()
        player.handleEvent(CSPlayerEvent.Pause)

        val currentVideoTracks = tracks.allVideoTracks.sortedBy {
          it.height?.times(-1)
        }
        val binding: PlayerSelectVideoTracksBinding =
          PlayerSelectVideoTracksBinding.inflate(LayoutInflater.from(ctx), null, false)
        val trackDialog = Dialog(ctx, R.style.AlertDialogCustom)
        trackDialog.setContentView(binding.root)
        trackDialog.show()
        selectVideoDialog = trackDialog

        fun dismiss() {
          if (isPlaying) {
            player.handleEvent(CSPlayerEvent.Play)
          }
          activity?.hideSystemUI()
        }

        val videosArrayAdapter =
          ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)

        videosArrayAdapter.addAll(currentVideoTracks.mapIndexed { index, format ->
          format.label
            ?: (if (format.height == NO_VALUE || format.width == NO_VALUE) index else "${format.width}x${format.height}").toString()
        })

        // Sometimes the data is not the same because some data gets resolved at different stages i think
        var videoIndex = currentVideoTracks.indexOf(tracks.currentVideoTrack).takeIf {
          it != -1
        } ?: currentVideoTracks.indexOfFirst {
          tracks.currentVideoTrack?.id == it.id
        }

        trackDialog.setOnDismissListener {
          dismiss()
        }
        binding.cancelBtt.setOnClickListener {
          trackDialog.dismissSafe(activity)
        }
        binding.applyBtt.setOnClickListener {
          val currentVideo = currentVideoTracks.getOrNull(videoIndex)
          val width = currentVideo?.width ?: NO_VALUE
          val height = currentVideo?.height ?: NO_VALUE
          if (width != NO_VALUE && height != NO_VALUE) {
            player.setMaxVideoSize(width, height, currentVideo?.id)
          }
          trackDialog.dismissSafe(activity)
        }
      }
    } catch (e: Exception) {
      logError(e)
    }
  }

  var selectTrackDialog: Dialog? = null
  @OptIn(UnstableApi::class)
  override fun showTracksDialogue() {
    try {
      //println("CURRENT SELECTED :$currentSelectedSubtitles of $currentSubs")
      context?.let { ctx ->
        val tracks = player.getVideoTracks()

        val isPlaying = player.getIsPlaying()
        player.handleEvent(CSPlayerEvent.Pause)

        val currentVideoTracks = tracks.allVideoTracks.sortedBy {
          it.height?.times(-1)
        }
        val currentAudioTracks = tracks.allAudioTracks
        val binding: PlayerSelectTracksBinding =
          PlayerSelectTracksBinding.inflate(LayoutInflater.from(ctx), null, false)
        val trackDialog = Dialog(ctx, R.style.AlertDialogCustom)
        trackDialog.setContentView(binding.root)
        trackDialog.show()
        selectTrackDialog = trackDialog

        val audioList = binding.autoTracksList

        binding.audioTracksHolder.isVisible = currentAudioTracks.size > 1

        fun dismiss() {
          if (isPlaying) {
            player.handleEvent(CSPlayerEvent.Play)
          }
          activity?.hideSystemUI()
        }

        val videosArrayAdapter =
          ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)

        videosArrayAdapter.addAll(currentVideoTracks.mapIndexed { index, format ->
          format.label
            ?: (if (format.height == NO_VALUE || format.width == NO_VALUE) index else "${format.width}x${format.height}").toString()
        })

        // Sometimes the data is not the same because some data gets resolved at different stages i think
        var videoIndex = currentVideoTracks.indexOf(tracks.currentVideoTrack).takeIf {
          it != -1
        } ?: currentVideoTracks.indexOfFirst {
          tracks.currentVideoTrack?.id == it.id
        }

        trackDialog.setOnDismissListener {
          dismiss()
//                    selectTracksDialog = null
        }

        var audioIndexStart = currentAudioTracks.indexOf(tracks.currentAudioTrack).takeIf {
          it != -1
        } ?: currentVideoTracks.indexOfFirst {
          tracks.currentAudioTrack?.id == it.id
        }

        val audioArrayAdapter =
          ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)
//                audioArrayAdapter.add(ctx.getString(R.string.no_subtitles))
        audioArrayAdapter.addAll(currentAudioTracks.mapIndexed { index, format ->
          format.label ?: format.language?.let { fromTwoLettersToLanguage(it) }
          ?: index.toString()
        })

        audioList.adapter = audioArrayAdapter
        audioList.choiceMode = AbsListView.CHOICE_MODE_SINGLE

        audioList.setSelection(audioIndexStart)
        audioList.setItemChecked(audioIndexStart, true)

        audioList.setOnItemClickListener { _, _, which, _ ->
          audioIndexStart = which
          audioList.setItemChecked(which, true)
        }


        val subtitleList = binding.sortSubtitles
        val loadFromFileFooter: TextView =
          layoutInflater.inflate(R.layout.sort_bottom_footer_add_choice, null) as TextView

        loadFromFileFooter.text = ctx.getString(R.string.player_load_subtitles)
        loadFromFileFooter.setOnClickListener {
          openSubPicker()
        }
        subtitleList.addFooterView(loadFromFileFooter)
        val currentSubtitles = sortSubs(currentSubs)
        val subtitleIndexStart = currentSubtitles.indexOf(currentSelectedSubtitles) + 1
        var subtitleIndex = subtitleIndexStart

        val subsArrayAdapter = ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)
        subsArrayAdapter.add(ctx.getString(R.string.no_subtitles))
        subsArrayAdapter.addAll(currentSubtitles.map { it.name })

        subtitleList.adapter = subsArrayAdapter
        subtitleList.choiceMode = AbsListView.CHOICE_MODE_SINGLE

        subtitleList.setSelection(subtitleIndex)
        subtitleList.setItemChecked(subtitleIndex, true)

        subtitleList.setOnItemClickListener { _, _, which, _ ->
          if (which > currentSubtitles.size) {
            // Since android TV is funky the setOnItemClickListener will be triggered
            // instead of setOnClickListener when selecting. To override this we programmatically
            // click the view when selecting an item outside the list.

            // Cheeky way of getting the view at that position to click it
            // to avoid keeping track of the various footers.
            // getChildAt() gives null :(
            val child = subtitleList.adapter.getView(which, null, subtitleList)
            child?.performClick()
          } else {
            subtitleIndex = which
            subtitleList.setItemChecked(which, true)
          }
        }


        binding.subtitlesEncodingFormat.apply {
          val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)

          val prefNames = ctx.resources.getStringArray(R.array.subtitles_encoding_list)
          val prefValues = ctx.resources.getStringArray(R.array.subtitles_encoding_values)

          val value = settingsManager.getString(
            ctx.getString(R.string.subtitles_encoding_key), null
          )
          val index = prefValues.indexOf(value)
          text = prefNames[if (index == -1) 0 else index]
        }
        binding.subtitlesClickSettings.setOnClickListener {
          val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)

          val prefNames = ctx.resources.getStringArray(R.array.subtitles_encoding_list)
          val prefValues = ctx.resources.getStringArray(R.array.subtitles_encoding_values)

          val currentPrefMedia = settingsManager.getString(
            ctx.getString(R.string.subtitles_encoding_key), null
          )
          val index = prefValues.indexOf(currentPrefMedia)
          activity?.showDialog(prefNames.toList(),
            if (index == -1) 0 else index,
            ctx.getString(R.string.subtitles_encoding),
            true,
            {}) {
            settingsManager.edit().putString(
              ctx.getString(R.string.subtitles_encoding_key), prefValues[it]
            ).apply()

            updateForcedEncoding(ctx)
            dismiss()
            player.seekTime(-1) // to update subtitles, a dirty trick
          }
        }

        binding.cancelBtt.setOnClickListener {
          trackDialog.dismissSafe(activity)
        }

        binding.applyBtt.setOnClickListener {
          val currentTrack = currentAudioTracks.getOrNull(audioIndexStart)
          player.setPreferredAudioTrack(
            currentTrack?.language, currentTrack?.id
          )
          if (subtitleIndex != subtitleIndexStart) {
            if (subtitleIndex <= 0) {
              noSubtitles()
            } else {
              currentSubtitles.getOrNull(subtitleIndex - 1)?.let {
                setSubtitles(it)
              } ?: false
            }
          }

          val currentVideo = currentVideoTracks.getOrNull(videoIndex)
          val width = currentVideo?.width ?: NO_VALUE
          val height = currentVideo?.height ?: NO_VALUE
          if (width != NO_VALUE && height != NO_VALUE) {
            player.setMaxVideoSize(width, height, currentVideo?.id)
          }
          trackDialog.dismissSafe(activity)
        }
      }
    } catch (e: Exception) {
      logError(e)
    }
  }


  override fun playerError(exception: Throwable) {
    player.getPosition()?.let {
      CommonActivitty.activityResultEvent?.invoke(
        Activity.RESULT_OK,
        it
      )
    }
    Log.i(TAG, "playerError = $currentSelectedLink")
    super.playerError(exception)
  }

  private fun noLinksFound() {
    showToast(R.string.no_links_found_toast, Toast.LENGTH_SHORT)
    //activity?.popCurrentPage()
  }

  private fun startPlayer(index: Int) {
    if (!player.isActive())
      loadLink(allLinks.elementAt(index), false)
  }

  override fun onResume() {
    if (context == null) return
    if (player.isActive() && !player.getIsPlaying())
      loadLink(currentSelectedLink, true)
    super.onResume()
  }

  override fun nextEpisode() {
    if (viewModel.isSameEpisode.value == false) {
      player.release()
      isNextEpisode = true
      allLinks.forEachIndexed { index, pair ->
        if (pair == currentSelectedLink)
          if (index + 1 < allLinks.size) {
            loadLink(allLinks.elementAt(index + 1), false);
            return
          }
      }
    }

    player.getPosition()?.let {
      CommonActivitty.activityResultEvent?.invoke(
        Activity.RESULT_OK,
        it
      )
    }
    activity?.finish()

  }

  override fun prevEpisode() {
//    isNextEpisode = true
//    player.release()
//    allLinks.forEachIndexed { index, pair ->
//      if(pair == currentSelectedLink)
//        if(index - 1 >= 0)
//        loadLink(allLinks.elementAt(index - 1), false);
//    }
  }

  override fun hasNextMirror(): Boolean {
    return allLinks.isNotEmpty() && allLinks.indexOf(currentSelectedLink) + 1 < allLinks.size
  }


  override fun nextMirror() {
    val newIndex = allLinks.indexOf(currentSelectedLink) + 1
    if (newIndex >= allLinks.size) {
      noLinksFound()
      return
    }

    loadLink(allLinks.elementAt(newIndex), true)
  }


  override fun onStop() {
    currentVerifyLink?.cancel()
    super.onStop()
  }

  var hasRequestedStamps: Boolean = false
  override fun playerPositionChanged(position: Long, duration: Long) {

    if (duration <= 0L) return // idk how you achieved this, but div by zero crash
    if (!hasRequestedStamps) {
      hasRequestedStamps = true
      val fetchStamps = context?.let { ctx ->
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)
        settingsManager.getBoolean(
          ctx.getString(R.string.enable_skip_op_from_database),
          true
        )
      } ?: true
    }


    val percentage = position * 100L / duration

    var isOpVisible = false

    playerBinding?.playerSkipOp?.isVisible = isOpVisible

    player.getPosition()?.let {
      CommonActivitty.activityResultEvent?.invoke(
        Activity.RESULT_OK,
        it
      )
    }
  }

  private fun getAutoSelectSubtitle(
    subtitles: Set<SubtitleData>, settings: Boolean, downloads: Boolean
  ): SubtitleData? {
    val langCode = preferredAutoSelectSubtitles ?: return null
    val lang = fromTwoLettersToLanguage(langCode) ?: return null
    if (downloads) {
      return subtitles.firstOrNull { sub ->
        (sub.origin == SubtitleOrigin.DOWNLOADED_FILE && sub.name == context?.getString(
          R.string.default_subtitles
        ))
      }
    }

    sortSubs(subtitles).firstOrNull { sub ->
      val t = sub.name.replace(Regex("[^A-Za-z]"), " ").trim()
      (settings) && t == lang || t.startsWith(lang) || t == langCode
    }?.let { sub ->
      return sub
    }

    return null
  }

  private fun autoSelectFromSettings(): Boolean {
    // auto select subtitle based of settings
    val langCode = preferredAutoSelectSubtitles
    val current = player.getCurrentPreferredSubtitle()
    Log.i(TAG, "autoSelectFromSettings = $current")
    context?.let { ctx ->
      if (current != null) {
        if (setSubtitles(current)) {
          player.saveData()
          player.reloadPlayer(ctx)
          player.handleEvent(CSPlayerEvent.Play)
          return true
        }
      } else if (!langCode.isNullOrEmpty()) {
        getAutoSelectSubtitle(
          currentSubs, settings = true, downloads = false
        )?.let { sub ->
          if (setSubtitles(sub)) {
            player.saveData()
            player.reloadPlayer(ctx)
            player.handleEvent(CSPlayerEvent.Play)
            return true
          }
        }
      }
    }
    return false
  }

  private fun autoSelectFromDownloads(): Boolean {
    if (player.getCurrentPreferredSubtitle() == null) {
      getAutoSelectSubtitle(currentSubs, settings = false, downloads = true)?.let { sub ->
        context?.let { ctx ->
          if (setSubtitles(sub)) {
            player.saveData()
            player.reloadPlayer(ctx)
            player.handleEvent(CSPlayerEvent.Play)
            return true
          }
        }
      }
    }
    return false
  }

  private fun autoSelectSubtitles() {
    //Log.i(TAG, "autoSelectSubtitles")
    normalSafeApiCall {
      if (!autoSelectFromSettings()) {
        autoSelectFromDownloads()
      }
    }
  }

  private fun getPlayerVideoTitle(): String {
    if(isNextEpisode)
      return currentSelectedLink?.first?.source ?: ""
    else
      return currentSelectedLink?.first?.name ?: ""
  }


  @SuppressLint("SetTextI18n")
  fun setTitle() {
    var playerVideoTitle = getPlayerVideoTitle()

    //Hide title, if set in setting
    if (limitTitle < 0) {
      playerBinding?.playerVideoTitle?.visibility = View.GONE
    } else {
      //Truncate video title if it exceeds limit
      val differenceInLength = playerVideoTitle.length - limitTitle
      val margin = 3 //If the difference is smaller than or equal to this value, ignore it
      if (limitTitle > 0 && differenceInLength > margin) {
        playerVideoTitle = playerVideoTitle.substring(0, limitTitle - 1) + "..."
      }
    }

    playerBinding?.playerVideoTitle?.text = playerVideoTitle
  }

  @SuppressLint("SetTextI18n")
  fun setPlayerDimen(widthHeight: Pair<Int, Int>?) {
    val extra = if (widthHeight != null) {
      val (width, height) = widthHeight
      "${width}x${height}"
    } else {
      ""
    }

    val source = currentSelectedLink?.first?.source ?: currentSelectedLink?.second?.name ?: "NULL"

    val title = when (titleRez) {
      0 -> ""
      1 -> extra
      2 -> source
      3 -> "$source - $extra"
      else -> ""
    }
    playerBinding?.playerVideoTitleRez?.apply {
      text = title
      isVisible = title.isNotBlank()
    }
  }

  override fun playerDimensionsLoaded(width: Int, height: Int) {
    super.playerDimensionsLoaded(width, height)
    setPlayerDimen(width to height)
  }

  private fun unwrapBundle(savedInstanceState: Bundle?) {
    Log.i(TAG, "unwrapBundle = $savedInstanceState")
    savedInstanceState?.let { bundle ->
      //sync.addSyncs(bundle.getSerializable("syncData") as? HashMap<String, String>?)
    }
  }

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
  ): View? {
    // this is used instead of layout-television to follow the settings and some TV devices are not classified as TV for some reason
    layout =
      if (context?.isTvOrEmulator() == true) R.layout.fragment_player_tv else R.layout.fragment_player

//    viewModel = ViewModelProvider(this)[CSPlayerViewModel::class.java]
//    viewModel.initialize(arguments)

    unwrapBundle(savedInstanceState)
    unwrapBundle(arguments)


    val root = super.onCreateView(inflater, container, savedInstanceState) ?: return null
    binding = FragmentPlayerBinding.bind(root)
    return root
  }

  override fun onDestroyView() {
    binding = null
    super.onDestroyView()
  }

  var timestampShowState = false

  var skipAnimator: ValueAnimator? = null
  var skipIndex = 0

  private fun displayTimeStamp(show: Boolean) {
    if (timestampShowState == show) return
    skipIndex++
    timestampShowState = show

  }

  override fun onTimestampSkipped(timestamp: SkipStamp) {
    displayTimeStamp(false)
  }

  override fun onTimestamp(timestamp: SkipStamp?) {
    if (timestamp != null) {
      //playerBinding?.skipChapterButton?.setText(timestamp.uiText)
      displayTimeStamp(true)
    } else {
      displayTimeStamp(false)
    }
  }

  @OptIn(UnstableApi::class)
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    var langFilterList = listOf<String>()
    var filterSubByLang = false

    context?.let { ctx ->
      val settingsManager = PreferenceManager.getDefaultSharedPreferences(ctx)
      titleRez = settingsManager.getInt(ctx.getString(R.string.prefer_limit_title_rez_key), 3)
      limitTitle = settingsManager.getInt(ctx.getString(R.string.prefer_limit_title_key), 0)
      updateForcedEncoding(ctx)

      filterSubByLang =
        settingsManager.getBoolean(getString(R.string.filter_sub_lang_key), false)
      if (filterSubByLang) {
        val langFromPrefMedia = settingsManager.getStringSet(
          this.getString(R.string.provider_lang_key), mutableSetOf("en")
        )
        langFilterList = langFromPrefMedia?.mapNotNull {
          fromTwoLettersToLanguage(it)?.lowercase() ?: return@mapNotNull null
        } ?: listOf()
      }
    }

    unwrapBundle(savedInstanceState)
    unwrapBundle(arguments)

    observe(viewModel.allLinks) {
      allLinks = it
      //currentSelectedLink = allLinks.first()
    }
    observe(viewModel.currentLinkIndex) {
      normalSafeApiCall {
        startPlayer(0.coerceAtLeast(it))
      }
    }

    observe(viewModel.currentSubs) { set ->
      val setOfSub = mutableSetOf<SubtitleData>()
      if (langFilterList.isNotEmpty() && filterSubByLang) {
        Log.i("subfilter", "Filtering subtitle")
        langFilterList.forEach { lang ->
          Log.i("subfilter", "Lang: $lang")
          setOfSub += set.filter {
            it.name.contains(lang, ignoreCase = true) ||
              it.origin != SubtitleOrigin.URL
          }
        }
        currentSubs = setOfSub
      } else {
        currentSubs = set
      }

      player.setActiveSubtitles(set)

      // If the file is downloaded then do not select auto select the subtitles
      // Downloaded subtitles cannot be selected immediately after loading since
      // player.getCurrentPreferredSubtitle() cannot fetch data from non-loaded subtitles
      // Resulting in unselecting the downloaded subtitle
      if (set.lastOrNull()?.origin != SubtitleOrigin.DOWNLOADED_FILE) {
        autoSelectSubtitles()
      }
    }

    observe(viewModel.currentSubtitleIndex) { index ->
      if (index >= 0 && index < currentSubs.size)
        setSubtitles(currentSubs.elementAt(index))
    }

    preferredAutoSelectSubtitles = context?.getAutoSelectLanguageISO639_1()
//    binding?.overlayLoadingSkipButton?.setOnClickListener {
//      startPlayer()
//    }

    binding?.playerLoadingGoBack?.setOnClickListener {
      exitFullscreen()
      player.release()
      activity?.popCurrentPage()
    }
  }
}
