package com.example.vkbookandroid

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ArmatureFragment : Fragment() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SignalsAdapter
    private lateinit var excelDataManager: AppExcelDataManager
    private lateinit var excelRepository: ExcelRepository
    private var currentColumnWidths: MutableMap<String, Int> = mutableMapOf()
    private lateinit var searchView: SearchView
    private lateinit var toggleResizeModeButton: Button
    private var isResizingMode: Boolean = false
    private var isDataLoaded: Boolean = false
    private var isLoadingPage: Boolean = false
    private var nextStartRow: Int = 0
    private val pageSize: Int = 100
    private var pagingSession: ExcelPagingSession? = null
    private var cachedSession: PagingSession? = null
    private var lastMeasuredListWidth: Int = 0
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_armature, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        recyclerView.setHasFixedSize(true)
        recyclerView.setItemViewCacheSize(20)
        adapter = SignalsAdapter(emptyList(), isResizingMode, { columnIndex, newWidth, action ->
            val headerName = adapter.headers.getOrNull(columnIndex)
            if (headerName != null) {
                currentColumnWidths[headerName] = newWidth
                adapter.updateColumnWidths(columnIndex, newWidth, false)
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    val sharedPrefs = requireContext().getSharedPreferences("ColumnWidthsArmature", Context.MODE_PRIVATE)
                    sharedPrefs.edit().putString("Armatures.xlsx", Gson().toJson(currentColumnWidths)).apply()
                    Log.d("ArmatureFragment", "Column $headerName resized to $newWidth. Saved: $currentColumnWidths")
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
            val pdfDesignationPattern = Regex("(?i)([^\\s;|#]+\\.pdf)(?:[#;|\\s]+(.+))?")
            val match = pdfDesignationPattern.find(cell)
            val pdfName = match?.groupValues?.getOrNull(1)
            val parsedId = match?.groupValues?.getOrNull(2)?.trim()?.trim('-', ' ', ';', '|', '#')
            val designation: String? = if (!parsedId.isNullOrBlank()) parsedId else armatureId

            if (!pdfName.isNullOrBlank() && !designation.isNullOrBlank()) {
                val intent = android.content.Intent(requireContext(), PdfViewerActivity::class.java).apply {
                    putExtra("pdf_path", "assets/Schemes/$pdfName")
                    putExtra("designation", designation)
                }
                startActivity(intent)
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
            val pattern = Regex("(?i)([^\\s;|#]+\\.pdf)(?:[#;|\\s]+(.+))?")
            val match = pattern.find(cell)
            val pdfName = match?.groupValues?.getOrNull(1)
            val parsedId = match?.groupValues?.getOrNull(2)?.trim()?.trim('-', ' ', ';', '|', '#')
            val designation: String? = if (!parsedId.isNullOrBlank()) parsedId else armId
            if (pdfName.isNullOrBlank() || designation.isNullOrBlank()) return@SignalsAdapter
            val intent = android.content.Intent(requireContext(), PdfViewerActivity::class.java).apply {
                putExtra("pdf_path", "assets/Schemes/$pdfName")
                putExtra("designation", designation)
            }
            startActivity(intent)
        })
        recyclerView.adapter = adapter
        // Гарантируем первичную разметку и видимость после ротации
        view.post {
            recyclerView.adapter?.notifyDataSetChanged()
            recyclerView.requestLayout()
        }
        // Отключаем звуки кликов у всего дерева фрагмента
        view.isSoundEffectsEnabled = false
        
        excelDataManager = AppExcelDataManager(requireContext().applicationContext)
        val baseUrl = getString(R.string.remote_base_url)
        val remote = if (baseUrl.isNotEmpty()) RemoteFileProvider(requireContext(), baseUrl) else null
        excelRepository = ExcelRepository(requireContext(), remote)
        searchView = view.findViewById(R.id.search_view)
        toggleResizeModeButton = view.findViewById(R.id.toggle_resize_mode_button)

        setupSearch()
        setupToggleResizeModeButton()
        attachWidthAutoScaler()
    }

    override fun onResume() {
        super.onResume()
        if (isVisible) ensureDataLoaded()
        // После ротации иногда список не перерисовывается до переключения вкладки — форсируем ребайндинг
        recyclerView.post {
            recyclerView.adapter?.notifyDataSetChanged()
            recyclerView.requestLayout()
        }
    }
    
    private fun loadArmatureData() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ctx = requireContext()
                // 1) быстрый старт из кэша, если он есть
                cachedSession = excelRepository.openCachedSessionArmatures(pageSize)
                val sessionForInitial = cachedSession ?: run {
                    pagingSession = excelRepository.openPagingSessionArmatures()
                    pagingSession as PagingSession
                }
                val initialColumnWidths = sessionForInitial.getColumnWidths()

                if (initialColumnWidths.isNotEmpty()) {
                    val sharedPrefs = ctx.getSharedPreferences("ColumnWidthsArmature", Context.MODE_PRIVATE)
                    val type = object : TypeToken<MutableMap<String, Int>>() {}.type
                    val savedColumnWidths: MutableMap<String, Int> = Gson().fromJson(sharedPrefs.getString("Armatures.xlsx", null), type) ?: mutableMapOf()
                    initialColumnWidths.forEach { (header, width) ->
                        if (!savedColumnWidths.containsKey(header) || savedColumnWidths[header] == null) {
                            savedColumnWidths[header] = width
                        }
                    }
                    currentColumnWidths = savedColumnWidths
                } else {
                    currentColumnWidths = mutableMapOf()
                }

                val headers = sessionForInitial.getHeaders()

                val firstPage = sessionForInitial.readRange(0, pageSize)
                nextStartRow = firstPage.size

                withContext(Dispatchers.Main) {
                    adapter.updateData(firstPage, headers, currentColumnWidths, isResizingMode, updateOriginal = true)
                    isDataLoaded = true
                    attachPaging()
                }

                // 2) фоновая пересборка кэша и бесшовное обновление
                excelRepository.refreshCacheArmatures(pageSize) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val newCached = excelRepository.openCachedSessionArmatures(pageSize)
                        if (newCached != null) {
                            val newHeaders = newCached.getHeaders()
                            val newFirstPage = newCached.readRange(0, pageSize)
                            nextStartRow = newFirstPage.size
                            withContext(Dispatchers.Main) {
                                adapter.updateData(newFirstPage, newHeaders, currentColumnWidths, isResizingMode, updateOriginal = true)
                            }
                            cachedSession = newCached
                            pagingSession?.close()
                            pagingSession = null
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ArmatureFragment", "Error loading armature data", e)
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

            val sharedPrefs = requireContext().getSharedPreferences("ColumnWidthsArmature", Context.MODE_PRIVATE)
            sharedPrefs.edit().putString("Armatures.xlsx", Gson().toJson(currentColumnWidths)).apply()
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
                filterData(query.orEmpty())
                (activity as? MainActivity)?.onFragmentSearchQueryChanged(query.orEmpty())
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterData(newText.orEmpty())
                (activity as? MainActivity)?.onFragmentSearchQueryChanged(newText.orEmpty())
                return true
            }
        })

        val searchAutoComplete = searchView.findViewById<androidx.appcompat.widget.SearchView.SearchAutoComplete>(
            androidx.appcompat.R.id.search_src_text
        )
        searchAutoComplete?.apply {
            setTextColor(android.graphics.Color.BLACK)
            setHintTextColor(android.graphics.Color.parseColor("#666666"))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
            alpha = 1f
        }
    }

    private fun setupToggleResizeModeButton() {
        toggleResizeModeButton.setOnClickListener {
            isResizingMode = !isResizingMode
            toggleResizeModeButton.text = if (isResizingMode) "Сохранить" else "Редактировать"
            adapter.setResizingMode(isResizingMode)
            recyclerView.isNestedScrollingEnabled = true
        }
    }
    
    fun filterData(searchText: String) {
        adapter.filter(searchText)
    }

    fun setSearchQueryExternal(query: String) {
        if (!isAdded) return
        if (searchView.query?.toString() == query) return
        searchView.setQuery(query, false)
        filterData(query)
    }

    fun ensureDataLoaded() {
        if (!isDataLoaded) {
            loadArmatureData()
        }
    }
} 