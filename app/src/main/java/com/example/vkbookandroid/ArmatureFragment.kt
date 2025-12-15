package com.example.vkbookandroid

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.vkbookandroid.search.EnhancedSearchView
import com.example.vkbookandroid.search.SearchManager
import com.example.vkbookandroid.search.VoiceSearchHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Экран "Арматура".
 *
 * Отвечает за:
 * - загрузку и кэширование данных об арматуре из Excel/сервера через `AppExcelDataManager` и `ArmatureRepository`;
 * - отображение таблицы арматуры с помощью общего `SignalsAdapter` и горизонтального скролла;
 * - сохранение пользовательских настроек колонок (ширина, порядок, скрытые столбцы);
 * - полнотекстовый поиск по арматуре (включая голосовой ввод) и переход к PDF‑схемам по нажатию на ячейку.
 *
 * Реализует:
 * - `RefreshableFragment` — позволяет внешне инициировать перезагрузку данных;
 * - `ThemeManager.ThemeAwareFragment` — применяет текущую тему оформления к своим View.
 */
class ArmatureFragment : Fragment(), RefreshableFragment, com.example.vkbookandroid.theme.ThemeManager.ThemeAwareFragment {
    
    private lateinit var recyclerView: RecyclerView
    private var emptyView: android.widget.TextView? = null
    private lateinit var adapter: SignalsAdapter
    private lateinit var excelDataManager: AppExcelDataManager
    private lateinit var excelRepository: ExcelRepository
    private var currentColumnWidths: MutableMap<String, Int> = mutableMapOf()
    private var currentColumnOrder: MutableList<String> = mutableListOf()
    private lateinit var searchView: SearchView
    private lateinit var searchProgress: ProgressBar
    private lateinit var toggleResizeModeButton: Button
    private lateinit var scrollToTopButton: android.widget.ImageButton
    private lateinit var scrollToBottomButton: android.widget.ImageButton
    private var isResizingMode: Boolean = false
    private var isDataLoaded: Boolean = false
    private var isLoadingPage: Boolean = false
    
    // ИСПРАВЛЕНИЕ: Дебаунсинг для поиска
    private var searchJob: Job? = null
    private val queryFlow = MutableStateFlow("")
    private var nextStartRow: Int = 0
    private val pageSize: Int = 500 // Увеличиваем размер страницы для лучшего поиска
    private var pagingSession: ExcelPagingSession? = null
    private var cachedSession: PagingSession? = null
    private var lastMeasuredListWidth: Int = 0
    private var lastHeaders: List<String> = emptyList()
    private var armatureRepository: com.example.vkbookandroid.repository.ArmatureRepository? = null
    
