package com.tv.apps.zippy.utils

import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

fun <T> Fragment.observe(data: LiveData<T>, block: (T) -> Unit) {
  data.observe(viewLifecycleOwner, Observer(block))
}

fun <T> Fragment.observe(flow: Flow<T>, callback: suspend (T) -> Unit) =
  viewLifecycleOwner.observe(flow, callback)


fun <T> LifecycleOwner.observe(flow: Flow<T>, block: suspend (T) -> Unit) = lifecycleScope.launch {
  flow.flowWithLifecycle(lifecycle).collectLatest(block)
}

fun <T> LifecycleOwner.collect(flow: Flow<T>, block: suspend (T) -> Unit) {
  lifecycleScope.launch { flow.collect(block) }
}
