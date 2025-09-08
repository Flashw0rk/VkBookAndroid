package com.example.vkbookandroid

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.example.pult.android.DataFragment

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    private val fragments = mutableMapOf<Int, Fragment>()

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        val fragment = when (position) {
            0 -> DataFragment()
            1 -> ArmatureFragment()
            2 -> SchemesFragment()
            else -> throw IllegalArgumentException("Invalid position $position")
        }
        fragments[position] = fragment
        return fragment
    }
    
    fun getFragment(position: Int): Fragment? {
        return fragments[position]
    }
} 