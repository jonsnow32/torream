package cloud.app.csplayer.ui.library.download

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import cloud.app.csplayer.databinding.FragmentLibraryListBinding
import cloud.app.csplayer.ui.library.LibraryViewModel
import cloud.app.csplayer.utils.AutoClearedValue.Companion.autoCleared
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class DownloadFragment : Fragment() {
    private var binding by autoCleared<FragmentLibraryListBinding>()
    private val torrentViewModel: TorrentViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLibraryListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeData()
    }

    private fun setupRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        // TODO: Set adapter when data model is ready
    }

    private fun observeData() {
        // TODO: Observe ViewModel data for download items
    }
}
