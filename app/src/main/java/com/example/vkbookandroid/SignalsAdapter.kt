package com.example.vkbookandroid

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.example.pult.RowDataDynamic
import org.example.pult.android.dpToPx
import android.view.MotionEvent

class SignalsAdapter(
    private var data: List<RowDataDynamic>,
    private var _isResizingMode: Boolean,
    private val onColumnResize: (columnIndex: Int, newWidth: Int, action: Int) -> Unit,
    private val onRowClick: ((RowDataDynamic) -> Unit)? = null,
    private val onArmatureCellClick: ((RowDataDynamic) -> Unit)? = null,
    private val hidePdfSchemeColumn: Boolean = true
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var headers: List<String> = emptyList() // Changed from private to public
    private var columnWidths: Map<String, Int> = emptyMap()
    private var _originalData: List<RowDataDynamic> = emptyList()
    var currentSearchQuery: String = ""
    private var armatureColIndex: Int = -1
    private var pdfSchemeColIndex: Int = -1
    private val hiddenHeaderNames: Set<String> = setOf("PDF_Схема_и_ID_арматуры")

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
                    setBackgroundResource(R.drawable.cell_border) // Cell border
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
            return totalWidth + (visibleCount - 1).coerceAtLeast(0) * context.dpToPx(8) // Add width of all resize handles between visible columns
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
            cellContainer.layoutParams.width = totalRowWidth

            var maxCellHeight = 0
            val textViews = mutableListOf<TextView>()
            val visibleIndices = headers.mapIndexedNotNull { idx, h -> if (!adapter.isHidden(h)) idx else null }

            Log.d("RowViewHolder", "Binding row: ${rowData.getAllProperties()}")
            headers.forEachIndexed { i, headerName ->
                if (adapter.isHidden(headerName)) return@forEachIndexed
                val colWidthRaw = columnWidths[headerName] ?: context.dpToPx(100) // Default width in dp
                val colWidth = colWidthRaw.coerceIn(adapter.minColumnWidthPx(context), adapter.maxColumnWidthPx(context))
                val textView = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(colWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
                    text = if (i < values.size) values[i] ?: "" else ""
                    val cellText = text
                    if (adapter.currentSearchQuery.isNotEmpty() && (cellText?.contains(adapter.currentSearchQuery, ignoreCase = true) == true)) {
                        setBackgroundResource(R.drawable.cell_highlight)
                    } else {
                    setBackgroundResource(R.drawable.cell_border)
                    }
                    gravity = Gravity.START or Gravity.TOP
                    setPadding(context.dpToPx(4), context.dpToPx(4), context.dpToPx(4), context.dpToPx(4))
                    textSize = 12f
                    isSoundEffectsEnabled = false
                }
                // Клик и подсветка только для "Арматура" если в строке указан PDF в колонке PDF_Схема_и_ID_арматуры
                if (!isResizingMode && adapter.armatureColIndex >= 0 && i == adapter.armatureColIndex) {
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
                    }
                }
                textView.measure(View.MeasureSpec.makeMeasureSpec(colWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                maxCellHeight = maxOf(maxCellHeight, textView.measuredHeight)
                Log.d("RowViewHolder", "Cell: '$headerName', Value: '${textView.text}', Measured Width: ${textView.measuredWidth}, Measured Height: ${textView.measuredHeight}")
                textViews.add(textView)
                cellContainer.addView(textView)

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
                    maxCellHeight = maxOf(maxCellHeight, resizeHandle.measuredHeight)
                    cellContainer.addView(resizeHandle)

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
            }

            val finalMaxCellHeight = maxOf(maxCellHeight, context.dpToPx(50))
            cellContainer.layoutParams.height = finalMaxCellHeight
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
            return totalWidth + (visibleCount) * context.dpToPx(16) // Width of all resize handles including trailing for visible columns
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) TYPE_HEADER else TYPE_ROW
    }

    override fun setHasStableIds(hasStableIds: Boolean) {
        super.setHasStableIds(true)
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
        headers = newHeaders
        columnWidths = newColumnWidths
        armatureColIndex = headers.indexOfFirst { h ->
            h.equals("Арматура", ignoreCase = true) || h.contains("арматур", ignoreCase = true)
        }
        pdfSchemeColIndex = headers.indexOfFirst { h ->
            h.equals("PDF_Схема_и_ID_арматуры", ignoreCase = true) || h.equals("PDF_Схема", ignoreCase = true)
        }
        if (updateOriginal || _originalData.isEmpty()) {
            _originalData = newData // Initialize/refresh originalData only when baseline load
        }
        _isResizingMode = isResizingMode // Update resizing mode
        notifyDataSetChanged()
    }

    fun appendData(more: List<RowDataDynamic>) {
        if (more.isEmpty()) return
        val start = data.size
        data = data + more
        notifyItemRangeInserted(start + 1, more.size) // +1 из-за заголовка
        // оригинальные данные не переписываем, т.к. это продолжение базовой ленты
        if (_originalData.isNotEmpty()) {
            _originalData = _originalData + more
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

    fun filter(searchText: String) {
        currentSearchQuery = searchText
        data = if (searchText.isEmpty()) _originalData else _originalData.filter { row ->
                row.getAllProperties().any { value ->
                    value?.contains(searchText, ignoreCase = true) == true
            }
        }
        notifyDataSetChanged()
    }

    fun getOriginalData(): List<RowDataDynamic> = _originalData

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
        // Учитываем хэндлы между колонками и правый конечный хэндл только для видимых колонок
        val handleWidth = context.dpToPx(16)
        return totalWidth + visibleCount * handleWidth
    }
}