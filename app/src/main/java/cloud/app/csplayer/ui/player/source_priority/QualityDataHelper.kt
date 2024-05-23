package cloud.app.csplayer.ui.player.source_priority

import androidx.annotation.StringRes
import cloud.app.csplayer.R
import cloud.app.csplayer.utils.DataStore.getKey
import cloud.app.csplayer.utils.DataStore.removeKey
import cloud.app.csplayer.utils.DataStore.setKey
import cloud.app.csplayer.utils.Qualities
import cloud.app.csplayer.utils.UiText
import cloud.app.csplayer.utils.Utils
import cloud.app.csplayer.utils.Utils.debugAssert
import cloud.app.csplayer.utils.txt

object QualityDataHelper {
    private const val VIDEO_SOURCE_PRIORITY = "video_source_priority"
    private const val VIDEO_PROFILE_NAME = "video_profile_name"
    private const val VIDEO_QUALITY_PRIORITY = "video_quality_priority"
    private const val VIDEO_PROFILE_TYPE = "video_profile_type"
    private const val DEFAULT_SOURCE_PRIORITY = 1
    /**
     * Automatically skip loading links once this priority is reached
     **/
    const val AUTO_SKIP_PRIORITY = 10

    /**
     * Must be higher than amount of QualityProfileTypes
     **/
    private const val PROFILE_COUNT = 7

    /**
     * Unique guarantees that there will always be one of this type in the profile list.
     **/
    enum class QualityProfileType(@StringRes val stringRes: Int, val unique: Boolean) {
        None(R.string.none, false),
        WiFi(R.string.wifi, true),
        Data(R.string.mobile_data, true)
    }

    data class QualityProfile(
      val name: UiText,
      val id: Int,
      val type: QualityProfileType
    )

    fun getSourcePriority(profile: Int, name: String?): Int {
        if (name == null) return DEFAULT_SOURCE_PRIORITY
        return Utils.activity?.getKey(
            "$VIDEO_SOURCE_PRIORITY/$profile",
            name,
            DEFAULT_SOURCE_PRIORITY
        ) ?: DEFAULT_SOURCE_PRIORITY
    }

    fun setSourcePriority(profile: Int, name: String, priority: Int) {
      Utils.activity?.setKey("$VIDEO_SOURCE_PRIORITY/$profile", name, priority)
    }

    fun setProfileName(profile: Int, name: String?) {
        val path = "$VIDEO_PROFILE_NAME/$profile"
        if (name == null) {
          Utils.activity?.removeKey(path)
        } else {
          Utils.activity?.setKey(path, name.trim())
        }
    }

    fun getProfileName(profile: Int): UiText {
        return Utils.activity?.getKey<String>("$VIDEO_PROFILE_NAME/$profile")?.let { txt(it) }
            ?: txt(R.string.profile_number, profile)
    }

    fun getQualityPriority(profile: Int, quality: Qualities): Int {
        return Utils.activity?.getKey(
            "$VIDEO_QUALITY_PRIORITY/$profile",
            quality.value.toString(),
            quality.defaultPriority
        ) ?: quality.defaultPriority
    }

    fun setQualityPriority(profile: Int, quality: Qualities, priority: Int) {
      Utils.activity?.setKey(
            "$VIDEO_QUALITY_PRIORITY/$profile",
            quality.value.toString(),
            priority
        )
    }

    fun getQualityProfileType(profile: Int): QualityProfileType {
        return Utils.activity?.getKey("$VIDEO_PROFILE_TYPE/$profile") ?: QualityProfileType.None
    }

    fun setQualityProfileType(profile: Int, type: QualityProfileType?) {
        val path = "$VIDEO_PROFILE_TYPE/$profile"
        if (type == QualityProfileType.None) {
          Utils.activity?.removeKey(path)
        } else {
          Utils.activity?.setKey(path, type)
        }
    }

    /**
     * Gets all quality profiles, always includes one profile with WiFi and Data
     * Must under all circumstances at least return one profile
     **/
    fun getProfiles(): List<QualityProfile> {
        val availableTypes = QualityProfileType.values().toMutableList()
        val profiles = (1..PROFILE_COUNT).map { profileNumber ->
            // Get the real type
            val type = getQualityProfileType(profileNumber)

            // This makes it impossible to get more than one of each type
            // Duplicates will be turned to None
            val uniqueType = if (type.unique && !availableTypes.remove(type)) {
              QualityProfileType.None
            } else {
                type
            }

            QualityProfile(
                getProfileName(profileNumber),
                profileNumber,
                uniqueType
            )
        }.toMutableList()

        /**
         * If no profile of this type exists: insert it on the earliest profile with None type
         **/
        fun insertType(
          list: MutableList<QualityProfile>,
          type: QualityProfileType
        ) {
            if (list.any { it.type == type }) return
            val index =
                list.indexOfFirst { it.type == QualityProfileType.None }
            list.getOrNull(index)?.copy(type = type)
                ?.let { fixed ->
                    list.set(index, fixed)
                }
        }

        QualityProfileType.values().forEach {
            if (it.unique) insertType(profiles, it)
        }

        debugAssert({
            !QualityProfileType.values().all { type ->
                !type.unique || profiles.any { it.type == type }
            }
        }, { "All unique quality types do not exist" })

        debugAssert({
            profiles.isEmpty()
        }, { "No profiles!" })

        return profiles
    }
}
