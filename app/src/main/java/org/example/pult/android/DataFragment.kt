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
import androidx.lifecycle.Observer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import com.example.vkbookandroid.search.SearchManager
import com.example.vkbookandroid.search.SearchResult

// Extension function to convert dp to pixels
fun Context.dpToPx(dp: Int): Int {
    return (dp * resources.displayMetrics.density).toInt()
}

class DataFragment : Fragment(), com.example.vkbookandroid.RefreshableFragment {

    private lateinit var recyclerView: RecyclerView
    private var emptyView: android.widget.TextView? = null
    private lateinit var adapter: SignalsAdapter
    private lateinit var excelDataManager: AppExcelDataManager
    private lateinit var excelRepository: com.example.vkbookandroid.ExcelRepository
    private var currentColumnWidths: MutableMap<String, Int> = mutableMapOf()
    private lateinit var searchView: SearchView
    private lateinit var toggleResizeModeButton: Button
    private lateinit var scrollToTopButton: android.widget.ImageButton
    private lateinit var scrollToBottomButton: android.widget.ImageButton
    private var isResizingMode: Boolean = false
    private var isDataLoaded: Boolean = false
    private var isLoadingPage: Boolean = false
    private var nextStartRow: Int = 0
    private val pageSize: Int = 1500
    private var pagingSession: com.example.vkbookandroid.ExcelPagingSession? = null
    private var lastMeasuredListWidth: Int = 0
    private var cachedSession: com.example.vkbookandroid.PagingSession? = null
    private var lastHeaders: List<String> = emptyList()
    
