package com.example.vkbookandroid

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.example.pult.android.DataFragment
import android.os.StrictMode
import android.content.pm.ApplicationInfo

class MainActivity : AppCompatActivity() {
    
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var pagerAdapter: MainPagerAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Включаем StrictMode только в debug для раннего обнаружения медленных операций на UI-потоке
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
        }
        setContentView(R.layout.activity_main)
        
        // Инициализация ViewPager2 и TabLayout
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)
        viewPager.isUserInputEnabled = false // Отключаем свайп между вкладками
        
        pagerAdapter = MainPagerAdapter(this)
        viewPager.adapter = pagerAdapter
        viewPager.offscreenPageLimit = 1 // держим только текущую и одну соседнюю страницу в памяти
        
        // Связывание TabLayout с ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Сигналы БЩУ"
                1 -> "Арматура"
                2 -> "Схемы"
                else -> ""
            }
        }.attach()

        // Принудительно подгружаем текущую вкладку сразу после первой разметки
        viewPager.postOnAnimation {
            (pagerAdapter.getFragment(0) as? DataFragment)?.ensureDataLoaded()
        }

        // Ленивая, но ускоренная подгрузка при переключении вкладок
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                when (position) {
                    0 -> (pagerAdapter.getFragment(0) as? DataFragment)?.ensureDataLoaded()
                    1 -> (pagerAdapter.getFragment(1) as? ArmatureFragment)?.ensureDataLoaded()
                    // 2: SchemesFragment не требует предварительной подгрузки данных
                }
            }
        })
    }
    
}
