package com.example.vkbookandroid

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.example.pult.android.DataFragment

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    private val fragments = mutableMapOf<Int, Fragment>()
    private var visiblePositions: MutableList<Int> = mutableListOf(0, 1, 2, 3, 4, 5)

    override fun getItemCount(): Int = visiblePositions.size

    override fun createFragment(position: Int): Fragment {
        val globalPos = visiblePositions[position]
        android.util.Log.d("MainPagerAdapter", "createFragment called: position=$position, globalPos=$globalPos")
        val fragment = when (globalPos) {
            0 -> DataFragment()
            1 -> ArmatureFragment()
            2 -> SchemesFragment()
            3 -> EditorFragment()
            4 -> ScheduleFragment()
            5 -> ChecksScheduleFragment()
            else -> throw IllegalArgumentException("Invalid position $globalPos")
        }
        fragments[globalPos] = fragment
        android.util.Log.d("MainPagerAdapter", "Fragment created for globalPos=$globalPos: ${fragment.javaClass.simpleName}")
        return fragment
    }

    override fun getItemId(position: Int): Long {
        return visiblePositions[position].toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return visiblePositions.contains(itemId.toInt())
    }

    fun setVisiblePositions(newPositions: List<Int>) {
        android.util.Log.d("MainPagerAdapter", "setVisiblePositions called: $newPositions")
        visiblePositions.clear()
        visiblePositions.addAll(newPositions)
        android.util.Log.d("MainPagerAdapter", "New visible positions: $visiblePositions")
        notifyDataSetChanged()
    }

    fun getFragmentByGlobalPosition(globalPosition: Int): Fragment? {
        return fragments[globalPosition]
    }

    fun getGlobalPositionAt(localIndex: Int): Int {
        return visiblePositions.getOrElse(localIndex) { 0 }
    }

    fun getLocalIndex(globalPosition: Int): Int? {
        val index = visiblePositions.indexOf(globalPosition)
        return if (index >= 0) index else null
    }

    fun getVisiblePositions(): List<Int> = visiblePositions.toList()
}