    // Новая система поиска
    private lateinit var searchManager: SearchManager
    private var currentSearchQuery: String = ""
    private var searchJob: Job? = null
    private val queryFlow = MutableStateFlow("")
    private var nextRequestId: Int = 0
    private var activeRequestId: Int = -1
    private var lastRawQuery: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_data, container, false)
        recyclerView = view.findViewById(R.id.recyclerView)
        val hscroll: android.widget.HorizontalScrollView? = view.findViewById(R.id.hscroll)
        emptyView = view.findViewById(R.id.empty_view)
        recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        // Отключаем анимации, чтобы избежать дергания при обновлениях
        recyclerView.itemAnimator = null
        // recyclerView.setHasFixedSize(true) // Отключено из-за конфликта с wrap_content
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
                        com.example.vkbookandroid.utils.ColumnWidthManager.saveBschuColumnWidths(
                            requireContext(), 
                            currentColumnWidths, 
                            "DataFragment"
                        )
                        adapter.updateColumnWidths(columnIndex, newWidth, true) // Notify here for final update
                    }
                }
            },
            onRowClick = null,
            hidePdfSchemeColumn = false,
            onColumnReorder = { newOrder ->
                try {
                    currentColumnWidths = currentColumnWidths.toMutableMap()
                    com.example.vkbookandroid.utils.ColumnOrderManager.saveBschuColumnOrder(requireContext(), newOrder)
                } catch (_: Throwable) {}
            }
        )
        adapter.setOnAfterSearchResultsApplied {
            // Жестко сбрасываем скролл: останавливаем текущий, обнуляем горизонтальный, затем вертикальный
            try { recyclerView.stopScroll() } catch (_: Throwable) {}
            try { hscroll?.post { hscroll.scrollTo(0, 0) } } catch (_: Throwable) {}
            // Тройной скролл + после layout (вертикально к шапке)
            recyclerView.post {
                try {
                    (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
                    recyclerView.scrollToPosition(0)
                } catch (_: Throwable) {}
            }
            recyclerView.postDelayed({
                try {
                    (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
                    recyclerView.scrollToPosition(0)
                } catch (_: Throwable) {}
            }, 100)
            recyclerView.postDelayed({
                try {
                    (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
                    recyclerView.scrollToPosition(0)
                } catch (_: Throwable) {}
            }, 220)
            recyclerView.viewTreeObserver?.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    try {
                        (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
                        recyclerView.scrollToPosition(0)
                        hscroll?.scrollTo(0, 0)
                    } catch (_: Throwable) {}
                    recyclerView.viewTreeObserver?.removeOnGlobalLayoutListener(this)
                }
            })
        }
        recyclerView.adapter = adapter
        excelDataManager = AppExcelDataManager(requireContext().applicationContext)
        val baseUrl = com.example.vkbookandroid.ServerSettingsActivity.getCurrentServerUrl(requireContext())
        val fileProvider: com.example.vkbookandroid.IFileProvider = if (baseUrl != "http://10.0.2.2:8082/") {
            com.example.vkbookandroid.RemoteFileProvider(requireContext(), baseUrl)
        } else {
            com.example.vkbookandroid.FileProvider(requireContext())
        }
        excelRepository = com.example.vkbookandroid.ExcelRepository(requireContext(), fileProvider)
        searchView = view.findViewById(R.id.search_view)
        toggleResizeModeButton = view.findViewById(R.id.toggle_resize_mode_button)
        scrollToTopButton = view.findViewById(R.id.scroll_to_top_button)
        scrollToBottomButton = view.findViewById(R.id.scroll_to_bottom_button)

        // Инициализация новой системы поиска
        initializeSearchSystem()
        
        setupSearch()
        setupSearchFlow()
        setupToggleResizeModeButton()
        setupScrollButtons()
        attachWidthAutoScaler()
        
        // Загружаем данные сразу при создании фрагмента
        ensureDataLoaded()
        
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
                // При Submit прокручиваем к началу после применения результата
                recyclerView.tag = "scroll_to_top_next"
                // Отправляем запрос в Flow-пайплайн
                queryFlow.value = query.orEmpty()
                (activity as? com.example.vkbookandroid.MainActivity)?.onFragmentSearchQueryChanged(query.orEmpty())
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                val newQuery = newText.orEmpty()
                val isDeletion = newQuery.length < lastRawQuery.length

                // Мгновенный скролл к заголовку при удалении любого символа
                if (isDeletion) {
                    try { (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0) } catch (_: Throwable) {}
                }

                recyclerView.tag = "scroll_to_top_next"
                queryFlow.value = newQuery
                (activity as? com.example.vkbookandroid.MainActivity)?.onFragmentSearchQueryChanged(newQuery)
                lastRawQuery = newQuery
                return true
            }
        })

        // Настройка кнопки очистки
        searchView.setOnCloseListener {
            // Очищаем поиск при нажатии на крестик
            searchView.setQuery("", false)
            recyclerView.tag = "scroll_to_top_next"
            queryFlow.value = ""
            lastRawQuery = ""
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
        // Переведено на Flow-пайплайн: просто отправляем значение
        recyclerView.tag = "scroll_to_top_next"
        queryFlow.value = query
    }

    private fun setupSearchFlow() {
        lifecycleScope.launch {
            queryFlow
                .map { it.trim() }
                .debounce(300)
                .distinctUntilChanged()
                .flatMapLatest { normalized ->
                    flow {
                        currentSearchQuery = normalized
                        // Всегда скроллим к началу на любое изменение запроса
                        recyclerView.tag = "scroll_to_top_next"
                        val requestIdForThisQuery = (++nextRequestId)
                        activeRequestId = requestIdForThisQuery

                        if (normalized.isEmpty()) {
                            // Очистка результатов и скролл к началу
                if (isDataReadyForSearch()) {
                    adapter.clearSearchResultsOptimized()
                    recyclerView.post {
                        try { (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0) } catch (_: Throwable) {}
                        recyclerView.requestLayout()
                    }
                    // Дополнительно: показать таблицу и скрыть пустой вид при очистке
                    emptyView?.visibility = View.GONE
                    (recyclerView.parent as? View)?.visibility = View.VISIBLE
                    // Дополнительный скролл чуть позже, чтобы закрепить заголовок
                    recyclerView.postDelayed({
                        try { (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0) } catch (_: Throwable) {}
                    }, 150)
                }
                            emit(Unit)
                            return@flow
                        }

                        // Дожидаемся готовности данных/индекса, затем выполняем поиск
                        try {
                            if (::searchManager.isInitialized) {
                                if (searchManager.isIndexReady.value != true) {
                                    waitForIndexAndSearch(normalized, requestIdForThisQuery)
                                } else {
                                    performEnhancedSearch(normalized, requestIdForThisQuery)
                                }
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) {
                                // flatMapLatest уже отменил старую работу
                            } else {
                                Log.e("DataFragment", "Search error in flow", e)
                                if (isDataReadyForSearch()) adapter.clearSearchResults()
                            }
                        }
                        emit(Unit)
                    }
                }
                .collect { /* no-op, всё сделано побочно */ }
        }
    }

    private suspend fun waitForIndexAndSearch(searchText: String, requestId: Int) {
        var attempts = 0
        val maxAttempts = 30 // ~900ms c шагом 30ms
        while (attempts < maxAttempts && searchManager.isIndexReady.value != true) {
            delay(30)
            attempts++
        }
        performEnhancedSearch(searchText, requestId)
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

    private fun setupScrollButtons() {
        scrollToTopButton.setOnClickListener {
            scrollToTop()
        }
        
        scrollToBottomButton.setOnClickListener {
            scrollToBottom()
        }
    }

    private fun scrollToTop() {
        try {
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
            if (layoutManager != null) {
                layoutManager.scrollToPositionWithOffset(0, 0)
                recyclerView.smoothScrollToPosition(0)
            }
        } catch (e: Exception) {
            Log.e("DataFragment", "Error scrolling to top", e)
        }
    }

    private fun scrollToBottom() {
        try {
            val itemCount = adapter.itemCount
            if (itemCount > 0) {
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                if (layoutManager != null) {
                    layoutManager.scrollToPositionWithOffset(itemCount - 1, 0)
                    recyclerView.smoothScrollToPosition(itemCount - 1)
                }
            }
        } catch (e: Exception) {
            Log.e("DataFragment", "Error scrolling to bottom", e)
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

                // БАЗА: ширины по умолчанию = 3.5 см до тех пор, пока пользователь их не изменит
                val savedColumnWidths = com.example.vkbookandroid.utils.ColumnWidthManager.loadBschuColumnWidths(requireContext())
                currentColumnWidths = mutableMapOf()
                if (savedColumnWidths.isNotEmpty()) {
                    currentColumnWidths.putAll(savedColumnWidths)
                } else {
                    val headersForDefaults = initialColumnWidths.keys.toList()
                    val xdpi = resources.displayMetrics.xdpi
                    val px3cm = ((3f * xdpi) / 2.54f).toInt().coerceAtLeast(1)
                    val px4cm = ((4f * xdpi) / 2.54f).toInt().coerceAtLeast(1)
                    val px5cm = ((5f * xdpi) / 2.54f).toInt().coerceAtLeast(1)
                    headersForDefaults.forEach { header ->
                        val h = header?.lowercase() ?: ""
                        val w = when {
                            h.contains("место установки ключа") -> px4cm
                            h.contains("название позиции") || h.startsWith("бел") -> px5cm
                            else -> px3cm
                        }
                        currentColumnWidths[header] = w
                    }
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
                    // Применяем сохранённый порядок колонок (без изменения ширин по умолчанию)
                    com.example.vkbookandroid.utils.ColumnOrderManager.loadBschuColumnOrder(requireContext()).takeIf { it.isNotEmpty() }?.let {
                        adapter.applyColumnOrder(it)
                    }
                    isDataLoaded = true
                    attachPaging()
                    
                    Log.d("DataFragment", "UI updated successfully. Adapter item count: ${adapter.itemCount}")
                    Log.d("DataFragment", "RecyclerView visibility: ${recyclerView.visibility}")
                }

                // Предпрогрев индекса на полном наборе данных после первичной загрузки (в фоне)
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val combined = mutableListOf<org.example.pult.RowDataDynamic>()
                        combined.addAll(firstPage)
                        val session = cachedSession ?: pagingSession
                        var cursor = firstPage.size
                        val step = pageSize
                        while (session != null) {
                            val next = try { session.readRange(cursor, step) } catch (_: Exception) { emptyList() }
                            if (next.isEmpty()) break
                            combined.addAll(next)
                            cursor += next.size
                        }
                        if (::searchManager.isInitialized) {
                            searchManager.prewarmIndex(combined, headers)
                        }
                    } catch (_: Exception) {}
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

                            // Предпрогрев индекса после фонового обновления кэша (в фоне)
                            try {
                                val combined = mutableListOf<org.example.pult.RowDataDynamic>()
                                combined.addAll(newFirstPage)
                                var cursor = newFirstPage.size
                                val step = pageSize
                                while (true) {
                                    val next = try { newCached.readRange(cursor, step) } catch (_: Exception) { emptyList() }
                                    if (next.isEmpty()) break
                                    combined.addAll(next)
                                    cursor += next.size
                                }
                                if (::searchManager.isInitialized) {
                                    searchManager.prewarmIndex(combined, newHeaders)
                                }
                            } catch (_: Exception) {}
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
            com.example.vkbookandroid.utils.ColumnWidthManager.saveBschuColumnWidths(requireContext(), currentColumnWidths, "DataFragment")
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
            // Проверяем, завершена ли инициализация приложения
            val mainActivity = activity as? com.example.vkbookandroid.MainActivity
            if (mainActivity != null && !mainActivity.isInitializationComplete()) {
                Log.d("DataFragment", "Initialization not complete, waiting...")
                mainActivity.addInitializationListener {
                    Log.d("DataFragment", "Initialization complete, starting data load")
                    loadSignalsData()
                }
                return
            }
            
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
            val baseUrl = com.example.vkbookandroid.ServerSettingsActivity.getCurrentServerUrl(requireContext())
            if (baseUrl != "http://10.0.2.2:8082/") {
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
    
    /**
     * Инициализация новой системы поиска
     */
    private fun initializeSearchSystem() {
        try {
            // Инициализируем SearchManager
            searchManager = SearchManager(requireContext())
            
            // Настраиваем наблюдатели
            setupSearchObservers()
            
            Log.d("DataFragment", "Search system initialized successfully")
        } catch (e: Exception) {
            Log.e("DataFragment", "Error initializing search system", e)
        }
    }
    
    /**
     * Настройка наблюдателей для SearchManager
     */
    private fun setupSearchObservers() {
        // Наблюдаем за результатами поиска
        searchManager.searchResults.observe(viewLifecycleOwner, Observer { results ->
            if (results != null) {
                if (results.requestId >= 0 && results.requestId != activeRequestId) {
                    Log.d("DataFragment", "Observer: outdated results requestId=${results.requestId}, active=${activeRequestId}")
                    return@Observer
                }
                val expected = currentSearchQuery.trim()
                if (results.normalizedQuery.isNotEmpty()) {
                    val same = com.example.vkbookandroid.utils.SearchNormalizer.normalizeSearchQuery(expected) == results.normalizedQuery
                    if (!same) {
                        Log.d("DataFragment", "Observer: normalized query mismatch. expected='$expected', got='${results.normalizedQuery}'")
                        return@Observer
                    }
                }
                handleSearchResults(results)
            }
        })
        
        // Наблюдаем за состоянием поиска
        searchManager.isSearching.observe(viewLifecycleOwner, Observer { isSearching ->
            if (isSearching) {
                // Показываем индикатор загрузки
                Log.d("DataFragment", "Search started")
            } else {
                // Скрываем индикатор загрузки
                Log.d("DataFragment", "Search completed")
            }
        })
        
        // Наблюдаем за историей поиска
        searchManager.searchHistory.observe(viewLifecycleOwner, Observer { history ->
            // Обновляем историю поиска в UI
            Log.d("DataFragment", "Search history updated: ${history.size} items")
        })
    }
    
    /**
     * Обработка результатов поиска от SearchManager
     */
    private fun handleSearchResults(results: SearchManager.SearchResults) {
        Log.d("DataFragment", "Handling search results: ${results.totalCount} items, from cache: ${results.fromCache}")
        if (results.requestId >= 0 && results.requestId != activeRequestId) {
            Log.d("DataFragment", "Ignoring outdated results for requestId=${results.requestId}, active=${activeRequestId}")
            return
        }
        
        try {
            if (results.results.isNotEmpty()) {
                // ИСПРАВЛЕНИЕ: Сортируем результаты по релевантности (лучшие совпадения наверх)
                val sortedResults = results.results.sortedByDescending { it.matchScore }
                Log.d("DataFragment", "Sorted search results by relevance: top score = ${sortedResults.firstOrNull()?.matchScore}")
                // Сбрасываем возможный режим поиска по столбцу, чтобы подсветка не терялась
                try { adapter.deactivateColumnSearch() } catch (_: Throwable) {}
                
                adapter.updateSearchResults(sortedResults, currentSearchQuery)
                recyclerView.post {
                    try { (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0) } catch (_: Throwable) {}
                            recyclerView.tag = null
                    recyclerView.requestLayout()
                }
                // Показать таблицу и скрыть пустой вид
                emptyView?.visibility = View.GONE
                (recyclerView.parent as? View)?.visibility = View.VISIBLE
                
                // ИСПРАВЛЕНИЕ: НЕ прокручиваем таблицу - найденные строки уже перемещены наверх
                // Пользователь остается на том же месте, но видит найденные результаты сверху
                Log.d("DataFragment", "Search results reordered: most relevant (${sortedResults.size} results) moved to top")
            } else {
                // Пустой результат: показываем пустой вид и скрываем таблицу
                // Не возвращаем полную таблицу, чтобы не создавать ложное ощущение «как будто поиск пустой»
                try { adapter.updateFilteredDataPreserveOrder(emptyList(), currentSearchQuery) } catch (_: Throwable) {}
                recyclerView.post {
                    try { (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0) } catch (_: Throwable) {}
                            recyclerView.tag = null
                    recyclerView.requestLayout()
                }
                Log.d("DataFragment", "No search results found for query: '$currentSearchQuery'")
                // Скрыть таблицу и показать пустой вид
                (recyclerView.parent as? View)?.visibility = View.GONE
                emptyView?.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            Log.e("DataFragment", "Error handling search results", e)
            // Fallback к старому методу
            val rowData = results.results.map { it.data }
            adapter.setSearchResults(rowData, currentSearchQuery)
        }
    }
    
    /**
     * Ожидает готовности данных и выполняет поиск
     */
    private fun waitForDataAndSearch(searchText: String) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            var attempts = 0
            val maxAttempts = 20 // Максимум 2 секунды ожидания (20 * 100ms)
            
            while (attempts < maxAttempts && !isDataReadyForSearch()) {
                delay(100)
                attempts++
            }
            
            if (isDataReadyForSearch()) {
                Log.d("DataFragment", "Data ready after $attempts attempts, performing enhanced search")
                if (::searchManager.isInitialized) {
                    val rid = (++nextRequestId)
                    activeRequestId = rid
                    performEnhancedSearch(searchText, rid)
                } else {
                    Log.w("DataFragment", "SearchManager not initialized, search skipped")
                }
            } else {
                Log.w("DataFragment", "Data still not ready after $maxAttempts attempts")
            }
        }
    }
    
    /**
     * Проверяет, готовы ли данные для поиска
     */
    private fun isDataReadyForSearch(): Boolean {
        // Проверяем базовые условия
        if (!isDataLoaded) {
            Log.d("DataFragment", "Data not loaded yet")
            return false
        }
        
        if (!::adapter.isInitialized) {
            Log.d("DataFragment", "Adapter not initialized")
            return false
        }
        
        // ИСПРАВЛЕНИЕ: Проверяем, что у адаптера есть данные для поиска
        val originalData = adapter.getOriginalData()
        if (originalData.isEmpty()) {
            Log.w("DataFragment", "Adapter has no original data for search")
            return false
        }
        
        // ДОПОЛНИТЕЛЬНАЯ ПРОВЕРКА: Убеждаемся, что данные не содержат только null значения
        val validDataCount = originalData.count { row ->
            val values = row.getAllProperties()
            values.any { it != null && it.toString().trim().isNotEmpty() }
        }
        
        if (validDataCount == 0) {
            Log.w("DataFragment", "All data rows are empty or contain only null values")
            return false
        }
        
        // ДОПОЛНИТЕЛЬНАЯ ПРОВЕРКА: Убеждаемся, что headers не пустые
        if (adapter.headers.isEmpty()) {
            Log.w("DataFragment", "Headers are empty")
            return false
        }
        
        // ИСПРАВЛЕНИЕ: Дополнительная проверка - убеждаемся, что фрагмент еще активен
        if (!isAdded || !isVisible) {
            Log.w("DataFragment", "Fragment not active (isAdded: $isAdded, isVisible: $isVisible)")
            return false
        }
        
        // ИСПРАВЛЕНИЕ: Проверяем, что RecyclerView готов к работе
        if (recyclerView.width <= 0 || recyclerView.height <= 0) {
            Log.d("DataFragment", "RecyclerView not laid out yet (${recyclerView.width}x${recyclerView.height})")
            return false
        }
        
        Log.d("DataFragment", "Data ready for search: ${originalData.size} items, ${validDataCount} valid rows, ${adapter.headers.size} headers")
        return true
    }
    
    /**
     * Улучшенная фильтрация данных с использованием SearchManager
     */
    private suspend fun performEnhancedSearch(searchText: String, requestId: Int) {
        val normalizedSearch = searchText.trim()
        
        if (!isDataReadyForSearch()) {
            Log.w("DataFragment", "Cannot perform enhanced search: data not ready yet")
            return
        }
        
        try {
            val originalData = adapter.getOriginalData()
            val headers = adapter.headers
            // НОВОЕ: Расширяем набор данных для индекса/поиска за пределы первой страницы
            val dataForSearch = withContext(Dispatchers.IO) {
                try {
                    val combined = mutableListOf<org.example.pult.RowDataDynamic>()
                    combined.addAll(originalData)

                    val session = cachedSession ?: pagingSession
                    var cursor = originalData.size
                    val step = pageSize

                    while (session != null) {
                        val toRead = step
                        val next = try {
                            session.readRange(cursor, toRead)
                        } catch (e: Exception) {
                            Log.w("DataFragment", "ReadRange failed at $cursor+$toRead: ${e.message}")
                            emptyList()
                        }
                        if (next.isEmpty()) break
                        combined.addAll(next)
                        cursor += next.size
                    }

                    Log.d(
                        "DataFragment",
                        "Data for search prepared: original=${originalData.size}, total=${combined.size}"
                    )
                    combined.toList()
                } catch (e: Exception) {
                    Log.w("DataFragment", "Failed to prepare extended data for search, fallback to original", e)
                    originalData
                }
            }
            val selectedColumn = if (adapter.hasSelectedColumn()) {
                adapter.getSelectedColumnName()
            } else null
            
            // Используем SearchManager для поиска
            searchManager.performSearch(
                query = normalizedSearch,
                data = dataForSearch,
                headers = headers,
                selectedColumn = selectedColumn,
                forceRefresh = false,
                requestId = requestId
            )
            
        } catch (e: Exception) {
            Log.e("DataFragment", "Error during enhanced search", e)
            // ИСПРАВЛЕНИЕ: Вместо fallback к старому методу, очищаем результаты поиска
            if (isDataReadyForSearch()) {
                adapter.clearSearchResultsOptimized()
            }
        }
    }
    
    /**
     * Очистка ресурсов
     */
    override fun onDestroy() {
        super.onDestroy()
        
        // Очищаем ресурсы SearchManager
        if (::searchManager.isInitialized) {
            searchManager.cleanup()
        }
        
        // Очищаем ресурсы адаптера
        if (::adapter.isInitialized) {
            adapter.cleanup()
        }
        
        // Отменяем все активные задачи поиска
        searchJob?.cancel()
    }
}
