package cloud.streamless.torream.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import cloud.streamless.torream.R
import cloud.streamless.torream.databinding.DialogFeedActionBinding
import cloud.streamless.torream.datastore.Serializer.getSerialized
import cloud.streamless.torream.datastore.Serializer.putSerialized
import cloud.streamless.torream.ui.dialog.SelectionDialog.Companion.ITEMS_SELECTED
import cloud.streamless.torream.utils.AutoClearedValue.Companion.autoCleared
import cloud.streamless.torream.utils.UIHelper.dismissSafe
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.Serializable


@Serializable
data class ActionItem(
  val id: String,
  val title: String,
  val iconRes: Int?,
  val isDestructive: Boolean = false,
)

@AndroidEntryPoint
class FeedActionDialog : DockingDialog() {
  private var binding by autoCleared<DialogFeedActionBinding>()
  private val args by lazy { requireArguments() }
  private val items by lazy {
    args.getSerialized<List<ActionItem>>(ITEMS_KEY)
  }

  private val titleRez by lazy {
    args.getInt(TITLE_KEY, R.string.feed_action)
  }
  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
  ): View {
    binding = DialogFeedActionBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val context = this.context ?: return

    binding.apply {
      text1.text = getString(titleRez)
      // Setup listview1 with action items
      val itemList = items ?: emptyList()
      val adapter = object : ArrayAdapter<ActionItem>(
        context,
        R.layout.item_feed_action,
        itemList.toMutableList()
      ) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
          val v = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_feed_action, parent, false)

          val tv = v.findViewById<TextView>(R.id.feedTitle)
          val icon = v.findViewById<ImageView>(R.id.feedIcon)
          val item = getItem(position)

          tv.text = item?.title ?: ""

          // Set icon if provided
          if (item?.iconRes != null) {
            icon.setImageResource(item.iconRes)
          } else {
            icon.setImageResource(R.drawable.outline_adjust_24)
          }

          // Apply red color for destructive items
          val textColor = if (item?.isDestructive == true) {
            ContextCompat.getColor(context, android.R.color.holo_red_dark)
          } else {
            ContextCompat.getColor(context, R.color.icon_tint)
          }

          icon.imageTintList = android.content.res.ColorStateList.valueOf(textColor)
          tv.setTextColor(textColor)

          return v
        }
      }

      listview1.choiceMode = AbsListView.CHOICE_MODE_SINGLE
      listview1.adapter = adapter
      listview1.setOnItemClickListener { _, _, position, _ ->
        selectedItemIdex = position
        dialog?.dismissSafe(activity)
      }

    }
  }

  companion object {
    const val ITEMS_KEY = "feed_action_items_key"
    const val TITLE_KEY = "feed_action_title_key"
    fun newInstance(
      actionItems: List<ActionItem>,
      title: Int? = null
    ) = FeedActionDialog().apply {
      arguments = Bundle().apply {
        putSerialized(ITEMS_KEY, actionItems)
        title?.let {
          putInt(TITLE_KEY, title)
        }
      }
    }
  }

    private var selectedItemIdex = -1
    override fun getResultBundle(): Bundle? {
      return if (selectedItemIdex > -1) {
        Bundle().apply {
          putIntegerArrayList(ITEMS_SELECTED, arrayListOf(selectedItemIdex))
        }
      } else {
        null
      }
    }
  }
