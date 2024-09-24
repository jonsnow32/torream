package cloud.app.csplayer.utils

import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** NOTE: Only one observer at a time per value */
fun <T> LifecycleOwner.observe(liveData: LiveData<T>, action: (t: T) -> Unit) {
    liveData.removeObservers(this)
    liveData.observe(this) { it?.let { t -> action(t) } }
}

/** NOTE: Only one observer at a time per value */
fun <T> LifecycleOwner.observeNullable(liveData: LiveData<T>, action: (t: T) -> Unit) {
    liveData.removeObservers(this)
    liveData.observe(this) { action(it) }
}


fun <T> LifecycleOwner.observe(flow: Flow<T>, block: suspend (T) -> Unit) {
  lifecycleScope.launch {
    flow.flowWithLifecycle(lifecycle).collectLatest(block)
  }
}

fun <T> Fragment.observe(flow: Flow<T>, callback: suspend (T) -> Unit) {
  viewLifecycleOwner.observe(flow, callback)
}
