package com.example.vkbookandroid

import android.content.Context
import android.content.ClipData
import android.graphics.Typeface
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.View.DragShadowBuilder
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.example.pult.RowDataDynamic
import org.example.pult.android.dpToPx
import android.view.MotionEvent
import android.os.Handler
import android.os.Looper
import com.example.vkbookandroid.utils.SearchNormalizer
import com.example.vkbookandroid.search.SearchResult
import com.example.vkbookandroid.search.SearchDiffCallback
import com.example.vkbookandroid.search.DataDiffCallback
import com.example.vkbookandroid.search.DiffUtilHelper
import androidx.recyclerview.widget.DiffUtil
import com.example.vkbookandroid.theme.AppTheme
import kotlinx.coroutines.*
import java.util.LinkedHashMap

class SignalsAdapter(
    private var data: List<RowDataDynamic>,
    private var _isResizingMode: Boolean,
    private val onColumnResize: (columnIndex: Int, newWidth: Int, action: Int) -> Unit,
    private val onRowClick: ((RowDataDynamic) -> Unit)? = null,
    private val onArmatureCellClick: ((RowDataDynamic) -> Unit)? = null,
    private val hidePdfSchemeColumn: Boolean = true,
    private val onCellClick: ((rowIndex: Int, columnIndex: Int, headerName: String, currentValue: String, rowData: RowDataDynamic) -> Unit)? = null,
    private val onColumnReorder: ((List<String>) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var headers: MutableList<String> = mutableListOf() // allow reordering
    private var columnWidths: Map<String, Int> = emptyMap()
    private var _originalData: List<RowDataDynamic> = emptyList()
    var currentSearchQuery: String = ""
    private var armatureColIndex: Int = -1
    private var pdfSchemeColIndex: Int = -1
    private val hiddenHeaderNames: Set<String> = setOf("PDF_Схема_и_ID_арматуры")
    private var clickableArmatures: Set<String> = emptySet() // Арматуры с координатами в JSON
    private var selectedColumnHeaderName: String? = null
    
    // Новые поля для улучшенного поиска
    private var currentSearchResults: List<SearchResult> = emptyList()
    private var showingSearchResults: Boolean = false
    private val adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var onAfterSearchResultsApplied: (() -> Unit)? = null
    
    // Переиспользуемый Handler для оптимизации
    private val mainHandler = Handler(Looper.getMainLooper())

    fun setOnAfterSearchResultsApplied(listener: (() -> Unit)?) {
        onAfterSearchResultsApplied = listener
    }
    
    // Вспомогательная функция для восстановления политики адаптера
    private fun restoreAdapterStatePolicy() {
        try {
            mainHandler.postDelayed({
                this.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.ALLOW
            }, 150)
        } catch (_: Throwable) {}
    }

    private fun isHidden(headerName: String): Boolean {
        return hidePdfSchemeColumn && hiddenHeaderNames.any { it.equals(headerName, ignoreCase = true) }
    }

    // Максимальная ширина колонки: 150 мм по физическому размеру экрана
    private fun maxColumnWidthPx(context: Context): Int {
        val xdpi = context.resources.displayMetrics.xdpi
        return ((150f * xdpi) / 25.4f).toInt().coerceAtLeast(context.dpToPx(40))
    }
    private fun minColumnWidthPx(context: Context): Int {
        return context.dpToPx(48)
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ROW = 1
    }

    // ==== ViewHolder для заголовка ====
    class HeaderViewHolder(itemView: View, private var _isResizingMode: Boolean, private val onColumnResize: (columnIndex: Int, newWidth: Int, action: Int) -> Unit, private val adapter: SignalsAdapter) : RecyclerView.ViewHolder(itemView) {
        private val headerContainer: LinearLayout = itemView.findViewById(R.id.linearLayoutRowContent)
        private var _initialX = 0f
        private var _initialWidth = 0
        private val longPressHandler = Handler(Looper.getMainLooper())
        private var pendingSelection: Runnable? = null
        private var dragging = false
        private var dragFromIndex = -1 // visible index among headerTextViews
        private var dragTargetIndex = -1 // visible index
        private var dragFromActualIndex = -1 // index in headers list
        private var dragTargetActualIndex = -1 // index in headers list
        private var autoScrollDir = 0 // -1 left, +1 right
        private var autoScrollRunning = false
        
        // Очистка ресурсов
        fun unbind() {
            pendingSelection?.let { longPressHandler.removeCallbacks(it) }
            pendingSelection = null
        }

        fun bind(headers: List<String>, columnWidths: Map<String, Int>, totalRowWidth: Int, context: Context, isResizingMode: Boolean) {
            _isResizingMode = isResizingMode
            headerContainer.removeAllViews()
            headerContainer.layoutParams.width = totalRowWidth
            var maxHeaderHeight = 0
            val headerTextViews = mutableListOf<TextView>()
            val visibleIndices = headers.mapIndexedNotNull { idx, h -> if (!adapter.isHidden(h)) idx else null }

            headers.forEachIndexed { index, headerName ->
                if (adapter.isHidden(headerName)) return@forEachIndexed
                val colWidthRaw = columnWidths[headerName] ?: context.dpToPx(100) // Default width in dp
                val colWidth = colWidthRaw.coerceIn(adapter.minColumnWidthPx(context), adapter.maxColumnWidthPx(context))
                val textView = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(colWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
                    text = headerName
                    if (adapter.isColumnSelected(headerName)) {
                        setBackgroundResource(R.drawable.cell_header_selected)
                    } else {
                        setBackgroundResource(R.drawable.cell_border)
                    }
                    gravity = Gravity.START or Gravity.TOP
                    setPadding(context.dpToPx(4), context.dpToPx(4), context.dpToPx(4), context.dpToPx(4))
                    setTypeface(typeface, Typeface.BOLD)
                    isSoundEffectsEnabled = false
                }
                textView.measure(View.MeasureSpec.makeMeasureSpec(colWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                maxHeaderHeight = maxOf(maxHeaderHeight, textView.measuredHeight)
                headerTextViews.add(textView)
                headerContainer.addView(textView)

                if (_isResizingMode) {
                    // Режим редактирования: длинный тап по заголовку — перетаскивание для смены порядка
                    textView.setOnLongClickListener {
                        dragging = true
                        dragFromIndex = headerTextViews.indexOf(textView)
                        dragTargetIndex = dragFromIndex
                        dragFromActualIndex = visibleIndices.getOrElse(dragFromIndex) { -1 }
                        dragTargetActualIndex = dragFromActualIndex
                        (itemView.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                        // Запускаем системный drag с призрачной тенью
                        try {
                            val data = ClipData.newPlainText("header", headers.getOrNull(dragFromIndex) ?: "")
                            textView.startDragAndDrop(data, DragShadowBuilder(textView), null, 0)
                        } catch (_: Throwable) {
                            try { textView.startDrag(null, DragShadowBuilder(textView), null, 0) } catch (_: Throwable) {}
                        }
                        true
                    }
                    headerContainer.setOnTouchListener { _, ev ->
                        if (!dragging) return@setOnTouchListener false
                        when (ev.action) {
                            MotionEvent.ACTION_MOVE -> {
                                // Определяем целевой индекс по позиции пальца относительно центров заголовков
                                val target = run {
                                    var idx = 0
                                    var bestIdx = 0
                                    var bestDist = Float.MAX_VALUE
                                    headerTextViews.forEach { tv ->
                                        val centerX = tv.x + tv.width / 2f
                                        val d = kotlin.math.abs(ev.x - centerX)
                                        if (d < bestDist) { bestDist = d; bestIdx = idx }
                                        idx++
                                    }
                                    bestIdx
                                }
                                if (target in headerTextViews.indices) {
                                    dragTargetIndex = target
                                    dragTargetActualIndex = visibleIndices.getOrElse(dragTargetIndex) { -1 }
                                }
                                // Автопрокрутка при подходе к краям
                                val threshold = (32 * itemView.resources.displayMetrics.density).toInt()
                                val hscroll = findAncestorHScroll(headerContainer)
                                val wantDir = when {
                                    ev.x < threshold -> -1
                                    ev.x > headerContainer.width - threshold -> +1
                                    else -> 0
                                }
                                if (wantDir != autoScrollDir) {
                                    autoScrollDir = wantDir
                                    if (autoScrollDir == 0) stopAutoScroll() else startAutoScroll(hscroll)
                                }
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                dragging = false
                                if (dragFromActualIndex >= 0 && dragTargetActualIndex >= 0 && dragTargetActualIndex != dragFromActualIndex) {
                                    try { adapter.moveHeader(dragFromActualIndex, dragTargetActualIndex) } catch (_: Throwable) {}
                                }
                                dragFromIndex = -1
                                dragTargetIndex = -1
                                dragFromActualIndex = -1
                                dragTargetActualIndex = -1
                                stopAutoScroll()
                                headerContainer.setOnTouchListener(null)
                                true
                            }
                            else -> false
                        }
                    }
                    // Принимаем drop внутри контейнера для совместимости с системным drag
                    headerContainer.setOnDragListener { _, event ->
                        when (event.action) {
                            android.view.DragEvent.ACTION_DRAG_LOCATION -> {
                                // Подсчет целевого индекса по X в координатах контейнера
                                val x = event.x
                                var idx = 0
                                var bestIdx = 0
                                var bestDist = Float.MAX_VALUE
                                headerTextViews.forEach { tv ->
                                    val centerX = tv.x + tv.width / 2f
                                    val d = kotlin.math.abs(x - centerX)
                                    if (d < bestDist) { bestDist = d; bestIdx = idx }
                                    idx++
                                }
                                dragTargetIndex = bestIdx
                                dragTargetActualIndex = visibleIndices.getOrElse(dragTargetIndex) { -1 }
                                true
                            }
                            android.view.DragEvent.ACTION_DROP, android.view.DragEvent.ACTION_DRAG_ENDED -> {
                                if (dragFromActualIndex >= 0 && dragTargetActualIndex >= 0 && dragTargetActualIndex != dragFromActualIndex) {
                                    try { adapter.moveHeader(dragFromActualIndex, dragTargetActualIndex) } catch (_: Throwable) {}
                                }
                                dragging = false
                                dragFromIndex = -1
                                dragTargetIndex = -1
                                dragFromActualIndex = -1
                                dragTargetActualIndex = -1
                                stopAutoScroll()
                                true
                            }
                            else -> true
                        }
                    }
                } else {
                    // Режим просмотра: долгий тап по заголовку включает поиск по столбцу
                var longPressActivated = false
                textView.setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            longPressActivated = false
                            pendingSelection?.let { longPressHandler.removeCallbacks(it) }
                            pendingSelection = Runnable {
                                longPressActivated = true
                                adapter.activateColumnSearch(headerName)
                            }
                            longPressHandler.postDelayed(pendingSelection!!, 3000)
                                false
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            pendingSelection?.let { longPressHandler.removeCallbacks(it) }
                            pendingSelection = null
                            if (longPressActivated) {
                                longPressActivated = false
                                return@setOnTouchListener true
                            }
                            false
                        }
                        else -> false
                    }
                }
                textView.setOnClickListener {
                    if (adapter.isColumnSelected(headerName)) {
                        adapter.deactivateColumnSearch()
                        }
                    }
                }

                val isLastVisible = visibleIndices.isNotEmpty() && index == visibleIndices.last()
                if (!isLastVisible) {
                    val resizeHandleWidth = context.dpToPx(16)
                    val resizeHandle = View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(resizeHandleWidth, LinearLayout.LayoutParams.MATCH_PARENT) // Изменено на MATCH_PARENT
                        setBackgroundResource(R.drawable.search_background) // Placeholder background for visibility
                        alpha = 0.5f // Make it semi-transparent
                        visibility = if (_isResizingMode) View.VISIBLE else View.GONE // Set visibility based on mode
                        isClickable = true
                        isFocusable = true
                        isFocusableInTouchMode = true
                        isSoundEffectsEnabled = false
                    }
                    resizeHandle.measure(View.MeasureSpec.makeMeasureSpec(resizeHandleWidth, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    maxHeaderHeight = maxOf(maxHeaderHeight, resizeHandle.measuredHeight)
                    headerContainer.addView(resizeHandle)

                    if (_isResizingMode) {
                        resizeHandle.setOnTouchListener { v, event ->
                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    _initialX = event.rawX
                                    _initialWidth = (textView.layoutParams as LinearLayout.LayoutParams).width
                                    Log.d("HeaderResize", "ACTION_DOWN: initialX=$_initialX, initialWidth=$_initialWidth")
                                    (v.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                                    (itemView.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                                    onColumnResize(index, _initialWidth, event.action)
                                    true
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    val dx = event.rawX - _initialX
                                    val newWidth = (_initialWidth + dx).toInt().coerceIn(adapter.minColumnWidthPx(context), adapter.maxColumnWidthPx(context))
                                    Log.d("HeaderResize", "ACTION_MOVE: dx=$dx, newWidth=$newWidth")
                                    (v.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                                    (itemView.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                                    (textView.layoutParams as LinearLayout.LayoutParams).width = newWidth
                                    textView.requestLayout()
                                    // Update total row width dynamically during resize
                                    headerContainer.layoutParams.width = adapter.calculateTotalRowWidth(headers, columnWidths, context, index, newWidth)
                                    headerContainer.requestLayout()
                                    onColumnResize(index, newWidth, event.action)
                                    true
                                }
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                    Log.d("HeaderResize", "ACTION_UP/CANCEL: Final width=${(textView.layoutParams as LinearLayout.LayoutParams).width}")
                                    onColumnResize(index, (textView.layoutParams as LinearLayout.LayoutParams).width, event.action)
                                    _initialX = 0f
                                    _initialWidth = 0
                                    v.performClick()
                                    true
                                }
                                else -> true
                            }
                        }
                    } else {
                        resizeHandle.setOnTouchListener(null)
                    }
                }
            }
            // Добавляем правый конечный хэндл для изменения самой правой колонки
            if (headerTextViews.isNotEmpty()) {
                val lastIndex = visibleIndices.lastOrNull() ?: -1
                val lastTextView = headerTextViews.last()
                val resizeHandleWidth = context.dpToPx(16)
                val trailingHandle = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(resizeHandleWidth, LinearLayout.LayoutParams.MATCH_PARENT)
                    setBackgroundResource(R.drawable.search_background)
                    alpha = 0.5f
                    visibility = if (_isResizingMode) View.VISIBLE else View.GONE
                    isClickable = true
                    isFocusable = true
                    isFocusableInTouchMode = true
                    isSoundEffectsEnabled = false
                }
                if (_isResizingMode) {
                    trailingHandle.setOnTouchListener { v, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                _initialX = event.rawX
                                _initialWidth = (lastTextView.layoutParams as LinearLayout.LayoutParams).width
                                (v.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                                (itemView.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                                onColumnResize(lastIndex, _initialWidth, event.action)
                                true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val dx = event.rawX - _initialX
                                val newWidth = (_initialWidth + dx).toInt().coerceIn(adapter.minColumnWidthPx(context), adapter.maxColumnWidthPx(context))
                                (v.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                                (itemView.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                                (lastTextView.layoutParams as LinearLayout.LayoutParams).width = newWidth
                                lastTextView.requestLayout()
                                headerContainer.layoutParams.width = adapter.calculateTotalRowWidth(headers, columnWidths, context, lastIndex, newWidth)
                                headerContainer.requestLayout()
                                onColumnResize(lastIndex, newWidth, event.action)
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                onColumnResize(lastIndex, (lastTextView.layoutParams as LinearLayout.LayoutParams).width, event.action)
                                _initialX = 0f
                                _initialWidth = 0
                                v.performClick()
                                true
                            }
                            else -> true
                        }
                    }
                } else {
                    trailingHandle.setOnTouchListener(null)
                }
                trailingHandle.measure(View.MeasureSpec.makeMeasureSpec(resizeHandleWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                maxHeaderHeight = maxOf(maxHeaderHeight, trailingHandle.measuredHeight)
                headerContainer.addView(trailingHandle)
            }

            val finalMaxHeaderHeight = maxOf(maxHeaderHeight, context.dpToPx(50))
            headerContainer.layoutParams.height = finalMaxHeaderHeight
            headerTextViews.forEach { textView ->
                textView.layoutParams.height = finalMaxHeaderHeight
                Log.d("HeaderViewHolder", "Header text: ${textView.text}, Final Height: ${textView.layoutParams.height}")
            }
            headerContainer.requestLayout()
        }

        private fun findAncestorHScroll(v: View): HorizontalScrollView? {
            var parent = v.parent
            while (parent is View) {
                if (parent is HorizontalScrollView) return parent
                parent = parent.parent
            }
            return null
        }

        private fun startAutoScroll(hscroll: HorizontalScrollView?) {
            if (hscroll == null) return
            if (autoScrollRunning) return
            autoScrollRunning = true
            val step = (16 * itemView.resources.displayMetrics.density).toInt().coerceAtLeast(8)
            val runnable = object : Runnable {
                override fun run() {
                    if (!autoScrollRunning || autoScrollDir == 0) return
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= 9) {
                            hscroll.smoothScrollBy(step * autoScrollDir, 0)
                        } else {
                            hscroll.scrollBy(step * autoScrollDir, 0)
                        }
                    } catch (_: Throwable) {}
                    // повторяем часто для плавности
                    longPressHandler.postDelayed(this, 16)
                }
            }
            longPressHandler.post(runnable)
        }

        private fun stopAutoScroll() {
            autoScrollRunning = false
            autoScrollDir = 0
            // mainHandler callbacks will naturally stop since we check autoScrollRunning
        }

        // Helper function to calculate total row width during resize
        private fun calculateTotalRowWidth(headers: List<String>, columnWidths: Map<String, Int>, columnIndex: Int, newWidth: Int, context: Context): Int {
            var totalWidth = 0
            var visibleCount = 0
            headers.forEachIndexed { i, headerName ->
                if (adapter.isHidden(headerName)) return@forEachIndexed
                visibleCount++
                if (i == columnIndex) {
                    totalWidth += newWidth
                } else {
                    val w = columnWidths[headerName] ?: context.dpToPx(100)
                    totalWidth += w.coerceAtMost(adapter.maxColumnWidthPx(context)) // Clamp to 50mm
                }
            }
            // В режиме редактирования добавляем пространство для handles
            if (_isResizingMode) {
                return totalWidth + visibleCount * context.dpToPx(16)
            } else {
                return totalWidth  // Без дополнительных зазоров в обычном режиме
            }
        }
    }

    // ==== ViewHolder для строки ====
    class RowViewHolder(itemView: View, private var _isResizingMode: Boolean, private val onColumnResize: (columnIndex: Int, newWidth: Int, action: Int) -> Unit, private val adapter: SignalsAdapter) : RecyclerView.ViewHolder(itemView) {
        private val cellContainer: LinearLayout = itemView.findViewById(R.id.linearLayoutRowContent)
        private var _initialX = 0f
        private var _initialWidth = 0

        fun bind(rowData: RowDataDynamic, headers: List<String>, columnWidths: Map<String, Int>, totalRowWidth: Int, context: Context, isResizingMode: Boolean) {
            _isResizingMode = isResizingMode
            cellContainer.removeAllViews()
            val values = rowData.getAllProperties()
            val query = adapter.currentSearchQuery
            // ВАЖНО: показываем ВСЕ ячейки для строк, попавших в выборку поиска.
            // Фильтрация строк выполняется на уровне данных (adapter.data), а не на уровне ячеек.
            var effectiveRowWidth = 0
            cellContainer.layoutParams.width = totalRowWidth

            var maxCellHeight = 0
            val textViews = mutableListOf<TextView>()
            val visibleIndices = headers.mapIndexedNotNull { idx, h -> if (!adapter.isHidden(h)) idx else null }

            Log.d("RowViewHolder", "Binding row: ${rowData.getAllProperties()}")
            headers.forEachIndexed { i, headerName ->
                if (adapter.isHidden(headerName)) return@forEachIndexed
                val colWidthRaw = columnWidths[headerName] ?: context.dpToPx(100) // Default width in dp
                val colWidth = colWidthRaw.coerceIn(adapter.minColumnWidthPx(context), adapter.maxColumnWidthPx(context))
                val cellTextValue = if (i < values.size) values[i]?.toString() ?: "" else ""

                val colIndex = i
                val textView = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(colWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
                    // Текст и подсветка
                    val cellText = cellTextValue
                    if (query.isNotEmpty()) {
                        val raw = cellText
                        // Подсвечиваем все ячейки с любым ненулевым совпадением
                        val score = SearchNormalizer.getMatchScore(raw, query)
                        val shouldHighlight = if (adapter.hasSelectedColumn()) {
                            // Если выбран столбец — по-прежнему подсвечиваем только его; но
                            // если строка уже попала в выдачу — слабые совпадения в других ячейках тоже подсветим
                            adapter.isColumnSelected(headerName) && score > 0 || score > 0
                        } else score > 0
                        if (shouldHighlight) {
                            adapter.highlightSearchTerms(this, cellText, query)
                        } else text = cellText
                    } else {
                        text = cellText
                    }
                    // Фоновая подсветка ячейки (градация по score)
                    if (query.isNotEmpty()) {
                        val score = SearchNormalizer.getMatchScore(cellText, query)
                        val shouldHighlightBg = score > 0
                        if (shouldHighlightBg) {
                            val useSolidHighlight = AppTheme.isRosatomTheme() || AppTheme.isNuclearTheme()
                            if (useSolidHighlight) {
                                setBackgroundResource(R.drawable.cell_highlight_solid)
                                try { backgroundTintList = null } catch (_: Throwable) {}
                            } else {
                                setBackgroundResource(R.drawable.cell_highlight)
                                val overlayColor = when {
                                    score >= 1000 -> 0xFF28A745.toInt()
                                    score >= 500 -> 0xFFFF9800.toInt()
                                    else -> 0xFFF44336.toInt()
                                }
                                try { backgroundTintList = android.content.res.ColorStateList.valueOf(overlayColor) } catch (_: Throwable) {}
                            }
                        } else {
                            setBackgroundResource(R.drawable.cell_border)
                            try { backgroundTintList = null } catch (_: Throwable) {}
                        }
                    } else {
                        setBackgroundResource(R.drawable.cell_border)
                        try { backgroundTintList = null } catch (_: Throwable) {}
                    }
                    gravity = Gravity.START or Gravity.TOP
                    setPadding(context.dpToPx(4), context.dpToPx(4), context.dpToPx(4), context.dpToPx(4))
                    textSize = 12f
                    isSoundEffectsEnabled = false
                }
                // Клик по ячейке для редактирования (если передан обработчик и включён режим редактирования)
                if (adapter.onCellClick != null && _isResizingMode) {
                    textView.setOnClickListener {
                        // КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Находим реальный индекс строки в данных адаптера
                        val rowIdx = adapter.data.indexOf(rowData).coerceAtLeast(0)
                        val headerName = if (colIndex < headers.size) headers[colIndex] else ""
                        val value = cellTextValue
                        adapter.onCellClick?.invoke(rowIdx, colIndex, headerName, value, rowData)
                    }
                }
                // Клик и подсветка для "Арматура" если есть PDF в колонке PDF_Схема_и_ID_арматуры
                if (!isResizingMode && adapter.armatureColIndex >= 0 && i == adapter.armatureColIndex) {
                    val armatureId = textView.text?.toString()?.trim()
                    val hasPdf = try {
                        val idxPdf = adapter.pdfSchemeColIndex
                        if (idxPdf >= 0 && idxPdf < values.size) {
                            val raw = values[idxPdf]
                            raw != null && raw.isNotBlank() && raw.contains(".pdf", ignoreCase = true)
                        } else false
                    } catch (_: Throwable) { false }
                    
                    if (hasPdf) {
                        try { textView.setTextColor(android.graphics.Color.parseColor("#1565C0")) } catch (_: Throwable) {}
                        textView.isClickable = true
                        textView.setOnClickListener { adapter.onArmatureCellClick?.invoke(rowData) }
                        Log.d("SignalsAdapter", "Armature $armatureId is clickable (has PDF: ${values[adapter.pdfSchemeColIndex]})")
                    } else {
                        Log.d("SignalsAdapter", "Armature $armatureId is not clickable (no PDF in column)")
                    }
                }
                textView.measure(View.MeasureSpec.makeMeasureSpec(colWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                maxCellHeight = maxOf(maxCellHeight, textView.measuredHeight)
                Log.d("RowViewHolder", "Cell: '$headerName', Value: '${textView.text}', Measured Width: ${textView.measuredWidth}, Measured Height: ${textView.measuredHeight}")
                textViews.add(textView)
                cellContainer.addView(textView)
                effectiveRowWidth += colWidth

                val isLastVisible = visibleIndices.isNotEmpty() && i == visibleIndices.last()
                if (!isLastVisible) {
                    val resizeHandleWidth = context.dpToPx(16)
                    val resizeHandle = View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(resizeHandleWidth, LinearLayout.LayoutParams.MATCH_PARENT) // Изменено на MATCH_PARENT
                        setBackgroundResource(R.drawable.search_background)
                        alpha = 0.5f
                        visibility = if (_isResizingMode) View.VISIBLE else View.GONE
                        isClickable = true
                        isFocusable = true
                        isFocusableInTouchMode = true
                        isSoundEffectsEnabled = false
                    }
                    resizeHandle.measure(View.MeasureSpec.makeMeasureSpec(resizeHandleWidth, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    // Добавляем хэндл только если ячейка была добавлена (иначе — пропуск)
                    if (textViews.isNotEmpty()) {
                        maxCellHeight = maxOf(maxCellHeight, resizeHandle.measuredHeight)
                        cellContainer.addView(resizeHandle)
                        effectiveRowWidth += resizeHandleWidth
                    }

                    if (_isResizingMode) {
                        resizeHandle.setOnTouchListener { v, event ->
                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    _initialX = event.rawX
                                    _initialWidth = (textView.layoutParams as LinearLayout.LayoutParams).width
                                    Log.d("RowResize", "ACTION_DOWN: initialX=$_initialX, initialWidth=$_initialWidth")
                                    (v.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                                    (itemView.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                                    onColumnResize(i, _initialWidth, event.action)
                                    true
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    val dx = event.rawX - _initialX
                                    val newWidth = (_initialWidth + dx).toInt().coerceIn(adapter.minColumnWidthPx(context), adapter.maxColumnWidthPx(context)) // Новые min/max значения
                                    Log.d("RowResize", "ACTION_MOVE: dx=$dx, newWidth=$newWidth")
                                    (v.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                                    (itemView.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                                    (textView.layoutParams as LinearLayout.LayoutParams).width = newWidth
                                    textView.requestLayout()
                                    // Update total row width dynamically during resize
                                    cellContainer.layoutParams.width = adapter.calculateTotalRowWidth(headers, columnWidths, context, i, newWidth)
                                    cellContainer.requestLayout()
                                    onColumnResize(i, newWidth, event.action)
                                    true
                                }
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                    Log.d("RowResize", "ACTION_UP/CANCEL: Final width=${(textView.layoutParams as LinearLayout.LayoutParams).width}")
                                    onColumnResize(i, (textView.layoutParams as LinearLayout.LayoutParams).width, event.action)
                                    _initialX = 0f
                                    _initialWidth = 0
                                    v.performClick()
                                    true
                                }
                                else -> true
                            }
                        }
                    } else {
                        resizeHandle.setOnTouchListener(null)
                    }
                }
            }
            // Добавляем правый конечный хэндл для изменения самой правой колонки
            if (textViews.isNotEmpty()) {
                val lastIndex = visibleIndices.lastOrNull() ?: -1
                val lastTextView = textViews.last()
                val resizeHandleWidth = context.dpToPx(16)
                val trailingHandle = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(resizeHandleWidth, LinearLayout.LayoutParams.MATCH_PARENT)
                    setBackgroundResource(R.drawable.search_background)
                    alpha = 0.5f
                    visibility = if (_isResizingMode) View.VISIBLE else View.GONE
                    isClickable = true
                    isFocusable = true
                    isFocusableInTouchMode = true
                    isSoundEffectsEnabled = false
                }
                if (_isResizingMode) {
                    trailingHandle.setOnTouchListener { v, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                _initialX = event.rawX
                                _initialWidth = (lastTextView.layoutParams as LinearLayout.LayoutParams).width
                                (v.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                                (itemView.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                                onColumnResize(lastIndex, _initialWidth, event.action)
                                true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val dx = event.rawX - _initialX
                                val newWidth = (_initialWidth + dx).toInt().coerceIn(adapter.minColumnWidthPx(context), adapter.maxColumnWidthPx(context))
                                (v.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                                (itemView.parent as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
                                (lastTextView.layoutParams as LinearLayout.LayoutParams).width = newWidth
                                lastTextView.requestLayout()
                                cellContainer.layoutParams.width = adapter.calculateTotalRowWidth(headers, columnWidths, context, lastIndex, newWidth)
                                cellContainer.requestLayout()
                                onColumnResize(lastIndex, newWidth, event.action)
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                onColumnResize(lastIndex, (lastTextView.layoutParams as LinearLayout.LayoutParams).width, event.action)
                                _initialX = 0f
                                _initialWidth = 0
                                v.performClick()
                                true
                            }
                            else -> true
                        }
                    }
                } else {
                    trailingHandle.setOnTouchListener(null)
                }
                trailingHandle.measure(View.MeasureSpec.makeMeasureSpec(resizeHandleWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                maxCellHeight = maxOf(maxCellHeight, trailingHandle.measuredHeight)
                cellContainer.addView(trailingHandle)
                effectiveRowWidth += resizeHandleWidth
            }

            val finalMaxCellHeight = maxOf(maxCellHeight, context.dpToPx(50))
            cellContainer.layoutParams.height = finalMaxCellHeight
            // ВАЖНО: не меняем ширину строки в режиме поиска, чтобы header и строки
            // всегда имели одинаковый totalRowWidth и не возникал правый зазор
            textViews.forEach { textView ->
                textView.layoutParams.height = finalMaxCellHeight
                Log.d("RowViewHolder", "Cell text: ${textView.text}, Final Height: ${textView.layoutParams.height}")
            }
            cellContainer.requestLayout()

            Log.d("RowViewHolder", "Total Row Width: $totalRowWidth, Final Max Cell Height: $finalMaxCellHeight")

            // Убираем общий клик по строке, чтобы срабатывал только клик по колонке "Арматура"
        }

        // Helper function to calculate total row width during resize
        private fun calculateTotalRowWidth(headers: List<String>, columnWidths: Map<String, Int>, columnIndex: Int, newWidth: Int, context: Context): Int {
            var totalWidth = 0
            var visibleCount = 0
            headers.forEachIndexed { i, headerName ->
                if (adapter.isHidden(headerName)) return@forEachIndexed
                visibleCount++
                if (i == columnIndex) {
                    totalWidth += newWidth
                } else {
                    val w = columnWidths[headerName] ?: context.dpToPx(100)
                    totalWidth += w.coerceAtMost(adapter.maxColumnWidthPx(context)) // Clamp to 50mm
                }
            }
            // В режиме редактирования добавляем пространство для handles
            if (_isResizingMode) {
                return totalWidth + visibleCount * context.dpToPx(16)
            } else {
                return totalWidth  // Без дополнительных зазоров в обычном режиме
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) TYPE_HEADER else TYPE_ROW
    }

    override fun setHasStableIds(hasStableIds: Boolean) {
        // Отключаем стабильные ID, чтобы RecyclerView не сохранял/восстанавливал позицию
        // при обновлениях через DiffUtil и не мешал принудительному скроллу к началу
        super.setHasStableIds(false)
    }

    override fun getItemId(position: Int): Long {
        return if (position == 0) Long.MIN_VALUE else data[position - 1].hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.item_signal_row, parent, false)
        return if (viewType == TYPE_HEADER) HeaderViewHolder(view, _isResizingMode, onColumnResize, this) else RowViewHolder(view, _isResizingMode, onColumnResize, this)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val totalRowWidth = calculateTotalRowWidth(headers, columnWidths, holder.itemView.context)
        if (getItemViewType(position) == TYPE_HEADER) {
            (holder as HeaderViewHolder).bind(headers, columnWidths, totalRowWidth, holder.itemView.context, _isResizingMode)
        } else {
            val row = data[position - 1] // смещение, потому что 0 — это заголовок
            (holder as RowViewHolder).bind(row, headers, columnWidths, totalRowWidth, holder.itemView.context, _isResizingMode)
        }
    }

    override fun getItemCount(): Int = data.size + 1 // +1 для заголовка

    fun updateData(newData: List<RowDataDynamic>, newHeaders: List<String>, newColumnWidths: Map<String, Int>, isResizingMode: Boolean, updateOriginal: Boolean = false) { // Add isResizingMode
        Log.d("SignalsAdapter", "Updating data. New data size: ${newData.size}, Headers: ${newHeaders}, Column Widths: ${newColumnWidths}")
        data = newData
        headers = newHeaders.toMutableList()
        columnWidths = newColumnWidths
        armatureColIndex = headers.indexOfFirst { h ->
            h.equals("Арматура", ignoreCase = true)
        }
        pdfSchemeColIndex = headers.indexOfFirst { h ->
            h.equals("PDF_Схема_и_ID_арматуры", ignoreCase = true) || h.equals("PDF_Схема", ignoreCase = true)
        }
        
        // ИСПРАВЛЕНИЕ: Всегда обновляем _originalData при загрузке новых данных
        // Это гарантирует, что поиск будет работать с актуальными данными
        if (updateOriginal || _originalData.isEmpty() || newData.isNotEmpty()) {
            _originalData = newData
            Log.d("SignalsAdapter", "Updated _originalData: ${_originalData.size} items")
            
            // ДИАГНОСТИКА: Проверяем качество данных
            if (_originalData.isNotEmpty()) {
                val firstItem = _originalData.first()
                val firstValues = firstItem.getAllProperties()
                Log.d("SignalsAdapter", "First item sample: ${firstValues.take(3)}")
                
                // Проверяем, есть ли null значения
                val nullCount = firstValues.count { it == null }
                Log.d("SignalsAdapter", "Null values in first item: $nullCount out of ${firstValues.size}")
                
                // ДОПОЛНИТЕЛЬНАЯ ПРОВЕРКА: Убеждаемся, что данные содержат полезную информацию
                val validDataCount = _originalData.count { row ->
                    val values = row.getAllProperties()
                    values.any { it != null && it.toString().trim().isNotEmpty() }
                }
                Log.d("SignalsAdapter", "Valid data rows: $validDataCount out of ${_originalData.size}")
                
                // ДОПОЛНИТЕЛЬНАЯ ПРОВЕРКА: Проверяем наличие арматуры в данных
                if (armatureColIndex >= 0) {
                    val armatureValues = _originalData.mapNotNull { row ->
                        val values = row.getAllProperties()
                        if (armatureColIndex < values.size) values[armatureColIndex] else null
                    }.filter { it.toString().trim().isNotEmpty() }
                    Log.d("SignalsAdapter", "Armature values found: ${armatureValues.size}, samples: ${armatureValues.take(3)}")
                }
            }
            
            // ИСПРАВЛЕНИЕ: Сбрасываем текущий поиск при обновлении данных
            currentSearchQuery = ""
        }
        _isResizingMode = isResizingMode // Update resizing mode
        // TODO: Заменить на DiffUtil для лучшей производительности
        // Пока используем notifyDataSetChanged() для совместимости
        notifyDataSetChanged()
    }

    // Drag & drop reordering (edit mode)
    fun moveHeader(from: Int, to: Int) {
        if (from == to) return
        if (from !in headers.indices || to !in headers.indices) return
        
        // Перемещаем только заголовки
        val h = headers.removeAt(from)
        headers.add(to, h)
        
        // ИСПРАВЛЕНИЕ: Для каждой строки создаем новый Map с новым порядком заголовков,
        // но СОХРАНЯЯ исходное соответствие "заголовок -> значение"
        data = data.map { row ->
            val oldMap = row.asMap()  // Получаем исходный Map с парами "заголовок -> значение"
            val newMap = LinkedHashMap<String, String>()
            headers.forEach { headerName ->
                // Берем значение по ключу (названию заголовка), а не по индексу!
                newMap[headerName] = oldMap[headerName] ?: ""
            }
            RowDataDynamic(newMap)
        }
        
        // ИСПРАВЛЕНИЕ: Также обновляем _originalData, чтобы сохранить изменения
        _originalData = _originalData.map { row ->
            val oldMap = row.asMap()
            val newMap = LinkedHashMap<String, String>()
            headers.forEach { headerName ->
                newMap[headerName] = oldMap[headerName] ?: ""
            }
            RowDataDynamic(newMap)
        }
        
        // КРИТИЧНО: Обновляем индексы колонок после перестановки!
        armatureColIndex = headers.indexOfFirst { h ->
            h.equals("Арматура", ignoreCase = true)
        }
        pdfSchemeColIndex = headers.indexOfFirst { h ->
            h.equals("PDF_Схема_и_ID_арматуры", ignoreCase = true) || h.equals("PDF_Схема", ignoreCase = true)
        }
        
        Log.d("SignalsAdapter", "=== COLUMN REORDER DEBUG ===")
        Log.d("SignalsAdapter", "Moved column from=$from to=$to")
        Log.d("SignalsAdapter", "New headers order: $headers")
        Log.d("SignalsAdapter", "Updated armatureColIndex=$armatureColIndex, pdfSchemeColIndex=$pdfSchemeColIndex")
        Log.d("SignalsAdapter", "First row sample after reorder: ${data.firstOrNull()?.asMap()}")
        Log.d("SignalsAdapter", "=== END DEBUG ===")
        
        notifyDataSetChanged()
        try { onColumnReorder?.invoke(headers.toList()) } catch (_: Throwable) {}
    }

    fun applyColumnOrder(order: List<String>) {
        if (order.isEmpty()) return

        val current = headers.toList()

        // Видимые колонки (без скрытых, например PDF_Схема_и_ID_арматуры)
        val visibleCurrent = current.filterNot { isHidden(it) }

        // Порядок, пришедший «снаружи», трактуем как порядок ТОЛЬКО видимых колонок
        val allExist = order.all { desired -> visibleCurrent.any { it == desired } }
        if (!allExist || order.size != visibleCurrent.size) {
            // Несовпадение наборов видимых колонок — безопасно пропускаем
            return
        }

        // Перестраиваем headers: скрытые колонки остаются на своих местах,
        // для видимых берём порядок из списка order
        val visibleIterator = order.iterator()
        val newHeaders = mutableListOf<String>()
        current.forEach { headerName ->
            if (isHidden(headerName)) {
                newHeaders.add(headerName)
            } else {
                newHeaders.add(visibleIterator.next())
            }
        }

        // ИСПРАВЛЕНИЕ: Пересобираем заголовки и данные, сохраняя соответствие "заголовок -> значение"
        headers = newHeaders
        data = data.map { row ->
            val oldMap = row.asMap()  // Получаем исходный Map с парами "заголовок -> значение"
            val newMap = LinkedHashMap<String, String>()
            headers.forEach { headerName ->
                // Берем значение по ключу (названию заголовка), а не по индексу!
                newMap[headerName] = oldMap[headerName] ?: ""
            }
            RowDataDynamic(newMap)
        }
        
        // ИСПРАВЛЕНИЕ: Также обновляем _originalData, чтобы сохранить изменения
        _originalData = _originalData.map { row ->
            val oldMap = row.asMap()
            val newMap = LinkedHashMap<String, String>()
            headers.forEach { headerName ->
                newMap[headerName] = oldMap[headerName] ?: ""
            }
            RowDataDynamic(newMap)
        }
        
        // Обновляем индексы колонок после изменения порядка
        armatureColIndex = headers.indexOfFirst { h ->
            h.equals("Арматура", ignoreCase = true)
        }
        pdfSchemeColIndex = headers.indexOfFirst { h ->
            h.equals("PDF_Схема_и_ID_арматуры", ignoreCase = true) || h.equals("PDF_Схема", ignoreCase = true)
        }
        
        Log.d("SignalsAdapter", "Applied column order: $order, armatureColIndex=$armatureColIndex, pdfSchemeColIndex=$pdfSchemeColIndex")
        
        notifyDataSetChanged()
    }

    fun appendData(more: List<RowDataDynamic>) {
        if (more.isEmpty()) return
        // НОРМАЛИЗАЦИЯ: приводим новые строки к текущему порядку headers,
        // чтобы нижние страницы использовали тот же порядок колонок, что и верх таблицы.
        val normalizedMore = normalizeRowsToCurrentHeaders(more)

        // Во время показа результатов поиска (включая пустой результат) не меняем текущие отображаемые данные,
        // чтобы таблица не «возвращалась» к полной при догрузке страниц. Обновляем только _originalData.
        if (showingSearchResults) {
            _originalData = if (_originalData.isNotEmpty()) _originalData + normalizedMore else normalizedMore
            return
        }
        val start = data.size
        data = data + normalizedMore
        notifyItemRangeInserted(start + 1, normalizedMore.size) // +1 из-за заголовка
        if (_originalData.isNotEmpty()) {
            _originalData = _originalData + normalizedMore
        }
    }

    /**
     * Нормализует строки к текущему списку headers, чтобы порядок значений всегда
     * соответствовал порядку колонок (учитывая скрытые колонки, такие как PDF).
     */
    private fun normalizeRowsToCurrentHeaders(rows: List<RowDataDynamic>): List<RowDataDynamic> {
        if (rows.isEmpty() || headers.isEmpty()) return rows
        return rows.map { row ->
            val oldMap = row.asMap()
            val newMap = LinkedHashMap<String, String>()
            headers.forEach { headerName ->
                newMap[headerName] = oldMap[headerName] ?: ""
            }
            RowDataDynamic(newMap)
        }
    }

    fun updateColumnWidths(columnIndex: Int, newWidth: Int, shouldNotify: Boolean = true) {
        val headerName = headers[columnIndex]
        val updatedWidths = columnWidths.toMutableMap()
        updatedWidths[headerName] = newWidth
        columnWidths = updatedWidths
        if (shouldNotify) {
        notifyDataSetChanged()
        }
    }

    fun setResizingMode(isResizingMode: Boolean) {
        _isResizingMode = isResizingMode
        notifyDataSetChanged()
    }

    fun getOriginalData(): List<RowDataDynamic> = _originalData
    
    /**
     * Установить список кликабельных арматур (с координатами в JSON)
     */
    fun setClickableArmatures(armatures: Set<String>) {
        clickableArmatures = armatures
        notifyDataSetChanged()
    }

    private fun calculateTotalRowWidth(headers: List<String>, columnWidths: Map<String, Int>, context: Context, updatedColumnIndex: Int = -1, updatedColumnWidth: Int = -1): Int {
        var totalWidth = 0
        var visibleCount = 0
        headers.forEachIndexed { index, headerName ->
            if (isHidden(headerName)) return@forEachIndexed
            visibleCount++
            if (index == updatedColumnIndex) {
                totalWidth += updatedColumnWidth
            } else {
                val w = columnWidths[headerName] ?: context.dpToPx(100)
                totalWidth += w.coerceAtMost(maxColumnWidthPx(context)) // Clamp to 50mm
            }
        }
        // Учитываем хэндлы ТОЛЬКО в режиме редактирования
        // В обычном режиме убираем все дополнительные отступы
        if (_isResizingMode) {
            val handleWidth = context.dpToPx(16)
            return totalWidth + visibleCount * handleWidth  // Полное пространство для handles
        } else {
            return totalWidth  // Без дополнительных зазоров в обычном режиме
        }
    }

    // === Поиск по столбцам ===
    fun activateColumnSearch(headerName: String) {
        if (selectedColumnHeaderName == headerName) return
        selectedColumnHeaderName = headerName
        Log.d("SignalsAdapter", "Column search activated for: $headerName")
        // Перерисовать заголовок для подсветки и применить фильтр к текущему запросу
        notifyItemChanged(0)
        // ИСПРАВЛЕНИЕ: Удален вызов старого метода filter()
        // Поиск теперь управляется из ArmatureFragment через SearchManager
        if (currentSearchQuery.isNotEmpty()) {
            Log.d("SignalsAdapter", "Column search changed, search should be re-triggered from Fragment")
        }
    }

    fun deactivateColumnSearch() {
        val prev = selectedColumnHeaderName
        selectedColumnHeaderName = null
        Log.d("SignalsAdapter", "Column search deactivated (was: $prev)")
        notifyItemChanged(0)
        // ИСПРАВЛЕНИЕ: Удален вызов старого метода filter()
        // Поиск теперь управляется из ArmatureFragment через SearchManager
        if (currentSearchQuery.isNotEmpty()) {
            Log.d("SignalsAdapter", "Column search changed, search should be re-triggered from Fragment")
        }
    }

    fun isColumnSelected(headerName: String): Boolean {
        return selectedColumnHeaderName?.equals(headerName, ignoreCase = true) == true
    }

    fun hasSelectedColumn(): Boolean {
        return !selectedColumnHeaderName.isNullOrBlank()
    }
    
    fun getSelectedColumnName(): String? {
        return selectedColumnHeaderName
    }
    
    /**
     * ПРОФЕССИОНАЛЬНОЕ РЕШЕНИЕ: Устанавливает результаты поиска по всей таблице
     * Заменяет текущие данные результатами поиска
     */
    fun setSearchResults(searchResults: List<RowDataDynamic>, searchQuery: String) {
        Log.d("SignalsAdapter", "Setting search results: ${searchResults.size} items for query: '$searchQuery'")
        currentSearchQuery = searchQuery
        data = searchResults
        notifyDataSetChanged()
        
        // Показываем информацию о результатах поиска
        if (searchResults.isEmpty()) {
            Log.w("SignalsAdapter", "No results found for query: '$searchQuery'")
        } else {
            Log.d("SignalsAdapter", "Search results set successfully: ${searchResults.size} items")
            // Показываем первые несколько результатов для диагностики
            searchResults.take(3).forEachIndexed { index, row ->
                val values = row.getAllProperties()
                Log.d("SignalsAdapter", "  Result $index: ${values.take(3).joinToString(", ")}")
            }
        }
    }
    
    /**
     * Возвращает к обычному отображению данных (сбрасывает результаты поиска)
     */
    fun clearSearchResults() {
        Log.d("SignalsAdapter", "Clearing search results, returning to original data")
        currentSearchQuery = ""
        data = _originalData
        notifyDataSetChanged()
    }
    
    /**
     * ИСПРАВЛЕНИЕ: Принудительно сбрасывает выделение столбца для поиска
     * Используется для диагностики проблем с поиском
     */
    fun forceResetColumnSelection() {
        val wasSelected = selectedColumnHeaderName
        selectedColumnHeaderName = null
        Log.d("SignalsAdapter", "Force reset column selection (was: $wasSelected)")
        notifyItemChanged(0)
        
        // ИСПРАВЛЕНИЕ: Удален вызов старого метода filter()
        // Повторяем поиск, если есть активный запрос
        if (currentSearchQuery.isNotEmpty()) {
            Log.d("SignalsAdapter", "Column selection reset, search should be re-triggered from Fragment: '$currentSearchQuery'")
        }
    }
    
    /**
     * НОВОЕ: Обновление результатов поиска с использованием DiffUtil
     */
    fun updateSearchResults(searchResults: List<SearchResult>, searchQuery: String) {
        Log.d("SignalsAdapter", "Updating search results with DiffUtil: ${searchResults.size} items for query: '$searchQuery'")
        
        val oldResults = currentSearchResults
        val oldData = data.toList() // Сохраняем копию старых данных
        
        // Предотвращаем восстановление позиции списка во время применения DiffUtil
        try { this.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT } catch (_: Throwable) {}

        currentSearchResults = searchResults
        currentSearchQuery = searchQuery
        showingSearchResults = true
        
        // Извлекаем данные из SearchResult и нормализуем их под текущий порядок headers
        val rawData = searchResults.map { it.data }
        val newData = normalizeRowsToCurrentHeaders(rawData)
        
        // ИСПРАВЛЕНИЕ: Используем DiffUtil безопасно
        adapterScope.launch {
            try {
                val diffResult = if (oldResults.isNotEmpty()) {
                    DiffUtilHelper.calculateSearchDiffAsync(oldResults, searchResults)
                } else {
                    DiffUtilHelper.calculateDataDiffAsync(oldData, newData)
                }
                
                // КРИТИЧНО: Обновляем данные НА ГЛАВНОМ ПОТОКЕ после вычисления DiffResult
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    data = newData
                    diffResult.dispatchUpdatesTo(this@SignalsAdapter)
                    Log.d("SignalsAdapter", "Search results updated using DiffUtil")
                    // Форсируем перебиндинг содержимого для корректной подсветки (query менялся)
                    try {
                        if (data.isNotEmpty()) notifyItemRangeChanged(1, data.size)
                    } catch (_: Throwable) {}
                    try { onAfterSearchResultsApplied?.invoke() } catch (_: Throwable) {}
                    // Возвращаем разрешение на восстановление состояния после скролла к началу
                    restoreAdapterStatePolicy()
                }
                
            } catch (e: Exception) {
                Log.e("SignalsAdapter", "Error updating search results with DiffUtil", e)
                // Fallback к простому обновлению
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    data = newData
                    notifyDataSetChanged()
                    Log.d("SignalsAdapter", "Fallback: Search results updated with notifyDataSetChanged")
                    try { onAfterSearchResultsApplied?.invoke() } catch (_: Throwable) {}
                    restoreAdapterStatePolicy()
                }
            }
        }
    }
    
    /**
     * НОВОЕ: Очистка результатов поиска с использованием DiffUtil
     */
    fun clearSearchResultsOptimized() {
        Log.d("SignalsAdapter", "Clearing search results with DiffUtil, returning to original data")
        
        if (!showingSearchResults) {
            Log.d("SignalsAdapter", "Not showing search results, nothing to clear")
            return
        }
        
        val oldData = data.toList() // Сохраняем копию старых данных
        // Предотвращаем восстановление позиции списка во время применения DiffUtil
        try { this.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT } catch (_: Throwable) {}
        currentSearchQuery = ""
        currentSearchResults = emptyList()
        showingSearchResults = false
        
        // ИСПРАВЛЕНИЕ: Используем DiffUtil безопасно
        adapterScope.launch {
            try {
                val diffResult = DiffUtilHelper.calculateDataDiffAsync(oldData, _originalData)
                
                // КРИТИЧНО: Обновляем данные НА ГЛАВНОМ ПОТОКЕ после вычисления DiffResult
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    data = _originalData
                    diffResult.dispatchUpdatesTo(this@SignalsAdapter)
                    Log.d("SignalsAdapter", "Cleared search results using DiffUtil")
                    // ДОБАВЛЕНО: Принудительный ребайнд видимых элементов для снятия подсветки
                    try {
                        if (data.isNotEmpty()) notifyItemRangeChanged(1, data.size)
                    } catch (_: Throwable) {}
                    restoreAdapterStatePolicy()
                }
                
            } catch (e: Exception) {
                Log.e("SignalsAdapter", "Error clearing search results with DiffUtil", e)
                // Fallback к простому обновлению
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    data = _originalData
                    notifyDataSetChanged()
                    Log.d("SignalsAdapter", "Fallback: Cleared search results with notifyDataSetChanged")
                    restoreAdapterStatePolicy()
                }
            }
        }
    }
    
    /**
     * НОВОЕ: Проверка, отображаются ли результаты поиска
     */
    fun isShowingSearchResults(): Boolean = showingSearchResults
    
    /**
     * НОВОЕ: Получение текущих результатов поиска
     */
    fun getCurrentSearchResults(): List<SearchResult> = currentSearchResults
    
    /**
     * НОВОЕ: Подсветка найденных терминов в тексте
     */
    fun highlightSearchTerms(textView: TextView, text: String, searchQuery: String) {
        if (searchQuery.isBlank()) {
            textView.text = text
            return
        }
        
        try {
            val searchVariants = SearchNormalizer.createSearchVariants(searchQuery)
            val spannable = android.text.SpannableString(text)
            
            // Градации подсветки по приоритету совпадения
            // 1) Точное совпадение (100%): светло-зелёный
            // 2) Начало слова: светло-оранжевый
            // 3) Содержит: светло-красный
            val exactColor = 0x4428A745 // #28A745 с прозрачностью
            val prefixColor = 0x44FF9800 // #FF9800 с прозрачностью
            val containsColor = 0x44F44336 // #F44336 с прозрачностью

            fun applySpan(rangeStart: Int, rangeEnd: Int, bgColor: Int) {
                spannable.setSpan(
                    android.text.style.BackgroundColorSpan(bgColor),
                    rangeStart,
                    rangeEnd,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    rangeStart,
                    rangeEnd,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // Сначала точные совпадения для всех вариантов
            searchVariants.forEach { variant ->
                if (variant.isEmpty()) return@forEach
                var start = 0
                while (start < text.length) {
                    val idx = text.indexOf(variant, start, ignoreCase = true)
                    if (idx < 0) break
                    val end = idx + variant.length
                    // Точное, если границы строки совпадают либо символы вокруг не буквенно-цифровые
                    applySpan(idx, end, exactColor)
                    start = idx + 1
                }
            }

            // Затем префиксные совпадения, не перезаписывая уже поставленные
            searchVariants.forEach { variant ->
                if (variant.isEmpty()) return@forEach
                var start = 0
                while (start < text.length) {
                    val idx = text.indexOf(variant, start, ignoreCase = true)
                    if (idx < 0) break
                    val end = idx + variant.length
                    // Префикс слова: совпадение на границе слова
                    val isWordStart = idx == 0 || !text[idx - 1].isLetterOrDigit()
                    if (isWordStart) {
                        // Не дублируем поверх точной подсветки — BackgroundColorSpan уже проставлен
                        applySpan(idx, end, prefixColor)
                    }
                    start = idx + 1
                }
            }

            // Наконец содержательные совпадения
            searchVariants.forEach { variant ->
                if (variant.isEmpty()) return@forEach
                var start = 0
                while (start < text.length) {
                    val idx = text.indexOf(variant, start, ignoreCase = true)
                    if (idx < 0) break
                    val end = idx + variant.length
                    applySpan(idx, end, containsColor)
                    start = idx + 1
                }
            }

            textView.text = spannable
        } catch (e: Exception) {
            Log.w("SignalsAdapter", "Error highlighting search terms", e)
            textView.text = text
        }
    }
    
    /**
     * НОВОЕ: Обновление отфильтрованных данных без изменения исходного порядка
     * и без прокрутки (скролл контролируется фрагментом, мы только применяем DiffUtil)
     */
    fun updateFilteredDataPreserveOrder(filtered: List<RowDataDynamic>, searchQuery: String) {
        Log.d("SignalsAdapter", "Updating filtered data (preserve order): ${filtered.size} items for query: '$searchQuery'")
        val oldData = data.toList()
        currentSearchQuery = searchQuery
        showingSearchResults = true

        // Предотвращаем восстановление позиции списка во время применения DiffUtil
        try { this.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT } catch (_: Throwable) {}

        adapterScope.launch {
            try {
                val diffResult = DiffUtilHelper.calculateDataDiffAsync(oldData, filtered)
                withContext(Dispatchers.Main) {
                    data = filtered
                    diffResult.dispatchUpdatesTo(this@SignalsAdapter)
                    Log.d("SignalsAdapter", "Filtered data updated with DiffUtil (preserve order)")
                    try { onAfterSearchResultsApplied?.invoke() } catch (_: Throwable) {}
                    restoreAdapterStatePolicy()
                }
            } catch (e: Exception) {
                Log.e("SignalsAdapter", "Error updating filtered data", e)
                withContext(Dispatchers.Main) {
                    data = filtered
                    notifyDataSetChanged()
                    try { onAfterSearchResultsApplied?.invoke() } catch (_: Throwable) {}
                    restoreAdapterStatePolicy()
                }
            }
        }
    }
    
    /**
     * Очистка ViewHolder при переработке для предотвращения утечек памяти
     */
    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is HeaderViewHolder) {
            holder.unbind()
        }
    }
    
    /**
     * Очистка ресурсов для предотвращения утечек памяти
     */
    fun cleanup() {
        adapterScope.cancel()
        mainHandler.removeCallbacksAndMessages(null) // Очищаем все отложенные задачи
        SearchNormalizer.clearCaches()
    }
}