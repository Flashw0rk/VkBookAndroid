package com.example.vkbookandroid

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {
    
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var searchEditText: TextInputEditText
    private lateinit var pagerAdapter: MainPagerAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Инициализация ViewPager2 и TabLayout
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)
        searchEditText = findViewById(R.id.searchEditText)
        
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
        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val searchText = s?.toString() ?: ""
                filterCurrentFragment(searchText)
            }
        })
    }
    
    private fun filterCurrentFragment(searchText: String) {
        // Получаем текущий фрагмент через ViewPager2
        val currentFragment = when (viewPager.currentItem) {
            0 -> pagerAdapter.getFragment(0) as? SignalsFragment
            1 -> pagerAdapter.getFragment(1) as? ArmatureFragment
            2 -> pagerAdapter.getFragment(2) as? SchemesFragment
            else -> null
        }
        
        // Проверяем, что фрагмент создан и имеет метод filterData
        currentFragment?.let { fragment ->
            when (fragment) {
                is SignalsFragment -> fragment.filterData(searchText)
                is ArmatureFragment -> fragment.filterData(searchText)
                is SchemesFragment -> fragment.filterData(searchText)
            }
        }
    }
}
