package com.example.vkbookandroid

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.example.pult.android.DataFragment

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    private val fragments = mutableMapOf<Int, Fragment>()
    private var visiblePositions: MutableList<Int> = mutableListOf(0, 1, 2, 3, 4)

    override fun getItemCount(): Int = visiblePositions.size

    override fun createFragment(position: Int): Fragment {
        val globalPos = visiblePositions[position]
        val fragment = when (globalPos) {
            0 -> DataFragment()
            1 -> ArmatureFragment()
            2 -> SchemesFragment()
            3 -> EditorFragment()
            4 -> ScheduleFragment()
            else -> throw IllegalArgumentException("Invalid position $globalPos")
        }
        fragments[globalPos] = fragment
        return fragment
    }

    override fun getItemId(position: Int): Long {
        return visiblePositions[position].toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return visiblePositions.contains(itemId.toInt())
    }

    fun setVisiblePositions(newPositions: List<Int>) {
        visiblePositions.clear()
        visiblePositions.addAll(newPositions)
        notifyDataSetChanged()
    }

    fun getFragmentByGlobalPosition(globalPosition: Int): Fragment? {
        return fragments[globalPosition]
    }

    fun getGlobalPositionAt(localIndex: Int): Int {
        return visiblePositions.getOrElse(localIndex) { 0 }
    }
}