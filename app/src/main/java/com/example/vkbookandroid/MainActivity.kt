package com.example.vkbookandroid

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.example.pult.android.DataFragment
import android.os.StrictMode
import android.content.pm.ApplicationInfo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type

class MainActivity : AppCompatActivity() {
    
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var pagerAdapter: MainPagerAdapter
    private var sharedSearchQuery: String = ""
    
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
        // Глобально отключаем звуки кликов у корневого layout
        findViewById<android.view.View>(android.R.id.content)?.let { root ->
            root.isSoundEffectsEnabled = false
        }
        
        // Инициализация ViewPager2 и TabLayout
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)
        viewPager.isUserInputEnabled = false // Отключаем свайп между вкладками
        
        pagerAdapter = MainPagerAdapter(this)
        viewPager.adapter = pagerAdapter
        viewPager.offscreenPageLimit = 1 // держим только текущую и одну соседнюю страницу в памяти

        // Минимальная высота вкладок для удобного тапа
        tabLayout.minimumHeight = (resources.displayMetrics.density * 56).toInt()
        // Корректный отступ с учётом системных панелей (статус-бар/вырезы)
        ViewCompat.setOnApplyWindowInsetsListener(tabLayout) { v, insets ->
            val systemBars = insets.getInsets(Type.systemBars())
            val extraTop = (v.resources.displayMetrics.density * 8).toInt()
            v.setPadding(v.paddingLeft, systemBars.top + extraTop, v.paddingRight, v.paddingBottom)
            insets
        }
        
        // Связывание TabLayout с ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Сигналы БЩУ"
                1 -> "Арматура"
                2 -> "Схемы"
                else -> ""
            }
        }.attach()

        // Принудительно подгружаем АКТУАЛЬНУЮ вкладку после первой разметки (после ротации сохранится текущая)
        viewPager.postOnAnimation {
            ensureCurrentTabLoaded()
        }

        // Ленивая, но ускоренная подгрузка при переключении вкладок
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                ensureTabLoaded(position)
                // При переключении вкладки применим общий поисковый запрос к новой вкладке
                applySharedSearchToFragments()
            }
        })

    }
    
    override fun onResume() {
        super.onResume()
        // После ротации гарантируем, что видимая вкладка подгружена и отрисована
        ensureCurrentTabLoaded()
    }

    private fun ensureCurrentTabLoaded() {
        ensureTabLoaded(viewPager.currentItem)
    }

    private fun ensureTabLoaded(position: Int) {
        val tag = "f$position"
        val frag = supportFragmentManager.findFragmentByTag(tag)
        when (frag) {
            is org.example.pult.android.DataFragment -> frag.ensureDataLoaded()
            is com.example.vkbookandroid.ArmatureFragment -> frag.ensureDataLoaded()
            else -> {
                // Если Fragment ещё не найден по тегу, попробуем через адаптер как запасной вариант
                when (position) {
                    0 -> (pagerAdapter.getFragment(0) as? org.example.pult.android.DataFragment)?.ensureDataLoaded()
                    1 -> (pagerAdapter.getFragment(1) as? com.example.vkbookandroid.ArmatureFragment)?.ensureDataLoaded()
                }
            }
        }
    }

    fun onFragmentSearchQueryChanged(query: String) {
        if (query == sharedSearchQuery) return
        sharedSearchQuery = query
        applySharedSearchToFragments()
    }

    private fun applySharedSearchToFragments() {
        // Пробуем найти уже созданные фрагменты по тегам ViewPager2
        (supportFragmentManager.findFragmentByTag("f0") as? org.example.pult.android.DataFragment)?.setSearchQueryExternal(sharedSearchQuery)
        (supportFragmentManager.findFragmentByTag("f1") as? com.example.vkbookandroid.ArmatureFragment)?.setSearchQueryExternal(sharedSearchQuery)
        // Фоллбек через адаптер
        (pagerAdapter.getFragment(0) as? org.example.pult.android.DataFragment)?.setSearchQueryExternal(sharedSearchQuery)
        (pagerAdapter.getFragment(1) as? com.example.vkbookandroid.ArmatureFragment)?.setSearchQueryExternal(sharedSearchQuery)
    }

    
    
}
