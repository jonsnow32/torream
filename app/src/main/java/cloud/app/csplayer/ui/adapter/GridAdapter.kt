package cloud.app.csplayer.ui.adapter

import android.graphics.Rect
import android.view.View
import androidx.core.util.toKotlinPair
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cloud.app.csplayer.R
import cloud.app.csplayer.utils.UIHelper.resolveStyledDimension
import cloud.app.csplayer.utils.UIHelper.toPx
import kotlin.math.floor

interface GridAdapter {
  val adapter: RecyclerView.Adapter<*>
  fun getSpanSize(position: Int, width: Int, count: Int): Int

  /**
   * Concat adapter that combines multiple GridAdapters and manages their span sizes
   */
  class Concat(
    vararg adapters: GridAdapter
  ) : GridAdapter {
    override val adapter = ConcatAdapter(adapters.map { it.adapter })
    private val getSpanSizeMap = adapters.mapIndexed { index, gridAdapter ->
      gridAdapter.adapter to gridAdapter::getSpanSize
    }.toMap()

    override fun getSpanSize(position: Int, width: Int, count: Int): Int {
      val (wrappedAdapter, pos) = adapter.getWrappedAdapterAndPosition(position).toKotlinPair()
      val getSpanSize = getSpanSizeMap[wrappedAdapter]
        ?: throw IllegalStateException("No span size function found for adapter: ${wrappedAdapter.javaClass.name}")
      return getSpanSize(pos, width, count)
    }
  }

  companion object {
    fun configureGridLayout(
      recycler: RecyclerView, gridAdapter: GridAdapter, even: Boolean = false, itemSpacingDp: Int = 8
    ) {
      val context = recycler.context
      val itemWidth = context.resolveStyledDimension(R.attr.itemCoverSize)
      val itemSpacing = itemSpacingDp.toPx

      fun calculateSpanCount(totalWidth: Int): Int {
        // Add spacing between items
        val effectiveItemWidth = itemWidth + itemSpacing
        val calc = floor(totalWidth.toFloat() / effectiveItemWidth).toInt()
        return if (calc > 1) calc - if (even) calc % 2 else 0 else 1
      }

      val displayWidth = context.resources.displayMetrics.widthPixels
      val initialSpanCount = calculateSpanCount(displayWidth)

      val layoutManager = GridLayoutManager(context, initialSpanCount)

      layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int {
          val width = recycler.width - recycler.paddingLeft - recycler.paddingRight
          return gridAdapter.getSpanSize(position, width, layoutManager.spanCount)
        }
      }
      recycler.layoutManager = layoutManager
      recycler.apply {
        adapter = gridAdapter.adapter


        doOnLayout {
          val width = it.width - it.paddingLeft - it.paddingRight
          if (width <= 0) return@doOnLayout

          val newSpanCount = calculateSpanCount(width)
          if (layoutManager.spanCount != newSpanCount) {
            layoutManager.spanCount = newSpanCount
//            layoutManager.spanSizeLookup.invalidateSpanIndexCache()
//            recycledViewPool.clear()
//            requestLayout()
          }
        }
      }

      // Optional: Add item decoration for visual spacing
//      if (recycler.itemDecorationCount == 0) {
//        recycler.addItemDecoration(GridSpacingDecoration(itemSpacing, false))
//      }
    }

    class GridSpacingDecoration(
      private val spacing: Int,         // spacing in px
      private val includeEdge: Boolean // whether include spacing at edges
    ) : RecyclerView.ItemDecoration() {

      override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
      ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return

        val layoutManager = parent.layoutManager as? GridLayoutManager
        val spanCount = layoutManager?.spanCount ?: 1
        val spanLookup = layoutManager?.spanSizeLookup

        // Use spanIndex so it works for items that span multiple columns
        val spanIndex = spanLookup?.getSpanIndex(position, spanCount) ?: (position % spanCount)
        val spanSize = spanLookup?.getSpanSize(position) ?: 1
        // Use getSpanGroupIndex to determine the row (group). This fixes missing top spacing for items that span columns.
        val groupIndex = spanLookup?.getSpanGroupIndex(position, spanCount) ?: (position / spanCount)

        // For items that span multiple columns we treat them as starting at spanIndex and width = spanSize
        // We compute left/right using the starting column.
        val column = spanIndex // 0-based start column

        if (includeEdge) {
          // left: spacing - column * spacing / spanCount
          outRect.left = spacing - column * spacing / spanCount
          // right: (column + spanSize) * spacing / spanCount ?? We want equal distribution:
          outRect.right = (column + spanSize) * spacing / spanCount - column * spacing / spanCount
          // top for first row items (groupIndex == 0)
          if (groupIndex == 0) outRect.top = spacing
          outRect.bottom = spacing
        } else {
          outRect.left = column * spacing / spanCount
          outRect.right = spacing - (column + spanSize) * spacing / spanCount + column * spacing / spanCount
          // top for all rows except first (groupIndex > 0)
          if (groupIndex > 0) outRect.top = spacing
          // bottom left 0 so rows are spaced by top of next row
        }
      }
    }

  }
}

