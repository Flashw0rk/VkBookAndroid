package org.example.pult.android

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.vkbookandroid.AppExcelDataManager
import com.example.vkbookandroid.R
import com.example.vkbookandroid.SignalsAdapter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Button
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.Toast

// Extension function to convert dp to pixels
fun Context.dpToPx(dp: Int): Int {
    return (dp * resources.displayMetrics.density).toInt()
}

class DataFragment : Fragment(), com.example.vkbookandroid.RefreshableFragment {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SignalsAdapter
    private lateinit var excelDataManager: AppExcelDataManager
    private lateinit var excelRepository: com.example.vkbookandroid.ExcelRepository
    private var currentColumnWidths: MutableMap<String, Int> = mutableMapOf()
    private lateinit var searchView: SearchView
    private lateinit var toggleResizeModeButton: Button
    private var isResizingMode: Boolean = false
    private var isDataLoaded: Boolean = false
    private var isLoadingPage: Boolean = false
    private var nextStartRow: Int = 0
    private val pageSize: Int = 100
    private var pagingSession: com.example.vkbookandroid.ExcelPagingSession? = null
    private var lastMeasuredListWidth: Int = 0
    private var cachedSession: com.example.vkbookandroid.PagingSession? = null
    private var lastHeaders: List<String> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_data, container, false)
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        recyclerView.setHasFixedSize(true)
        recyclerView.setItemViewCacheSize(20)
        adapter = SignalsAdapter(
            emptyList(),
            isResizingMode,
            onColumnResize = { columnIndex, newWidth, action ->
                val headerName = adapter.headers.getOrNull(columnIndex)
                if (headerName != null) {
                    currentColumnWidths[headerName] = newWidth

                    // Update adapter's internal columnWidths map immediately for real-time feedback
                    adapter.updateColumnWidths(columnIndex, newWidth, false) // Do not notify here

                    // Save column widths to SharedPreferences and update adapter only on ACTION_UP or ACTION_CANCEL
                    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                        val sharedPrefs = requireContext().getSharedPreferences("ColumnWidths", Context.MODE_PRIVATE)
                        sharedPrefs.edit().putString("Oborudovanie_BSCHU.xlsx", Gson().toJson(currentColumnWidths)).apply()
                        Log.d("DataFragment", "Column $headerName resized to $newWidth. Saved: $currentColumnWidths")
                        adapter.updateColumnWidths(columnIndex, newWidth, true) // Notify here for final update
                    }
                }
            },
            onRowClick = null,
            hidePdfSchemeColumn = false
        )
        recyclerView.adapter = adapter
        excelDataManager = AppExcelDataManager(requireContext().applicationContext)
        val baseUrl = getString(com.example.vkbookandroid.R.string.remote_base_url)
        val fileProvider: com.example.vkbookandroid.IFileProvider = if (baseUrl.isNotEmpty()) {
            com.example.vkbookandroid.RemoteFileProvider(requireContext(), baseUrl)
        } else {
            com.example.vkbookandroid.FileProvider(requireContext())
        }
        excelRepository = com.example.vkbookandroid.ExcelRepository(requireContext(), fileProvider)
        searchView = view.findViewById(R.id.search_view)
        toggleResizeModeButton = view.findViewById(R.id.toggle_resize_mode_button)

        setupSearch()
        setupToggleResizeModeButton()
        attachWidthAutoScaler()
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Загрузка будет инициирована из onResume или извне через ensureDataLoaded()
        // Гарантируем первичную разметку и видимость после ротации
        view.post {
            recyclerView.adapter?.notifyDataSetChanged()
            recyclerView.requestLayout()
            val original = adapter.getOriginalData()
            if (adapter.itemCount <= 1 && original.isNotEmpty() && lastHeaders.isNotEmpty()) {
                adapter.updateData(original, lastHeaders, currentColumnWidths, isResizingMode, updateOriginal = false)
            }
            recyclerView.visibility = View.VISIBLE
        }
        // Отключаем звуки кликов у всего дерева фрагмента
        view.isSoundEffectsEnabled = false
    }

    override fun onResume() {
        super.onResume()
        if (isVisible) ensureDataLoaded()
        // После ротации иногда список не перерисовывается до переключения вкладки — форсируем ребайндинг
        recyclerView.post {
            recyclerView.adapter?.notifyDataSetChanged()
            recyclerView.requestLayout()
            val original = adapter.getOriginalData()
            if (adapter.itemCount <= 1 && original.isNotEmpty() && lastHeaders.isNotEmpty()) {
                adapter.updateData(original, lastHeaders, currentColumnWidths, isResizingMode, updateOriginal = false)
            }
            recyclerView.visibility = View.VISIBLE
        }
    }

    override fun onStart() {
        super.onStart()
        ensureDataLoaded()
    }

    private fun setupSearch() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                filterData(query.orEmpty())
                (activity as? com.example.vkbookandroid.MainActivity)?.onFragmentSearchQueryChanged(query.orEmpty())
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterData(newText.orEmpty())
                (activity as? com.example.vkbookandroid.MainActivity)?.onFragmentSearchQueryChanged(newText.orEmpty())
                return true
            }
        })

        // Настройка кнопки очистки
        searchView.setOnCloseListener {
            // Очищаем поиск при нажатии на крестик
            searchView.setQuery("", false)
            filterData("")
            (activity as? com.example.vkbookandroid.MainActivity)?.onFragmentSearchQueryChanged("")
            true // Возвращаем true, чтобы SearchView не закрывался
        }

        // Настройка текста/подсказки (используем AppCompat id, а не android:id)
        val searchAutoComplete = searchView.findViewById<androidx.appcompat.widget.SearchView.SearchAutoComplete>(
            androidx.appcompat.R.id.search_src_text
        )
        searchAutoComplete?.apply {
            setTextColor(android.graphics.Color.BLACK)
            setHintTextColor(android.graphics.Color.parseColor("#808080"))
            textSize = 14f
        }
    }

    fun filterData(query: String) {
        // Делегируем фильтрацию адаптеру, чтобы одновременно выставлялся currentSearchQuery для подсветки
        adapter.filter(query)
    }

    fun setSearchQueryExternal(query: String) {
        if (!isAdded) return
        if (!::searchView.isInitialized) return
        if (searchView.query?.toString() == query) return
        searchView.setQuery(query, false)
        filterData(query)
    }

    private fun setupToggleResizeModeButton() {
        toggleResizeModeButton.setOnClickListener {
            isResizingMode = !isResizingMode
            toggleResizeModeButton.text = if (isResizingMode) "Сохранить" else "Редактировать"
            adapter.setResizingMode(isResizingMode)
            // Отключаем/включаем прокрутку RecyclerView: оставим включённой, но не будем перехватывать события сверху
            recyclerView.isNestedScrollingEnabled = true
            Log.d("DataFragment", "Resizing mode toggled: $isResizingMode")
        }
    }

    private val resizeTouchListener = object : RecyclerView.OnItemTouchListener {
        override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
            // Не перехватываем события: пусть доходят до дочерних resize-хэндлов
            return false
        }

        override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
            // Ничего не делаем: обработка в хэндлах
        }

        override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
    }

    private fun loadSignalsData() {
        Log.d("DataFragment", "=== STARTING BSCHU DATA LOADING ===")
        Log.d("DataFragment", "Fragment state: isAdded=${isAdded}, isVisible=${isVisible}, isResumed=${isResumed}")
        Log.d("DataFragment", "Context available: ${::excelRepository.isInitialized}")
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d("DataFragment", "Starting data loading in IO thread")
                
                // Открываем одну сессию и используем её для widths/headers/данных без повторного чтения файла
                // 1) Быстрый старт из кэша, если он есть
                Log.d("DataFragment", "Attempting to open cached session...")
                cachedSession = excelRepository.openCachedSessionBschu(pageSize)
                Log.d("DataFragment", "Cached session result: ${cachedSession != null}")
                
                val sessionForInitial = cachedSession ?: run {
                    Log.d("DataFragment", "No cached session, opening paging session...")
                    // Если кэша нет — временно откроем Excel для получения метаданных
                    pagingSession = excelRepository.openPagingSessionBschu()
                    Log.d("DataFragment", "Paging session opened: ${pagingSession != null}")
                    pagingSession as com.example.vkbookandroid.PagingSession
                }

                val initialColumnWidths = sessionForInitial.getColumnWidths()

                if (initialColumnWidths.isNotEmpty()) {
                    val sharedPrefs = requireContext().getSharedPreferences("ColumnWidths", Context.MODE_PRIVATE)
                    val type = object : TypeToken<MutableMap<String, Int>>() {}.type
                    val savedColumnWidths: MutableMap<String, Int> = Gson().fromJson(sharedPrefs.getString("Oborudovanie_BSCHU.xlsx", null), type) ?: mutableMapOf()
                    
                    // Merge saved widths with initial widths
                    initialColumnWidths.forEach { (header, width) ->
                        if (!savedColumnWidths.containsKey(header)) {
                            savedColumnWidths[header] = width
                        } else if (savedColumnWidths[header] == null) {
                            savedColumnWidths[header] = width
                        }
                    }
                    currentColumnWidths = savedColumnWidths
                    Log.d("DataFragment", "Loaded and merged column widths: $currentColumnWidths")
                } else {
                    Log.w("DataFragment", "Initial column widths are empty.")
                    currentColumnWidths = mutableMapOf() // Ensure it's not null/empty for adapter
                }

                // Load headers
                val headers = sessionForInitial.getHeaders()
                Log.d("DataFragment", "=== BSCHU DATA DIAGNOSTICS ===")
                Log.d("DataFragment", "Loaded headers: $headers")
                Log.d("DataFragment", "Headers count: ${headers.size}")

                // Load first page
                val firstPage = sessionForInitial.readRange(0, pageSize)
                nextStartRow = firstPage.size
                Log.d("DataFragment", "Loaded first page count: ${firstPage.size}")
                Log.d("DataFragment", "First page data: $firstPage")
                Log.d("DataFragment", "=== END BSCHU DATA DIAGNOSTICS ===")

                withContext(Dispatchers.Main) {
                    Log.d("DataFragment", "Updating UI with data: headers=${headers.size}, firstPage=${firstPage.size}")
                    Log.d("DataFragment", "Fragment still valid: isAdded=${isAdded}, isVisible=${isVisible}")
                    
                    if (!isAdded) {
                        Log.w("DataFragment", "Fragment no longer added, skipping UI update")
                        return@withContext
                    }
                    
                    lastHeaders = headers
                    adapter.updateData(firstPage, headers, currentColumnWidths, isResizingMode, updateOriginal = true)
                    isDataLoaded = true
                    attachPaging()
                    
                    Log.d("DataFragment", "UI updated successfully. Adapter item count: ${adapter.itemCount}")
                    Log.d("DataFragment", "RecyclerView visibility: ${recyclerView.visibility}")
                }

                // 2) Тихая пересборка кэша в фоне и обновление по готовности
                excelRepository.refreshCacheBschu(pageSize) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val newCached = excelRepository.openCachedSessionBschu(pageSize)
                        if (newCached != null) {
                            val newHeaders = newCached.getHeaders()
                            val newFirstPage = newCached.readRange(0, pageSize)
                            nextStartRow = newFirstPage.size
                            withContext(Dispatchers.Main) {
                                lastHeaders = newHeaders
                                adapter.updateData(newFirstPage, newHeaders, currentColumnWidths, isResizingMode, updateOriginal = true)
                            }
                            cachedSession = newCached
                            pagingSession?.close()
                            pagingSession = null
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DataFragment", "Error loading signals data", e)
                Log.e("DataFragment", "Exception details: ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
                
                // Показываем ошибку пользователю
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Toast.makeText(requireContext(), "Ошибка загрузки данных БЩУ: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    

    private fun attachPaging() {
        // Prefetch next page when близко к концу
        recyclerView.clearOnScrollListeners()
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                val lm = rv.layoutManager as? LinearLayoutManager ?: return
                val last = lm.findLastVisibleItemPosition()
                val total = adapter.itemCount
                val prefetchThreshold = 30
                if (!isLoadingPage && last >= total - prefetchThreshold) {
                    loadNextPage()
                }
            }
        })
    }

    private fun attachWidthAutoScaler() {
        recyclerView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val container = recyclerView.parent as? View ?: return@addOnLayoutChangeListener
            val newW = container.width
            if (newW <= 0) return@addOnLayoutChangeListener
            if (!isDataLoaded) return@addOnLayoutChangeListener
            if (lastMeasuredListWidth == 0) {
                lastMeasuredListWidth = newW
                return@addOnLayoutChangeListener
            }
            if (newW == lastMeasuredListWidth) return@addOnLayoutChangeListener
            val oldW = lastMeasuredListWidth
            val factor = newW.toFloat() / oldW.toFloat()
            if (!factor.isFinite() || factor <= 0f) return@addOnLayoutChangeListener

            // Масштабируем текущие ширины колонок пропорционально (без искусственного минимума)
            val scaled = mutableMapOf<String, Int>()
            for (header in adapter.headers) {
                val cur = currentColumnWidths[header] ?: continue
                val nw = (cur * factor).toInt().coerceAtLeast(1)
                scaled[header] = nw
            }
            if (scaled.isEmpty()) return@addOnLayoutChangeListener
            currentColumnWidths.putAll(scaled)
            lastMeasuredListWidth = newW

            // Применяем к адаптеру
            adapter.headers.forEachIndexed { idx, _ ->
                val newWForCol = currentColumnWidths[adapter.headers[idx]] ?: return@forEachIndexed
                val notify = idx == adapter.headers.lastIndex
                adapter.updateColumnWidths(idx, newWForCol, notify)
            }

            // Сохраняем
            val sharedPrefs = requireContext().getSharedPreferences("ColumnWidths", Context.MODE_PRIVATE)
            sharedPrefs.edit().putString("Oborudovanie_BSCHU.xlsx", Gson().toJson(currentColumnWidths)).apply()
        }
    }

    private fun loadNextPage() {
        if (isLoadingPage) return
        isLoadingPage = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val session = cachedSession ?: pagingSession ?: return@launch
                val page = session.readRange(nextStartRow, pageSize)
                if (page.isNotEmpty()) {
                    nextStartRow += page.size
                    withContext(Dispatchers.Main) {
                        adapter.appendData(page)
                    }
                }
            } catch (e: Exception) {
                Log.e("DataFragment", "Paging load error", e)
            } finally {
                isLoadingPage = false
            }
        }
    }

    fun ensureDataLoaded() {
        Log.d("DataFragment", "ensureDataLoaded called: isDataLoaded=$isDataLoaded, isAdded=$isAdded, isVisible=$isVisible")
        if (!isDataLoaded) {
            Log.d("DataFragment", "Data not loaded, starting loadSignalsData()")
            loadSignalsData()
        } else {
            Log.d("DataFragment", "Data already loaded, skipping")
        }
    }
    
    // Реализация интерфейса RefreshableFragment
    override fun refreshData() {
        Log.d("DataFragment", "refreshData() called - forcing data reload")
        isDataLoaded = false
        loadSignalsData()
    }
    
    override fun isDataLoaded(): Boolean {
        return isDataLoaded
    }
    
    override fun getWatchedFilePath(): String? {
        return try {
            val baseUrl = getString(com.example.vkbookandroid.R.string.remote_base_url)
            if (baseUrl.isNotEmpty()) {
                // Для удаленного режима - путь к файлу в data директории
                val dataDir = requireContext().filesDir.resolve("data")
                dataDir.resolve("Oborudovanie_BSCHU.xlsx").absolutePath
            } else {
                // Для локального режима - путь к файлу в assets
                null // Assets файлы не отслеживаются
            }
        } catch (e: Exception) {
            Log.e("DataFragment", "Error getting watched file path", e)
            null
        }
    }
}
