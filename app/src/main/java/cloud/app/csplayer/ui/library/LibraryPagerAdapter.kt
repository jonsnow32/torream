package cloud.app.csplayer.ui.library

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import cloud.app.csplayer.torrent.TorrentFragment

class LibraryPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> HistoryFragment()
            1 -> FavoriteFragment()
            2 -> TorrentFragment()
            else -> throw IllegalStateException("Invalid position $position")
        }
    }
}