    // Новая система поиска
    private lateinit var searchManager: SearchManager
    private lateinit var enhancedSearchView: EnhancedSearchView
    private lateinit var voiceSearchHelper: VoiceSearchHelper
    private val voiceSearchLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (::voiceSearchHelper.isInitialized) {
                val recognizedText = voiceSearchHelper.handleVoiceSearchResult(result)
                if (!recognizedText.isNullOrBlank() && ::searchView.isInitialized) {
                    searchView.setQuery(recognizedText, true)
                }
            }
        }
    private var currentSearchQuery: String = ""
    private var scrollToTopOnNextResults: Boolean = false
    private var nextRequestId: Int = 0
    private var activeRequestId: Int = -1
    
    // Флаг для предотвращения множественных загрузок фона
    private var isLoadingBackground: Boolean = false
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_armature, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Применяем тему к фрагменту
        applyTheme()
        
        recyclerView = view.findViewById(R.id.recyclerView)
        emptyView = view.findViewById(R.id.empty_view)
        val hscroll: android.widget.HorizontalScrollView? = view.findViewById(R.id.hscroll)
        recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        // Отключаем анимации, чтобы избежать дергания при обновлениях
        recyclerView.itemAnimator = null
        // setHasFixedSize(true) убран, так как RecyclerView использует wrap_content в layout
        recyclerView.setItemViewCacheSize(20)
        // Устанавливаем начальный padding = 0 (без режима редактирования)
        recyclerView.setPadding(0, 0, 0, 0)
        adapter = SignalsAdapter(emptyList(), isResizingMode, { columnIndex, newWidth, action ->
            val headerName = adapter.headers.getOrNull(columnIndex)
            if (headerName != null) {
                currentColumnWidths[headerName] = newWidth
                adapter.updateColumnWidths(columnIndex, newWidth, false)
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    com.example.vkbookandroid.utils.ColumnWidthManager.saveArmatureColumnWidths(
                        requireContext(), 
                        currentColumnWidths, 
                        "ArmatureFragment"
                    )
                    adapter.updateColumnWidths(columnIndex, newWidth, true)
                }
            }
        }, onRowClick = { row ->
            // Извлекаем из столбца PDF_Схема_и_ID_арматуры имя PDF и идентификатор (designation)
            val headers = adapter.headers
            val values = row.getAllProperties()
            val colIndex = headers.indexOfFirst { it.equals("PDF_Схема_и_ID_арматуры", ignoreCase = true) }
            val cell = if (colIndex >= 0 && colIndex < values.size) values[colIndex] else null
            if (cell.isNullOrBlank()) return@SignalsAdapter

            // Поддерживаем два формата: "Имя.pdf#ID" и просто "Имя.pdf" (тогда ID берём из ячейки Арматура)
            val armatureIdx = headers.indexOfFirst { it.equals("Арматура", ignoreCase = true) }
            val armatureId = if (armatureIdx >= 0 && armatureIdx < values.size) values[armatureIdx]?.trim() else null
            val pdfDesignationPattern = Regex("(?i)(.+?\\.pdf)(?:[#;|\\s]+(.+))?")
            val match = pdfDesignationPattern.find(cell)
            val pdfName = match?.groupValues?.getOrNull(1)
            val parsedId = match?.groupValues?.getOrNull(2)?.trim()?.trim('-', ' ', ';', '|', '#')
            val designation: String? = if (!parsedId.isNullOrBlank()) parsedId else armatureId

            if (!pdfName.isNullOrBlank() && !designation.isNullOrBlank()) {
                val pdfPath = "Schemes/$pdfName"
                Log.d("ArmatureFragment", "=== OPENING SCHEME FROM ROW CLICK ===")
                Log.d("ArmatureFragment", "Original cell: '$cell'")
                Log.d("ArmatureFragment", "Extracted PDF name: '$pdfName'")
                Log.d("ArmatureFragment", "Extracted designation: '$designation'")
                Log.d("ArmatureFragment", "Final PDF path: '$pdfPath'")
                
                val intent = android.content.Intent(requireContext(), PdfViewerActivity::class.java).apply {
                    putExtra("pdf_path", pdfPath)
                    putExtra("designation", designation)
                }
                startActivity(intent)
            } else {
                Log.w("ArmatureFragment", "Cannot open scheme: pdfName='$pdfName', designation='$designation'")
            }
        }, onArmatureCellClick = { row ->
            // Клик по названию арматуры: ищем PDF/ID из столбца PDF_Схема_и_ID_арматуры в той же строке
            val headers = adapter.headers
            val values = row.getAllProperties()
            val idxPdf = headers.indexOfFirst { it.equals("PDF_Схема_и_ID_арматуры", ignoreCase = true) || it.equals("PDF_Схема", ignoreCase = true) }
            val cell = if (idxPdf >= 0 && idxPdf < values.size) values[idxPdf] else null
            if (cell.isNullOrBlank()) return@SignalsAdapter
            // Берём ID из той же строки в колонке "Арматура" при отсутствии ID после имени PDF
            val idxArm = headers.indexOfFirst { it.equals("Арматура", ignoreCase = true) }
            val armId = if (idxArm >= 0 && idxArm < values.size) values[idxArm]?.trim() else null
            val pattern = Regex("(?i)(.+?\\.pdf)(?:[#;|\\s]+(.+))?")
            val match = pattern.find(cell)
            val pdfName = match?.groupValues?.getOrNull(1)
            val parsedId = match?.groupValues?.getOrNull(2)?.trim()?.trim('-', ' ', ';', '|', '#')
            val designation: String? = if (!parsedId.isNullOrBlank()) parsedId else armId
            if (pdfName.isNullOrBlank() || designation.isNullOrBlank()) {
                Log.w("ArmatureFragment", "Cannot open scheme from armature cell: pdfName='$pdfName', designation='$designation'")
                return@SignalsAdapter
            }
            val pdfPath = "Schemes/$pdfName"
            Log.d("ArmatureFragment", "=== OPENING SCHEME FROM ARMATURE CELL CLICK ===")
            Log.d("ArmatureFragment", "Original cell: '$cell'")
            Log.d("ArmatureFragment", "Extracted PDF name: '$pdfName'")
            Log.d("ArmatureFragment", "Extracted designation: '$designation'")
            Log.d("ArmatureFragment", "Final PDF path: '$pdfPath'")
            
            val intent = android.content.Intent(requireContext(), PdfViewerActivity::class.java).apply {
                putExtra("pdf_path", pdfPath)
                putExtra("designation", designation)
            }
            startActivity(intent)
        }, onColumnReorder = { newOrder ->
            try {
                currentColumnOrder = newOrder.toMutableList()
                com.example.vkbookandroid.utils.ColumnOrderManager.saveArmatureColumnOrder(requireContext(), newOrder)
            } catch (_: Throwable) {}
        })
        // Единый жесткий колбэк после применения результатов поиска — скролл к самому верху/влево
        adapter.setOnAfterSearchResultsApplied {
            try { recyclerView.stopScroll() } catch (_: Throwable) {}
            try { hscroll?.post { hscroll.scrollTo(0, 0) } } catch (_: Throwable) {}
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
        // Drag & drop reorder in editing mode
        val touchHelper = androidx.recyclerview.widget.ItemTouchHelper(object : androidx.recyclerview.widget.ItemTouchHelper.Callback() {
            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                // Разрешаем перетаскивание только заголовка при режиме редактирования
                val dragFlags = if (isResizingMode && recyclerView.getChildAdapterPosition(viewHolder.itemView) == 0) androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT else 0
                return makeMovementFlags(dragFlags, 0)
            }
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                // Перетаскивание внутри заголовка: вычисляем индекс колонки по X
                if (!isResizingMode) return false
                val header = rv.findViewHolderForAdapterPosition(0) as? com.example.vkbookandroid.SignalsAdapter.HeaderViewHolder ?: return false
                // Упростим: не будем на лету менять — порядок задаётся через отдельный UI. Заглушка для совместимости
                return false
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            override fun isLongPressDragEnabled(): Boolean = false
        })
        touchHelper.attachToRecyclerView(recyclerView)
        // Гарантируем первичную разметку и видимость после ротации
        view.post {
            recyclerView.adapter?.notifyDataSetChanged()
            recyclerView.requestLayout()
            val original = adapter.getOriginalData()
            if (adapter.itemCount <= 1 && original.isNotEmpty() && lastHeaders.isNotEmpty()) {
                adapter.updateData(original, lastHeaders, currentColumnWidths, isResizingMode, updateOriginal = false)
            }
            recyclerView.visibility = View.VISIBLE
            emptyView?.visibility = View.GONE
        }
        // Отключаем звуки кликов у всего дерева фрагмента
        view.isSoundEffectsEnabled = false
        
        excelDataManager = AppExcelDataManager(requireContext().applicationContext)
        val baseUrl = ServerSettingsActivity.getCurrentServerUrl(requireContext())
        val fileProvider: IFileProvider = if (baseUrl != "http://10.0.2.2:8082/") {
            RemoteFileProvider(requireContext(), baseUrl)
        } else {
            FileProvider(requireContext())
        }
        excelRepository = ExcelRepository(requireContext(), fileProvider)
        armatureRepository = com.example.vkbookandroid.repository.ArmatureRepository(requireContext(), com.example.vkbookandroid.network.NetworkModule.getArmatureApiService())
        searchView = view.findViewById(R.id.search_view)
        searchProgress = view.findViewById(R.id.search_progress)
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
        
        // Применяем тему к кнопкам
        applyThemeToButtons()
        
        // Загружаем данные сразу при создании фрагмента
        ensureDataLoaded()
    }

    override fun onResume() {
        super.onResume()
        
        // Регистрируем фрагмент в ThemeManager
        com.example.vkbookandroid.theme.ThemeManager.registerFragment(this)
        
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
            emptyView?.visibility = View.GONE
        }
    }
    
    override fun isFragmentReady(): Boolean {
        // ИСПРАВЛЕНИЕ: Для применения темы достаточно наличия view
        // Адаптер и кнопки могут быть не инициализированы
        return view != null && isAdded
    }
    
    private fun loadArmatureData() {
        Log.d("ArmatureFragment", "=== STARTING ARMATURE DATA LOADING ===")
        Log.d("ArmatureFragment", "Fragment state: isAdded=${isAdded}, isVisible=${isVisible}, isResumed=${isResumed}")
        Log.d("ArmatureFragment", "Context available: ${::excelRepository.isInitialized}")
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d("ArmatureFragment", "Starting data loading in IO thread")
                val ctx = requireContext()
                // 1) быстрый старт из кэша, если он есть
                Log.d("ArmatureFragment", "Attempting to open cached session...")
                cachedSession = excelRepository.openCachedSessionArmatures(pageSize)
                Log.d("ArmatureFragment", "Cached session result: ${cachedSession != null}")
                
                val sessionForInitial = cachedSession ?: run {
                    Log.d("ArmatureFragment", "No cached session, opening paging session...")
                    pagingSession = excelRepository.openPagingSessionArmatures()
                    Log.d("ArmatureFragment", "Paging session opened: ${pagingSession != null}")
                    pagingSession as PagingSession
                }
                val initialColumnWidths = sessionForInitial.getColumnWidths()

                // БАЗА: ширины по умолчанию = 3.5 см до тех пор, пока пользователь их не изменит
                val savedColumnWidths = com.example.vkbookandroid.utils.ColumnWidthManager.loadArmatureColumnWidths(ctx)
                currentColumnWidths = mutableMapOf()
                val headersForDefaults = try { (cachedSession ?: pagingSession ?: sessionForInitial).getHeaders() } catch (_: Throwable) { emptyList() }
                if (savedColumnWidths.isNotEmpty()) {
                    currentColumnWidths.putAll(savedColumnWidths)
                } else {
                    val xdpi = resources.displayMetrics.xdpi
                    val px3cm = ((3f * xdpi) / 2.54f).toInt().coerceAtLeast(1)
                    val px4cm = ((4f * xdpi) / 2.54f).toInt().coerceAtLeast(1)
                    val px5cm = ((5f * xdpi) / 2.54f).toInt().coerceAtLeast(1)
                    headersForDefaults.forEach { header ->
                        val h = header?.lowercase() ?: ""
                        val w = when {
                            h.contains("место установки ключа") -> px4cm
                            h.contains("название позиции") -> px5cm
                            else -> px3cm
                        }
                        currentColumnWidths[header] = w
                    }
                }

                // Load headers
                val headers = sessionForInitial.getHeaders()
                Log.d("ArmatureFragment", "=== ARMATURE DATA DIAGNOSTICS ===")
                Log.d("ArmatureFragment", "Loaded headers: $headers")
                Log.d("ArmatureFragment", "Headers count: ${headers.size}")

                // ИСПРАВЛЕНИЕ: Загружаем больше данных сразу для лучшего поиска
                val initialDataSize = if (sessionForInitial is ExcelPagingSession) {
                    val totalRows = sessionForInitial.getTotalDataRows()
                    kotlin.math.min(totalRows, 2000) // Загружаем до 2000 строк для поиска
                } else {
                    pageSize
                }
                
                val firstPage = sessionForInitial.readRange(0, initialDataSize)
                nextStartRow = firstPage.size
                Log.d("ArmatureFragment", "Loaded initial data count: ${firstPage.size}")
                Log.d("ArmatureFragment", "=== END ARMATURE DATA DIAGNOSTICS ===")

                withContext(Dispatchers.Main) {
                    Log.d("ArmatureFragment", "Updating UI with data: headers=${headers.size}, firstPage=${firstPage.size}")
                    Log.d("ArmatureFragment", "Fragment still valid: isAdded=${isAdded}, isVisible=${isVisible}")
                    
                    if (!isAdded) {
                        Log.w("ArmatureFragment", "Fragment no longer added, skipping UI update")
                        return@withContext
                    }
                    
                    lastHeaders = headers
                    adapter.updateData(firstPage, headers, currentColumnWidths, isResizingMode, updateOriginal = true)
                    // Применяем сохранённый порядок колонок (без изменения ширин по умолчанию)
                    com.example.vkbookandroid.utils.ColumnOrderManager.loadArmatureColumnOrder(requireContext()).takeIf { it.isNotEmpty() }?.let {
                        adapter.applyColumnOrder(it)
                    }
                    isDataLoaded = true
                    attachPaging()
                    
                    // ИСПРАВЛЕНИЕ: Принудительная проверка готовности данных для поиска
                    val originalDataCount = adapter.getOriginalData().size
                    Log.d("ArmatureFragment", "UI updated successfully. Adapter item count: ${adapter.itemCount}, Original data: $originalDataCount")
                    Log.d("ArmatureFragment", "RecyclerView visibility: ${recyclerView.visibility}")
                    Log.d("ArmatureFragment", "Data ready for search: ${isDataReadyForSearch()}")
                    
                    // JSON координаты загружаются только при синхронизации
                    // loadArmatureCoordinates() - удалено для единообразного поведения
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

                // 2) фоновая пересборка кэша и бесшовное обновление
                excelRepository.refreshCacheArmatures(pageSize) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val newCached = excelRepository.openCachedSessionArmatures(pageSize)
                        if (newCached != null) {
                            val newHeaders = newCached.getHeaders()
                            // ИСПРАВЛЕНИЕ: Загружаем больше данных при фоновом обновлении
                            val newDataSize = if (newCached is ExcelPagingSession) {
                                val totalRows = newCached.getTotalDataRows()
                                kotlin.math.min(totalRows, 2000) // Загружаем до 2000 строк
                            } else {
                                pageSize
                            }
                            val newFirstPage = newCached.readRange(0, newDataSize)
                            nextStartRow = newFirstPage.size
                            withContext(Dispatchers.Main) {
                                lastHeaders = newHeaders
                                adapter.updateData(newFirstPage, newHeaders, currentColumnWidths, isResizingMode, updateOriginal = true)
                                
                                // ИСПРАВЛЕНИЕ: Проверка готовности данных после фонового обновления
                                val originalDataCount = adapter.getOriginalData().size
                                Log.d("ArmatureFragment", "Background update completed. Original data: $originalDataCount")
                                Log.d("ArmatureFragment", "Data ready for search after update: ${isDataReadyForSearch()}")
                            }
                            cachedSession = newCached
                            pagingSession?.close()
                            pagingSession = null
                            // Предпрогрев индекса после фонового обновления кэша (в фоне)
                            lifecycleScope.launch(Dispatchers.IO) {
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
                }
            } catch (e: Exception) {
                Log.e("ArmatureFragment", "Error loading armature data", e)
                Log.e("ArmatureFragment", "Exception details: ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
                
                // Показываем ошибку пользователю
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        Toast.makeText(requireContext(), "Ошибка загрузки данных арматуры: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }


    private fun attachPaging() {
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

    private fun applyThemeToButtons() {
        android.util.Log.d("ArmatureFragment", "=== applyThemeToButtons() вызван ===")
        
        // Проверяем, инициализирована ли кнопка
        if (!::toggleResizeModeButton.isInitialized) {
            android.util.Log.w("ArmatureFragment", "toggleResizeModeButton НЕ инициализирована!")
            return
        }
        
        android.util.Log.d("ArmatureFragment", "Кнопка инициализирована, применяем тему")
        
        if (!com.example.vkbookandroid.theme.AppTheme.shouldApplyTheme()) {
            // Классическая тема - темно-синий цвет, овальная форма
            toggleResizeModeButton.backgroundTintList = null
            
            val drawable = android.graphics.drawable.GradientDrawable()
            drawable.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            drawable.cornerRadius = 100f * toggleResizeModeButton.context.resources.displayMetrics.density // Овальная
            drawable.setColor(android.graphics.Color.parseColor("#0d47a1")) // Темно-синий
            
            toggleResizeModeButton.background = drawable
            toggleResizeModeButton.setTextColor(android.graphics.Color.WHITE)
            
            // Арматура: увеличиваем на 2dp (0.5мм)
            val px = toggleResizeModeButton.context.resources.displayMetrics.density
            val paddingH = ((com.example.vkbookandroid.theme.AppTheme.getButtonPaddingHorizontal() + 2) * px).toInt()
            val paddingV = ((com.example.vkbookandroid.theme.AppTheme.getButtonPaddingVertical() + 2) * px).toInt()
            toggleResizeModeButton.setPadding(paddingH, paddingV, paddingH, paddingV)
            toggleResizeModeButton.minHeight = 0
            toggleResizeModeButton.minWidth = 0
            return
        }
        
        // Применяем тему к кнопке переключения режима
        // Делаем кнопку темнее ФОНОВОГО цвета на 30% для лучшей видимости
        toggleResizeModeButton.backgroundTintList = null
        
        // ИСПРАВЛЕНИЕ: Используем ФОНОВЫЙ цвет темы и затемняем его на 30%
        val bgColor = com.example.vkbookandroid.theme.AppTheme.getBackgroundColor()
        android.util.Log.d("ArmatureFragment", "Фоновый цвет: #${Integer.toHexString(bgColor)}")
        
        val darkerColor = darkenColor(bgColor, 0.3f) // Затемняем на 30%
        android.util.Log.d("ArmatureFragment", "Затемненный цвет кнопки: #${Integer.toHexString(darkerColor)}")
        
        val drawable = android.graphics.drawable.GradientDrawable()
        drawable.shape = android.graphics.drawable.GradientDrawable.RECTANGLE
        drawable.cornerRadius = com.example.vkbookandroid.theme.AppTheme.getButtonCornerRadius()
        drawable.setColor(darkerColor)
        
        // КРИТИЧНО: Сбрасываем backgroundTintList ПОСЛЕ создания drawable
        toggleResizeModeButton.backgroundTintList = null
        toggleResizeModeButton.background = drawable
        
        val textColor = com.example.vkbookandroid.theme.AppTheme.getTextPrimaryColor()
        android.util.Log.d("ArmatureFragment", "Цвет текста кнопки: #${Integer.toHexString(textColor)}")
        toggleResizeModeButton.setTextColor(textColor)
        
        // Арматура: увеличиваем на 2dp (0.5мм)
        val px = toggleResizeModeButton.context.resources.displayMetrics.density
        val paddingH = ((com.example.vkbookandroid.theme.AppTheme.getButtonPaddingHorizontal() + 2) * px).toInt()
        val paddingV = ((com.example.vkbookandroid.theme.AppTheme.getButtonPaddingVertical() + 2) * px).toInt()
        toggleResizeModeButton.setPadding(paddingH, paddingV, paddingH, paddingV)
        toggleResizeModeButton.minHeight = 0
        toggleResizeModeButton.minWidth = 0
        
        android.util.Log.d("ArmatureFragment", "Кнопка обновлена!")
    }
    
    private fun darkenColor(color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        hsv[2] *= (1f - factor) // Уменьшаем яркость
        return android.graphics.Color.HSVToColor(hsv)
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

            val scaled = mutableMapOf<String, Int>()
            for (header in adapter.headers) {
                val cur = currentColumnWidths[header] ?: continue
                val nw = (cur * factor).toInt().coerceAtLeast(1)
                scaled[header] = nw
            }
            if (scaled.isEmpty()) return@addOnLayoutChangeListener
            currentColumnWidths.putAll(scaled)
            lastMeasuredListWidth = newW

            adapter.headers.forEachIndexed { idx, _ ->
                val newWForCol = currentColumnWidths[adapter.headers[idx]] ?: return@forEachIndexed
                val notify = idx == adapter.headers.lastIndex
                adapter.updateColumnWidths(idx, newWForCol, notify)
            }

            com.example.vkbookandroid.utils.ColumnWidthManager.saveArmatureColumnWidths(requireContext(), currentColumnWidths, "ArmatureFragment")
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
                Log.e("ArmatureFragment", "Paging load error", e)
            } finally {
                isLoadingPage = false
            }
        }
    }

    private fun setupSearch() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                scrollToTopOnNextResults = true
                queryFlow.value = query.orEmpty()
                (activity as? MainActivity)?.onFragmentSearchQueryChanged(query.orEmpty())
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                scrollToTopOnNextResults = true
                queryFlow.value = newText.orEmpty()
                (activity as? MainActivity)?.onFragmentSearchQueryChanged(newText.orEmpty())
                return true
            }
        })

        // Настройка кнопки очистки
        searchView.setOnCloseListener {
            // Очищаем поиск при нажатии на крестик
            searchView.setQuery("", false)
            scrollToTopOnNextResults = true
            queryFlow.value = ""
            showFullTable()
            (activity as? MainActivity)?.onFragmentSearchQueryChanged("")
            true // Возвращаем true, чтобы SearchView не закрывался
        }

        // ИСПРАВЛЕНИЕ: Используем безопасный способ настройки SearchView
        // Вместо прямого доступа к SearchAutoComplete используем общие настройки SearchView
        try {
            // Настройка цвета текста и подсказки через SearchView
            searchView.setQueryHint("Поиск арматуры...")
            
            // Дополнительные настройки через reflection (если необходимо)
            val searchSrcText = searchView.findViewById<View>(androidx.appcompat.R.id.search_src_text)
            searchSrcText?.let { textView ->
                if (textView is android.widget.EditText) {
                    textView.setTextColor(android.graphics.Color.BLACK)
                    textView.setHintTextColor(android.graphics.Color.parseColor("#666666"))
                    textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
                }
            }
        } catch (e: Exception) {
            // Если настройка не удалась, продолжаем без неё
            Log.w("ArmatureFragment", "Could not customize SearchView appearance", e)
        }
    }

    private fun setupToggleResizeModeButton() {
        toggleResizeModeButton.setOnClickListener {
            isResizingMode = !isResizingMode
            toggleResizeModeButton.text = if (isResizingMode) "Сохранить" else "Редактировать"
            adapter.setResizingMode(isResizingMode)
            
            // Добавляем правый padding в режиме редактирования для удобства растягивания последнего столбца
            val paddingRight = if (isResizingMode) {
                (48 * resources.displayMetrics.density).toInt() // 48dp зазор для handle последнего столбца
            } else {
                0
            }
            recyclerView.setPadding(
                recyclerView.paddingLeft,
                recyclerView.paddingTop,
                paddingRight,
                recyclerView.paddingBottom
            )
            
            recyclerView.isNestedScrollingEnabled = true
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

    private fun showFullTable() {
        try { (recyclerView.parent as? View)?.visibility = View.VISIBLE } catch (_: Throwable) {}
        emptyView?.visibility = View.GONE
    }

    private fun scrollToTop() {
        try {
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
            if (layoutManager != null) {
                layoutManager.scrollToPositionWithOffset(0, 0)
                recyclerView.smoothScrollToPosition(0)
            }
        } catch (e: Exception) {
            Log.e("ArmatureFragment", "Error scrolling to top", e)
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
            Log.e("ArmatureFragment", "Error scrolling to bottom", e)
        }
    }
    
    fun filterData(searchText: String) {
        // Переведено на Flow-пайплайн: просто отправляем значение
        scrollToTopOnNextResults = true
        queryFlow.value = searchText
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private fun setupSearchFlow() {
        lifecycleScope.launch {
            queryFlow
                .map { it.trim() }
                .debounce(300)
                .distinctUntilChanged()
                .flatMapLatest { normalized ->
                    flow {
                        currentSearchQuery = normalized
                        scrollToTopOnNextResults = true
                        val requestIdForThisQuery = (++nextRequestId)
                        activeRequestId = requestIdForThisQuery

                        if (normalized.isEmpty()) {
                            if (isDataReadyForSearch()) {
                                adapter.clearSearchResultsOptimized()
                                recyclerView.post {
                                    try { (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0) } catch (_: Throwable) {}
                                    recyclerView.requestLayout()
                                }
                                showFullTable()
                            }
                            emit(Unit)
                            return@flow
                        }

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
                                Log.e("ArmatureFragment", "Search error in flow", e)
                                if (isDataReadyForSearch()) adapter.clearSearchResults()
                            }
                        }
                        emit(Unit)
                    }
                }
                .collect { /* no-op */ }
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
                Log.d("ArmatureFragment", "Data ready after $attempts attempts, performing enhanced search")
                if (::searchManager.isInitialized) {
                    // При ожидании данных инициируем новый requestId
                    val rid = (++nextRequestId)
                    activeRequestId = rid
                    performEnhancedSearch(searchText, rid)
                } else {
                    Log.w("ArmatureFragment", "SearchManager not initialized, search skipped")
                }
            } else {
                Log.w("ArmatureFragment", "Data still not ready after $maxAttempts attempts")
            }
        }
    }
    
    /**
     * Проверяет, готовы ли данные для поиска
     */
    private fun isDataReadyForSearch(): Boolean {
        // Проверяем базовые условия
        if (!isDataLoaded) {
            Log.d("ArmatureFragment", "Data not loaded yet")
            return false
        }
        
        if (!::adapter.isInitialized) {
            Log.d("ArmatureFragment", "Adapter not initialized")
            return false
        }
        
        // ИСПРАВЛЕНИЕ: Проверяем, что у адаптера есть данные для поиска
        val originalData = adapter.getOriginalData()
        if (originalData.isEmpty()) {
            Log.w("ArmatureFragment", "Adapter has no original data for search")
            return false
        }
        
        // ДОПОЛНИТЕЛЬНАЯ ПРОВЕРКА: Убеждаемся, что данные не содержат только null значения
        val validDataCount = originalData.count { row ->
            val values = row.getAllProperties()
            values.any { it != null && it.toString().trim().isNotEmpty() }
        }
        
        if (validDataCount == 0) {
            Log.w("ArmatureFragment", "All data rows are empty or contain only null values")
            return false
        }
        
        // ДОПОЛНИТЕЛЬНАЯ ПРОВЕРКА: Убеждаемся, что headers не пустые
        if (adapter.headers.isEmpty()) {
            Log.w("ArmatureFragment", "Headers are empty")
            return false
        }
        
        // ИСПРАВЛЕНИЕ: Дополнительная проверка - убеждаемся, что фрагмент еще активен
        if (!isAdded || !isVisible) {
            Log.w("ArmatureFragment", "Fragment not active (isAdded: $isAdded, isVisible: $isVisible)")
            return false
        }
        
        // ИСПРАВЛЕНИЕ: Проверяем, что RecyclerView готов к работе
        if (recyclerView.width <= 0 || recyclerView.height <= 0) {
            Log.d("ArmatureFragment", "RecyclerView not laid out yet (${recyclerView.width}x${recyclerView.height})")
            return false
        }
        
        Log.d("ArmatureFragment", "Data ready for search: ${originalData.size} items, ${validDataCount} valid rows, ${adapter.headers.size} headers")
        return true
    }

    fun setSearchQueryExternal(query: String) {
        if (!isAdded) return
        if (!::searchView.isInitialized) return
        if (searchView.query?.toString() == query) return
        searchView.setQuery(query, false)
        filterData(query)
    }

    fun ensureDataLoaded() {
        Log.d("ArmatureFragment", "ensureDataLoaded called: isDataLoaded=$isDataLoaded, isAdded=$isAdded, isVisible=$isVisible")
        
        // ЗАЩИТА: Проверяем что view готов
        if (view == null || !::recyclerView.isInitialized) {
            Log.w("ArmatureFragment", "ensureDataLoaded() вызван но view не готов, откладываем загрузку")
            // Отложим загрузку до момента когда view будет готов
            view?.post {
                if (::recyclerView.isInitialized && !isDataLoaded) {
                    loadArmatureData()
                }
            }
            return
        }
        
        if (!isDataLoaded) {
            // Проверяем, завершена ли инициализация приложения
            val mainActivity = activity as? com.example.vkbookandroid.MainActivity
            if (mainActivity != null && !mainActivity.isInitializationComplete()) {
                Log.d("ArmatureFragment", "Initialization not complete, waiting...")
                mainActivity.addInitializationListener {
                    Log.d("ArmatureFragment", "Initialization complete, starting data load")
                    loadArmatureData()
                }
                return
            }
            
            Log.d("ArmatureFragment", "Data not loaded, starting loadArmatureData()")
            loadArmatureData()
        } else {
            Log.d("ArmatureFragment", "Data already loaded, skipping")
        }
    }
    
    // Реализация интерфейса RefreshableFragment
    /**
     * Применить тему к фрагменту
     */
    override fun applyTheme() {
        view?.let { v ->
            // Находим Toolbar
            val toolbar = v.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
            
            if (!com.example.vkbookandroid.theme.AppTheme.shouldApplyTheme()) {
                // КЛАССИЧЕСКАЯ ТЕМА - исходные цвета!
                toolbar?.setBackgroundColor(android.graphics.Color.parseColor("#1976d2"))
                toolbar?.setTitleTextColor(android.graphics.Color.WHITE)
                // Фон - БЕЛЫЙ (как был изначально)!
                v.setBackgroundColor(android.graphics.Color.WHITE)
                if (::recyclerView.isInitialized) {
                    recyclerView.setBackgroundColor(android.graphics.Color.WHITE)
                }
                // Применяем тему к кнопкам (восстанавливаем оригинальные цвета)
                applyThemeToButtons()
                return
            }
            
            // ДРУГИЕ ТЕМЫ - применяем стили
            // Применяем Toolbar - используем ФОНОВЫЙ цвет темы (светло-зеленый для эргономичной)
            toolbar?.let {
                it.setBackgroundColor(com.example.vkbookandroid.theme.AppTheme.getBackgroundColor())
                it.setTitleTextColor(com.example.vkbookandroid.theme.AppTheme.getTextPrimaryColor())
            }
            
            // Сначала применяем цвет фона (быстро)
            v.setBackgroundColor(com.example.vkbookandroid.theme.AppTheme.getBackgroundColor())
            if (::recyclerView.isInitialized) {
                recyclerView.setBackgroundColor(com.example.vkbookandroid.theme.AppTheme.getBackgroundColor())
            }
            
            // Затем асинхронно загружаем фоновое изображение (если есть)
            // ЗАЩИТА: предотвращаем множественные одновременные загрузки
            if (!isLoadingBackground) {
                isLoadingBackground = true
                android.util.Log.d("ArmatureFragment", "Начинаем загрузку фонового изображения...")
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val bgDrawable = com.example.vkbookandroid.theme.AppTheme.getBackgroundDrawable(requireContext())
                        android.util.Log.d("ArmatureFragment", "Фоновое изображение загружено: ${bgDrawable != null}")
                        
                        if (bgDrawable != null && isAdded) {
                            withContext(Dispatchers.Main) {
                                if (isAdded && v.isAttachedToWindow) {
                                    android.util.Log.d("ArmatureFragment", "Применяем фоновое изображение к view")
                                    v.background = bgDrawable
                                    if (::recyclerView.isInitialized && recyclerView.isAttachedToWindow) {
                                        recyclerView.background = bgDrawable.constantState?.newDrawable()?.mutate()
                                    }
                                } else {
                                    android.util.Log.w("ArmatureFragment", "View не готов: isAdded=$isAdded, isAttached=${v.isAttachedToWindow}")
                                }
                            }
                        } else {
                            android.util.Log.d("ArmatureFragment", "Фоновое изображение отсутствует или фрагмент не добавлен")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ArmatureFragment", "Ошибка загрузки фонового изображения", e)
                    } finally {
                        isLoadingBackground = false
                    }
                }
            } else {
                android.util.Log.d("ArmatureFragment", "Загрузка фона уже в процессе, пропускаем")
            }
            
            // Применяем тему к кнопкам
            applyThemeToButtons()
            
            // Обновляем адаптер ТОЛЬКО ОДИН РАЗ!
            if (::adapter.isInitialized) {
                adapter.notifyDataSetChanged()
            }
        }
    }
    
    override fun refreshData() {
        Log.d("ArmatureFragment", "refreshData() called - forcing data reload")
        
        // Уведомляем SearchManager об изменении данных (новая функциональность)
        if (::searchManager.isInitialized) {
            searchManager.notifyDataChanged()
        }
        
        // Очищаем кэши поиска (новая функциональность)
        com.example.vkbookandroid.utils.SearchNormalizer.clearCaches()
        
        // Перезагружаем данные (оригинальная функциональность)
        isDataLoaded = false
        loadArmatureData()
        
        // Загружаем JSON координаты после обновления данных (оригинальная функциональность)
        loadArmatureCoordinates()
        
        // Обновляем поиск если он активен (новая функциональность)
        val currentQuery = if (::searchView.isInitialized) {
            searchView.query?.toString() ?: ""
        } else ""
        
        if (currentQuery.isNotEmpty()) {
            lifecycleScope.launch {
                delay(100) // Ждем загрузки новых данных
                if (::searchManager.isInitialized && isDataReadyForSearch()) {
                    // ИСПРАВЛЕНИЕ: Очищаем кэш перед повторным поиском
                    searchManager.clearCache()
                    val rid = (++nextRequestId)
                    activeRequestId = rid
                    performEnhancedSearch(currentQuery, rid)
                }
            }
        }
    }
    
    override fun isDataLoaded(): Boolean {
        return isDataLoaded
    }
    
    override fun getWatchedFilePath(): String? {
        return try {
            val baseUrl = ServerSettingsActivity.getCurrentServerUrl(requireContext())
            if (baseUrl != "http://10.0.2.2:8082/") {
                // Для удаленного режима - путь к файлу в data директории
                val dataDir = requireContext().filesDir.resolve("data")
                dataDir.resolve("Armatures.xlsx").absolutePath
            } else {
                // Для локального режима - путь к файлу в assets
                null // Assets файлы не отслеживаются
            }
        } catch (e: Exception) {
            Log.e("ArmatureFragment", "Error getting watched file path", e)
            null
        }
    }
    
    /**
     * Загрузить координаты арматур из JSON для определения кликабельности
     */
    private fun loadArmatureCoordinates() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d("ArmatureFragment", "Loading armature coordinates for clickability...")
                val coordinates = armatureRepository?.loadMarkersFromFilesDir()
                if (coordinates != null && coordinates.isNotEmpty()) {
                    Log.d("ArmatureFragment", "Loaded coordinates for ${coordinates.size} PDF files")
                    
                    // Создаем список всех арматур, которые имеют координаты
                    val clickableArmatures = mutableSetOf<String>()
                    coordinates.forEach { (pdfName, markers) ->
                        markers.forEach { (armatureId, _) ->
                            clickableArmatures.add(armatureId)
                        }
                    }
                    
                    Log.d("ArmatureFragment", "Clickable armatures: $clickableArmatures")
                    
                    withContext(Dispatchers.Main) {
                        // Обновляем адаптер с информацией о кликабельности
                        adapter.setClickableArmatures(clickableArmatures)
                        adapter.notifyDataSetChanged()
                    }
                } else {
                    Log.w("ArmatureFragment", "No coordinates found")
                }
            } catch (e: Exception) {
                Log.e("ArmatureFragment", "Error loading armature coordinates", e)
            }
        }
    }
    
    /**
     * Обновить данные после синхронизации
     */
    fun refreshDataAfterSync() {
        Log.d("ArmatureFragment", "Refreshing data after sync...")
        isDataLoaded = false
        pagingSession = null
        cachedSession = null
        nextStartRow = 0
        
        // Очищаем кэш Excel файлов, чтобы загрузить новые данные
        clearExcelCache()
        
        loadArmatureData()
        
        // Также загружаем координаты из JSON для обновления кликабельности
        loadArmatureCoordinates()
    }
    
    /**
     * Очистить кэш Excel файлов для принудительного обновления
     * НЕ очищаем remote_cache - там хранятся синхронизированные файлы!
     */
    private fun clearExcelCache() {
        try {
            val cacheDir = File(requireContext().cacheDir, "excel_cache")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
                Log.d("ArmatureFragment", "Excel parsing cache cleared (keeping remote_cache)")
            }
        } catch (e: Exception) {
            Log.e("ArmatureFragment", "Error clearing Excel cache", e)
        }
    }
    
    /**
     * Загрузить маркеры из armature_coords.json (новые данные с сервера)
     */
    private fun loadMarkersFromJson() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val armatureRepository = com.example.vkbookandroid.repository.ArmatureRepository(
                    requireContext(), 
                    com.example.vkbookandroid.network.NetworkModule.getArmatureApiService()
                )
                
                val serverData = armatureRepository.loadArmatureCoordsFromServer()
                if (serverData != null && serverData.isNotEmpty()) {
                    // Подсчитываем общее количество маркеров
                    val totalMarkers = serverData.values.sumOf { it.size }

                    withContext(Dispatchers.Main) {
                        // Показываем Toast с информацией о загруженных маркерах
                        Toast.makeText(requireContext(), "Загружено $totalMarkers маркеров с сервера", Toast.LENGTH_SHORT).show()
                        Log.d("ArmatureFragment", "Loaded $totalMarkers markers from server")
                    }
                }
            } catch (e: Exception) {
                Log.e("ArmatureFragment", "Error loading markers from JSON", e)
            }
        }
    }
    
    /**
     * Инициализация новой системы поиска
     */
    private fun initializeSearchSystem() {
        try {
            // Инициализируем SearchManager
            searchManager = SearchManager(requireContext())
            
            // Инициализируем VoiceSearchHelper
            voiceSearchHelper = VoiceSearchHelper(this, voiceSearchLauncher)
            
            // Настраиваем наблюдатели
            setupSearchObservers()
            
            Log.d("ArmatureFragment", "Search system initialized successfully")
        } catch (e: Exception) {
            Log.e("ArmatureFragment", "Error initializing search system", e)
        }
    }
    
    /**
     * Настройка наблюдателей для SearchManager
     */
    private fun setupSearchObservers() {
        // Наблюдаем за результатами поиска
        searchManager.searchResults.observe(viewLifecycleOwner, Observer { results ->
            if (results != null) {
                // Игнорируем, если пришло не для текущего запроса
                if (results.requestId >= 0 && results.requestId != activeRequestId) {
                    Log.d("ArmatureFragment", "Observer: outdated results requestId=${results.requestId}, active=${activeRequestId}")
                    return@Observer
                }
                // Дополнительно проверим нормализованный текст
                val expected = currentSearchQuery.trim()
                if (results.normalizedQuery.isNotEmpty()) {
                    val same = com.example.vkbookandroid.utils.SearchNormalizer.normalizeSearchQuery(expected) == results.normalizedQuery
                    if (!same) {
                        Log.d("ArmatureFragment", "Observer: normalized query mismatch. expected='$expected', got='${results.normalizedQuery}'")
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
                searchProgress.visibility = android.view.View.VISIBLE
                Log.d("ArmatureFragment", "Search started")
            } else {
                // Скрываем индикатор загрузки
                searchProgress.visibility = android.view.View.GONE
                Log.d("ArmatureFragment", "Search completed")
            }
        })
        
        // Наблюдаем за историей поиска
        searchManager.searchHistory.observe(viewLifecycleOwner, Observer { history ->
            // Обновляем историю поиска в UI (если используем EnhancedSearchView)
            Log.d("ArmatureFragment", "Search history updated: ${history.size} items")
        })
    }
    
    /**
     * Обработка результатов поиска от SearchManager
     */
    private fun handleSearchResults(results: SearchManager.SearchResults) {
        Log.d("ArmatureFragment", "Handling search results: ${results.totalCount} items, from cache: ${results.fromCache}")
        // Применяем только самый свежий ответ
        if (results.requestId >= 0 && results.requestId != activeRequestId) {
            Log.d("ArmatureFragment", "Ignoring outdated results for requestId=${results.requestId}, active=${activeRequestId}")
            return
        }
        
        try {
            if (results.results.isNotEmpty()) {
                // ИСПРАВЛЕНИЕ: Сортируем результаты по релевантности (лучшие совпадения наверх)
                val sortedResults = results.results.sortedByDescending { it.matchScore }
                Log.d("ArmatureFragment", "Sorted search results by relevance: top score = ${sortedResults.firstOrNull()?.matchScore}")
                // Не сбрасываем режим поиска по столбцу при вводе текста.
                // Он должен отключаться только повторным нажатием на заголовок.
                
                // Обновляем через DiffUtil и затем всегда скроллим к началу
                adapter.updateSearchResults(sortedResults, currentSearchQuery)
                recyclerView.post {
                    try { (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0) } catch (_: Throwable) {}
                    scrollToTopOnNextResults = false
                    recyclerView.requestLayout()
                }
                // Доп. закрепление после layout
                recyclerView.viewTreeObserver?.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        try { (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0) } catch (_: Throwable) {}
                        recyclerView.viewTreeObserver?.removeOnGlobalLayoutListener(this)
                    }
                })
                // Показать таблицу и скрыть пустой вид
                (recyclerView.parent as? View)?.visibility = View.VISIBLE
                emptyView?.visibility = View.GONE
                
                // ИСПРАВЛЕНИЕ: НЕ прокручиваем таблицу - найденные строки уже перемещены наверх
                // Пользователь остается на том же месте, но видит найденные результаты сверху
                Log.d("ArmatureFragment", "Search results reordered: most relevant (${sortedResults.size} results) moved to top")
            } else {
                // Пустой результат: показываем пустой вид и скрываем таблицу
                // Не возвращаем полную таблицу при отсутствии совпадений
                try { adapter.updateFilteredDataPreserveOrder(emptyList(), currentSearchQuery) } catch (_: Throwable) {}
                recyclerView.post {
                    try { (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0) } catch (_: Throwable) {}
                    scrollToTopOnNextResults = false
                    recyclerView.requestLayout()
                }
                recyclerView.viewTreeObserver?.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        try { (recyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0) } catch (_: Throwable) {}
                        recyclerView.viewTreeObserver?.removeOnGlobalLayoutListener(this)
                    }
                })
                Log.d("ArmatureFragment", "No search results found for query: '$currentSearchQuery'")
                // Скрыть таблицу и показать пустой вид
                (recyclerView.parent as? View)?.visibility = View.GONE
                emptyView?.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            Log.e("ArmatureFragment", "Error handling search results", e)
            // Fallback к старому методу
            val rowData = results.results.map { it.data }
            adapter.setSearchResults(rowData, currentSearchQuery)
        }
    }
    
    /**
     * Улучшенная фильтрация данных с использованием SearchManager
     */
    private suspend fun performEnhancedSearch(searchText: String, requestId: Int) {
        val normalizedSearch = searchText.trim()
        
        Log.d("ArmatureFragment", "=== PERFORMING ENHANCED SEARCH ===")
        Log.d("ArmatureFragment", "Search text: '$normalizedSearch'")
        
        if (!isDataReadyForSearch()) {
            Log.w("ArmatureFragment", "Cannot perform enhanced search: data not ready yet")
            return
        }
        
        try {
            val originalData = adapter.getOriginalData()
            // НОВОЕ: Индексируем ВСЁ содержимое Excel (читаем постранично до конца)
            val dataForSearch = withContext(Dispatchers.IO) {
                try {
                    val combined = mutableListOf<org.example.pult.RowDataDynamic>()
                    combined.addAll(originalData)

                    // Используем доступную сессию для дозагрузки батчей, не меняя UI
                    val session = cachedSession ?: pagingSession
                    var cursor = originalData.size
                    val step = pageSize

                    while (session != null) {
                        val toRead = step
                        val next = try {
                            session.readRange(cursor, toRead)
                        } catch (e: Exception) {
                            Log.w("ArmatureFragment", "ReadRange failed at $cursor+$toRead: ${e.message}")
                            emptyList()
                        }
                        if (next.isEmpty()) break
                        combined.addAll(next)
                        cursor += next.size
                    }

                    Log.d(
                        "ArmatureFragment",
                        "Data for search prepared: original=${originalData.size}, total=${combined.size}"
                    )
                    combined.toList()
                } catch (e: Exception) {
                    Log.w("ArmatureFragment", "Failed to prepare extended data for search, fallback to original", e)
                    originalData
                }
            }
            val headers = adapter.headers
            val selectedColumn = if (adapter.hasSelectedColumn()) {
                adapter.getSelectedColumnName()
            } else null
            
            Log.d("ArmatureFragment", "Search parameters:")
            Log.d("ArmatureFragment", "  - Data rows (for search): ${dataForSearch.size}")
            Log.d("ArmatureFragment", "  - Headers: ${headers.size}")
            Log.d("ArmatureFragment", "  - Selected column: $selectedColumn")
            
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
            Log.e("ArmatureFragment", "Error during enhanced search", e)
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