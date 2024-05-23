package cloud.app.csplayer.ui.browse

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import cloud.app.csplayer.databinding.FragmentBrowseBinding

class BrowseFragment: Fragment() {
  lateinit var binding : FragmentBrowseBinding
  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    binding = FragmentBrowseBinding.inflate(layoutInflater)
    return binding.root;
  }
}
