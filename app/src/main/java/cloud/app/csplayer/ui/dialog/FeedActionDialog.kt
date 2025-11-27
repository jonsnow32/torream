package cloud.app.csplayer.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.content.ContextCompat
import cloud.app.csplayer.R
import cloud.app.csplayer.databinding.DialogFeedActionBinding
import cloud.app.csplayer.datastore.Serializer.getSerialized
import cloud.app.csplayer.datastore.Serializer.putSerialized
import cloud.app.csplayer.ui.dialog.SelectionDialog.Companion.ITEMS_SELECTED
import cloud.app.csplayer.utils.AutoClearedValue.Companion.autoCleared
import cloud.app.csplayer.utils.UIHelper.dismissSafe
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

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
  ): View {
    binding = DialogFeedActionBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    val context = this.context ?: return

    binding.apply {
      text1.text = getString(R.string.feed_action)
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
    fun newInstance(
      actionItems: List<ActionItem>
    ) = FeedActionDialog().apply {
      arguments = Bundle().apply {
        putSerialized(ITEMS_KEY, actionItems)
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
