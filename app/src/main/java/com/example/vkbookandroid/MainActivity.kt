package com.example.vkbookandroid

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import androidx.appcompat.widget.SearchView // Используем SearchView
import org.example.pult.android.DataFragment

class MainActivity : AppCompatActivity() {
    
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var searchView: SearchView // Изменено на SearchView
    private lateinit var pagerAdapter: MainPagerAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Инициализация ViewPager2 и TabLayout
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)
        viewPager.isUserInputEnabled = false // Отключаем свайп между вкладками
        searchView = findViewById(R.id.search_view) // Изменено на search_view
        
        pagerAdapter = MainPagerAdapter(this)
        viewPager.adapter = pagerAdapter
        
        // Связывание TabLayout с ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Сигналы БЩУ"
                1 -> "Арматура"
                2 -> "Схемы"
                else -> ""
            }
        }.attach()
        
        // Настройка поиска
        setupSearch()
    }
    
    private fun setupSearch() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                (pagerAdapter.getFragment(viewPager.currentItem) as? DataFragment)?.filterData(query.toString())
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                (pagerAdapter.getFragment(viewPager.currentItem) as? DataFragment)?.filterData(newText.toString())
                return true
            }
        })
    }
}
