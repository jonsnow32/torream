package com.tv.apps.zippy.ui.browse

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.tv.apps.zippy.MainActivityViewModel.Companion.applyContentRect
import com.tv.apps.zippy.databinding.FragmentBrowseBinding

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

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    applyContentRect(binding.appBar, binding.swipeRefresh)
    binding.swipeRefresh.setOnRefreshListener {

    }
  }
}
