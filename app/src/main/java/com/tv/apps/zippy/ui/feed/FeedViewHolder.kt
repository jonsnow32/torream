package com.tv.apps.zippy.ui.feed

import android.view.View
import androidx.recyclerview.widget.RecyclerView

abstract class FeedViewHolder<T : FeedData>(view: View) : RecyclerView.ViewHolder(view) {
    abstract fun bind(feed: T)
    open fun canBeSwiped(): Boolean = false
    open fun onSwipe(): T? = null
}
