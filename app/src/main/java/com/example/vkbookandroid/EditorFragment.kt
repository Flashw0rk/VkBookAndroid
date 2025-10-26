package com.example.vkbookandroid

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.*
import androidx.appcompat.widget.SearchView
import com.google.android.material.button.MaterialButton
import android.os.Build
import android.os.LocaleList
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.gson.GsonBuilder
import android.content.res.ColorStateList
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.vkbookandroid.network.NetworkModule
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.ss.usermodel.DataFormatter
import java.io.FileInputStream
import java.util.Locale

/**
 * Базовый каркас вкладки "Редактор" (минимальный функционал, не трогает другие вкладки).
 * - Открыть PDF (только интент выбора, без рендера на этом этапе)
 * - Открыть JSON и показать превью текста
 * - Сохранять изменённый JSON в отдельную директорию files/editor_out
 */
class EditorFragment : Fragment() {
    private lateinit var storage: com.example.vkbookandroid.editor.IEditorStorageService
    private val uploader: com.example.vkbookandroid.editor.IEditorUploadService = com.example.vkbookandroid.editor.EditorUploadService()
    private val excelWriter: com.example.vkbookandroid.editor.IArmaturesExcelWriter = com.example.vkbookandroid.editor.ArmaturesExcelWriter()

    private lateinit var tvStatus: TextView
    private lateinit var btnOpen: Button
    private lateinit var btnSave: Button
    private lateinit var btnSaveChanges: Button // legacy field, no longer used, but keep if referenced
    private lateinit var btnUpload: Button
    private lateinit var pdfZoomView: ZoomableImageView
    private lateinit var editorOverlay: EditorMarkerOverlayView
    private lateinit var jsonEditor: EditText
    private lateinit var jsonSearchView: SearchView
    private lateinit var btnJsonPrev: Button
    private lateinit var btnJsonNext: Button
    private lateinit var tvJsonSearchPos: TextView
    private var jsonSearchMatches: List<Int> = emptyList()
    private var jsonCurrentMatchIndex: Int = 0
    private lateinit var btnPrevPage: Button
    private lateinit var btnNextPage: Button
    private lateinit var tvPageInfo: TextView
    private lateinit var tvScale: TextView
    private lateinit var btnUseOriginals: Button
    private lateinit var btnUseEdited: Button
    private lateinit var toggleEditMode: ToggleButton
    private lateinit var toggleShowAll: ToggleButton
    private lateinit var btnUndo: Button
    private lateinit var btnRedo: Button
    private lateinit var tvLog: TextView
    private lateinit var jsonScroll: View
    private lateinit var logScroll: View
    private lateinit var btnZoomIn: Button
    private lateinit var btnZoomOut: Button

    // Встроенный Excel-редактор (виды и состояние)
    private lateinit var excelContainer: View
    private lateinit var excelRecyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var excelSearchView: androidx.appcompat.widget.SearchView
    private lateinit var excelToggleResizeButton: Button
    private lateinit var excelSaveButton: Button
    private var excelAdapter: SignalsAdapter? = null
    private var excelIsResizingMode: Boolean = false
    private var excelColumnWidths: MutableMap<String, Int> = mutableMapOf()
    private var excelHeaders: List<String> = emptyList()
    private var excelData: MutableList<org.example.pult.RowDataDynamic> = mutableListOf()

    private var lastOpenedJson: String = ""
    private var lastOpenedJsonUri: Uri? = null
    private var pdfUri: Uri? = null
    private var currentPdfName: String? = null
    private var pdfFd: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var pdfPageIndex = 0
    private var pdfToBitmapScale: Float = 1f
    private var useEditedSource: Boolean = false

    private val undoStack = ArrayDeque<List<EditorMarkerOverlayView.EditorMarkerItem>>()
    private val redoStack = ArrayDeque<List<EditorMarkerOverlayView.EditorMarkerItem>>()
    private val deletedIds = mutableSetOf<String>()
    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val prefs by lazy { requireContext().getSharedPreferences("vkbook_prefs", Context.MODE_PRIVATE) }

    private val openPdfLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            tvStatus.text = "Выбран PDF: ${uri}"
            requireContext().contentResolver.takePersistableUriPermission(uri, IntentFlagsRW)
            openPdf(uri)
        }
    }

    private val openJsonLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                requireContext().contentResolver.takePersistableUriPermission(uri, IntentFlagsRW)
                val json = requireContext().contentResolver.openInputStream(uri)?.use { it.reader().readText() } ?: ""
                lastOpenedJson = json
                lastOpenedJsonUri = uri
                tvStatus.text = "JSON загружен (${json.length} симв.)\n${json.take(512)}"
                jsonEditor.setText(json)
                jsonScroll.visibility = View.VISIBLE
                jsonEditor.visibility = View.VISIBLE
                // Скрываем логи во время редактирования JSON
                logScroll.visibility = View.GONE
                pdfZoomView.visibility = View.GONE
                editorOverlay.visibility = View.GONE
                btnPrevPage.visibility = View.GONE
                btnNextPage.visibility = View.GONE
                tvPageInfo.visibility = View.GONE
                tvScale.visibility = View.GONE
            } catch (e: Exception) {
                Log.e("EditorFragment", "Error reading JSON", e)
                tvStatus.text = "Ошибка чтения JSON: ${e.message}"
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = inflater.inflate(R.layout.fragment_editor, container, false)
        storage = com.example.vkbookandroid.editor.EditorStorageService(requireContext())
        tvStatus = v.findViewById(R.id.tvEditorStatus)
        btnOpen = v.findViewById(R.id.btnOpen)
        btnSave = v.findViewById(R.id.btnSave)
        // keep by id to avoid NPE if referenced somewhere
        btnSaveChanges = Button(requireContext())
        btnUpload = v.findViewById(R.id.btnUpload)
        pdfZoomView = v.findViewById(R.id.pdfZoomView)
        editorOverlay = v.findViewById(R.id.editorOverlay)
        // Оверлей не кликабелен по умолчанию (жесты идут в ZoomableImageView, пока режим редактирования выключен)
        editorOverlay.isClickable = false
        editorOverlay.isFocusable = false
        editorOverlay.isFocusableInTouchMode = false
        jsonEditor = v.findViewById(R.id.jsonEditor)
        jsonSearchView = v.findViewById(R.id.jsonSearchView)
        btnJsonPrev = v.findViewById(R.id.btnJsonPrev)
        btnJsonNext = v.findViewById(R.id.btnJsonNext)
        tvJsonSearchPos = v.findViewById(R.id.tvJsonSearchPos)
        setupJsonSearch()
        btnPrevPage = v.findViewById(R.id.btnPrevPage)
        btnNextPage = v.findViewById(R.id.btnNextPage)
        tvPageInfo = v.findViewById(R.id.tvPageInfo)
        tvScale = v.findViewById(R.id.tvScale)
        btnUseOriginals = v.findViewById(R.id.btnUseOriginals)
        btnUseEdited = v.findViewById(R.id.btnUseEdited)
        // Уберём внутренние инкрусты MaterialButton для чистой высоты
        (btnUseOriginals as? com.google.android.material.button.MaterialButton)?.apply {
            insetTop = 0
            insetBottom = 0
            iconPadding = 0
            rippleColor = null
        }
        (btnUseEdited as? com.google.android.material.button.MaterialButton)?.apply {
            insetTop = 0
            insetBottom = 0
            iconPadding = 0
            rippleColor = null
        }
        toggleEditMode = v.findViewById(R.id.toggleEditMode)
        toggleShowAll = v.findViewById(R.id.toggleShowAll)
        btnUndo = v.findViewById(R.id.btnUndo)
        btnRedo = v.findViewById(R.id.btnRedo)
        tvLog = v.findViewById(R.id.tvLog)
        jsonScroll = v.findViewById(R.id.jsonScroll)
        logScroll = v.findViewById(R.id.logScroll)
        btnZoomIn = v.findViewById(R.id.btnZoomIn)
        btnZoomOut = v.findViewById(R.id.btnZoomOut)

        // Excel контейнер
        excelContainer = v.findViewById(R.id.excelContainer)
        excelRecyclerView = v.findViewById(R.id.excelRecyclerView)
        excelSearchView = v.findViewById(R.id.excelSearchView)
        excelToggleResizeButton = v.findViewById(R.id.btnExcelToggleResize)
        excelSaveButton = v.findViewById(R.id.btnExcelSave)

        // По умолчанию выбран режим «Оригиналы», кнопку «Сохранить» блокируем
        useEditedSource = false
        applyModeOutline()
        btnSave.isEnabled = false

        btnOpen.setOnClickListener { openFromSelectedSource() }
        btnSave.setOnClickListener {
            // КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Запускаем на Main, т.к. saveToSelectedSource использует withContext
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                try {
                saveToSelectedSource()
                } catch (e: Exception) {
                    Log.e("EditorFragment", "Error saving", e)
                    if (isAdded) Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        // Долгое удержание для сохранения в оригиналы (5 секунд)
        btnSave.setOnLongClickListener {
            btnSave.postDelayed({
                if (btnSave.isPressed) {
                    showSaveToOriginalsDialog()
                }
            }, 5000)
            true
        }
        btnUpload.setOnClickListener { confirmAndUploadArmatureCoords() }
        tvStatus.text = "Подсказка: двумя пальцами зум, одним пальцем панорамирование (режим выкл)."

        // Явно выставляем текст/цвет и выключаем font padding для идеального центрирования
        btnZoomIn.text = "+"
        btnZoomIn.isAllCaps = false
        btnZoomIn.setTextColor(Color.WHITE)
        btnZoomIn.includeFontPadding = false
        btnZoomIn.setPadding(0, 0, 0, 0)
        btnZoomOut.text = "-"
        btnZoomOut.isAllCaps = false
        btnZoomOut.setTextColor(Color.WHITE)
        btnZoomOut.includeFontPadding = false
        btnZoomOut.setPadding(0, 0, 0, 0)
        btnZoomIn.setOnClickListener { pdfZoomView.applyScale(1.25f) }
        btnZoomOut.setOnClickListener { pdfZoomView.applyScale(0.8f) }

        btnPrevPage.setOnClickListener { showPdfPage(pdfPageIndex - 1) }
        btnNextPage.setOnClickListener { showPdfPage(pdfPageIndex + 1) }

        btnUseOriginals.setOnClickListener {
            useEditedSource = false
            tvStatus.text = "Источник: оригиналы"
            applyModeOutline()
            // Блокируем сохранение в режиме оригиналов (только длинное удержание)
            btnSave.isEnabled = false
        }
        btnUseEdited.setOnClickListener {
            useEditedSource = true
            tvStatus.text = "Источник: редакции (editor_out)"
            applyModeOutline()
            // Разрешаем обычное сохранение
            btnSave.isEnabled = true
        }

        toggleEditMode.setOnCheckedChangeListener { _, isChecked ->
            editorOverlay.setEditMode(isChecked)
            // Включаем кликабельность только в режиме редактирования
            editorOverlay.isClickable = isChecked
            editorOverlay.isFocusable = isChecked
            editorOverlay.isFocusableInTouchMode = isChecked
            logMsg(if (isChecked) "Режим редактирования: ВКЛ" else "Режим редактирования: ВЫКЛ")
            applyToggleStyle(toggleEditMode, isChecked)
        }
        toggleShowAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) loadAndShowAllMarkersForCurrentPdf() else clearOverlayMarkers()
            applyToggleStyle(toggleShowAll, isChecked)
        }

        // Применяем начальные стили тумблеров
        applyToggleStyle(toggleEditMode, toggleEditMode.isChecked)
        applyToggleStyle(toggleShowAll, toggleShowAll.isChecked)

        btnUndo.setOnClickListener {
            if (excelContainer.visibility == View.VISIBLE) excelUndoAction() else undoAction()
        }
        btnRedo.setOnClickListener {
            if (excelContainer.visibility == View.VISIBLE) excelRedoAction() else redoAction()
        }
        (btnUndo as? com.google.android.material.button.MaterialButton)?.apply {
            insetTop = 0
            insetBottom = 0
            iconPadding = 0
            rippleColor = null
        }
        (btnRedo as? com.google.android.material.button.MaterialButton)?.apply {
            insetTop = 0
            insetBottom = 0
            iconPadding = 0
            rippleColor = null
        }

        pdfZoomView.onScaleChanged = { scale ->
            tvScale.visibility = View.VISIBLE
            btnZoomIn.visibility = View.VISIBLE
            btnZoomOut.visibility = View.VISIBLE
            tvScale.text = String.format("Масштаб: %.2f×", scale)
        }
        pdfZoomView.onMatrixChanged = { m ->
            editorOverlay.setPdfMapping(pdfToBitmapScale, m)
        }

        editorOverlay.setListener(object : EditorMarkerOverlayView.Listener {
            override fun onAddRequested(xPdf: Float, yPdf: Float, page: Int) {
                if (!toggleEditMode.isChecked) return
                showAddOrEditDialog(null, xPdf, yPdf, page)
            }
            override fun onEditRequested(marker: EditorMarkerOverlayView.EditorMarkerItem) {
                if (!toggleEditMode.isChecked) return
                showAddOrEditDialog(marker, marker.xPdf, marker.yPdf, marker.page)
            }
            override fun onMoveFinished(marker: EditorMarkerOverlayView.EditorMarkerItem, newXPdf: Float, newYPdf: Float) {
                pushUndo()
                logMsg("Перемещено: ${marker.id} → (${newXPdf.toInt()}, ${newYPdf.toInt()})")
            }
        })

        // Привязываем оверлей к ZoomableImageView для панорамирования при выключенном редактировании
        editorOverlay.setPanTargetView(pdfZoomView)
        // Отключим звуки кликов у всего дерева экрана редактора
        v.isSoundEffectsEnabled = false

        return v
    }

    // ====== Поиск в JSON ======
    private fun setupJsonSearch() {
        jsonSearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = true
            override fun onQueryTextChange(newText: String?): Boolean {
                performJsonSearch(newText ?: "")
                return true
            }
        })
        btnJsonPrev.setOnClickListener { navigateJsonMatch(-1) }
        btnJsonNext.setOnClickListener { navigateJsonMatch(1) }
    }
    
    private fun performJsonSearch(query: String) {
        if (jsonScroll.visibility != View.VISIBLE) return
        try {
            val text = jsonEditor.text.toString()
            if (query.isEmpty()) {
                jsonEditor.setText(text)
                jsonSearchMatches = emptyList()
                jsonCurrentMatchIndex = 0
                btnJsonPrev.visibility = View.GONE
                btnJsonNext.visibility = View.GONE
                tvJsonSearchPos.visibility = View.GONE
                tvStatus.text = ""
                return
            }
            
            // Находим все позиции совпадений
            val matches = mutableListOf<Int>()
            var idx = 0
            while (idx < text.length) {
                val pos = text.indexOf(query, idx, ignoreCase = true)
                if (pos < 0) break
                matches.add(pos)
                idx = pos + 1
            }
            
            jsonSearchMatches = matches
            jsonCurrentMatchIndex = 0
            
            // Подсвечиваем все найденные
            val spannable = android.text.SpannableStringBuilder(text)
            matches.forEachIndexed { index, pos ->
                // Текущее совпадение - более яркая подсветка
                val color = if (index == 0) Color.GREEN else Color.YELLOW
                spannable.setSpan(
                    android.text.style.BackgroundColorSpan(color),
                    pos,
                    pos + query.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            
            jsonEditor.setText(spannable)
            
            if (matches.isNotEmpty()) {
                // Показываем кнопки навигации
                btnJsonPrev.visibility = View.VISIBLE
                btnJsonNext.visibility = View.VISIBLE
                tvJsonSearchPos.visibility = View.VISIBLE
                tvJsonSearchPos.text = "1/${matches.size}"
                tvStatus.text = "Найдено: ${matches.size}"
                
                // Прокручиваем к первому совпадению
                scrollToJsonMatch(0, query.length)
            } else {
                btnJsonPrev.visibility = View.GONE
                btnJsonNext.visibility = View.GONE
                tvJsonSearchPos.visibility = View.GONE
                tvStatus.text = "Не найдено"
            }
        } catch (e: Exception) {
            Log.e("EditorFragment", "JSON search error", e)
        }
    }
    
    private fun navigateJsonMatch(direction: Int) {
        if (jsonSearchMatches.isEmpty()) return
        
        val query = jsonSearchView.query?.toString() ?: return
        if (query.isEmpty()) return
        
        // Вычисляем новый индекс с циклическим переходом
        jsonCurrentMatchIndex = (jsonCurrentMatchIndex + direction + jsonSearchMatches.size) % jsonSearchMatches.size
        
        // Обновляем подсветку - текущее совпадение зеленое, остальные желтые
        val text = jsonEditor.text.toString()
        val spannable = android.text.SpannableStringBuilder(text)
        jsonSearchMatches.forEachIndexed { index, pos ->
            val color = if (index == jsonCurrentMatchIndex) Color.GREEN else Color.YELLOW
            spannable.setSpan(
                android.text.style.BackgroundColorSpan(color),
                pos,
                pos + query.length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        
        jsonEditor.setText(spannable)
        tvJsonSearchPos.text = "${jsonCurrentMatchIndex + 1}/${jsonSearchMatches.size}"
        
        // Прокручиваем к текущему совпадению
        scrollToJsonMatch(jsonCurrentMatchIndex, query.length)
    }
    
    private fun scrollToJsonMatch(matchIndex: Int, queryLength: Int) {
        if (matchIndex < 0 || matchIndex >= jsonSearchMatches.size) return
        
        val position = jsonSearchMatches[matchIndex]
        
        // Устанавливаем курсор на найденное совпадение
        try {
            jsonEditor.setSelection(position, position + queryLength)
            
            // Прокручиваем ScrollView к позиции
            jsonEditor.post {
                val layout = jsonEditor.layout ?: return@post
                val line = layout.getLineForOffset(position)
                val y = layout.getLineTop(line)
                
                // Находим родительский ScrollView
                val scrollView = jsonEditor.parent as? android.widget.ScrollView
                scrollView?.smoothScrollTo(0, y - 100) // -100 для отступа сверху
            }
        } catch (e: Exception) {
            Log.e("EditorFragment", "Error scrolling to match", e)
        }
    }

    // ====== Undo/Redo для Excel ======
    private val excelUndo: ArrayDeque<List<org.example.pult.RowDataDynamic>> = ArrayDeque()
    private val excelRedo: ArrayDeque<List<org.example.pult.RowDataDynamic>> = ArrayDeque()

    private fun excelPushUndo() {
        excelUndo.addLast(excelData.map { it })
        if (excelUndo.size > 30) excelUndo.removeFirst()
        excelRedo.clear()
    }

    private fun excelUndoAction() {
        if (excelContainer.visibility != View.VISIBLE) return
        if (excelUndo.isEmpty()) return
        val current = excelData.map { it }
        val prev = excelUndo.removeLast()
        excelRedo.addLast(current)
        excelData.clear(); excelData.addAll(prev)
        excelAdapter?.updateData(excelData, excelHeaders, excelColumnWidths, excelIsResizingMode)
    }

    private fun excelRedoAction() {
        if (excelContainer.visibility != View.VISIBLE) return
        if (excelRedo.isEmpty()) return
        val current = excelData.map { it }
        val next = excelRedo.removeLast()
        excelUndo.addLast(current)
        excelData.clear(); excelData.addAll(next)
        excelAdapter?.updateData(excelData, excelHeaders, excelColumnWidths, excelIsResizingMode)
    }

    private fun applyModeOutline() {
        // Возвращаем надёжную схему: selected background drawable с толстой оранжевой рамкой
        val normalBg = resources.getDrawable(R.drawable.bg_mode_button, null)
        val selectedBg = resources.getDrawable(R.drawable.bg_mode_button_selected, null)

        (btnUseOriginals as? MaterialButton)?.apply {
            try { foreground = null } catch (_: Throwable) {}
            try { backgroundTintList = null } catch (_: Throwable) {}
            background = if (!useEditedSource) selectedBg else normalBg
            setTextColor(Color.WHITE)
        }
        (btnUseEdited as? MaterialButton)?.apply {
            try { foreground = null } catch (_: Throwable) {}
            try { backgroundTintList = null } catch (_: Throwable) {}
            background = if (useEditedSource) selectedBg else normalBg
            setTextColor(Color.WHITE)
        }
        btnUseOriginals.invalidate()
        btnUseEdited.invalidate()
    }

    private fun saveJsonCopy() {
        if (lastOpenedJson.isBlank()) {
            tvStatus.text = "Нет данных для сохранения"
            return
        }
        try {
            val dir = requireContext().getDir("editor_out", Context.MODE_PRIVATE)
            val name = "edited_${System.currentTimeMillis()}.json"
            val outFile = java.io.File(dir, name)
            outFile.writeText(lastOpenedJson)
            tvStatus.text = "Сохранено: ${outFile.absolutePath}"
        } catch (e: Exception) {
            Log.e("EditorFragment", "Error saving copy", e)
            tvStatus.text = "Ошибка сохранения: ${e.message}"
        }
    }

    private val IntentFlagsRW: Int
        get() = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    // ===== Source-aware open helpers =====
    private fun openFromSelectedSource() {
        val root = if (toggleEditMode.isChecked) originalsDir() else if (useEditedSource) editedDir() else originalsDir()
        if (!root.exists()) {
            tvStatus.text = if (useEditedSource) "Папка редакций отсутствует" else "Папка оригиналов отсутствует"
            return
        }
        // В режиме редактирования показываем все файлы (PDF, JSON, XLSX) из папки редакций
        val allowed = if (toggleEditMode.isChecked) setOf("pdf", "json", "xlsx") else setOf("pdf")
        browseDirectory(
            title = if (toggleEditMode.isChecked) "Выберите файл (PDF или JSON)" else "Выберите PDF",
            rootDir = root,
            currentDir = root,
            allowedExtensions = allowed
        ) { file ->
            when {
                file.name.endsWith(".json", ignoreCase = true) -> {
                    try {
                        val json = file.readText()
                        val pretty = try { gson.toJson(com.google.gson.JsonParser.parseString(json)) } catch (_: Exception) { json }
                        lastOpenedJson = pretty
                        lastOpenedJsonUri = Uri.fromFile(file)
                        jsonEditor.setText(pretty)
                        // КРИТИЧЕСКОЕ: Скрываем ВСЕ контейнеры кроме JSON
                        jsonScroll.visibility = View.VISIBLE
                        jsonEditor.visibility = View.VISIBLE
                        excelContainer.visibility = View.GONE
                        logScroll.visibility = View.GONE
                        pdfZoomView.visibility = View.GONE
                        editorOverlay.visibility = View.GONE
                        btnPrevPage.visibility = View.GONE
                        btnNextPage.visibility = View.GONE
                        tvPageInfo.visibility = View.GONE
                        tvScale.visibility = View.GONE
                        btnSave.isEnabled = true
                        tvStatus.text = "JSON загружен: ${file.name} (${json.length} симв.)"
                        Log.d("EditorFragment", "JSON editor visible, text length: ${pretty.length}")
                    } catch (e: Exception) {
                        Log.e("EditorFragment", "read json error", e)
                        tvStatus.text = "Ошибка чтения: ${e.message}"
                    }
                }
                file.name.endsWith(".xlsx", ignoreCase = true) -> {
                    openExcelInsideEditor(file)
                }
                else -> {
                    // Отключаем Excel/JSON UI при открытии PDF
                    excelContainer.visibility = View.GONE
                    jsonScroll.visibility = View.GONE
                    logScroll.visibility = View.VISIBLE
                    openPdfFromFile(file)
                }
            }
        }
    }

    private fun openJsonFromSource() {
        val root = if (useEditedSource) editedDir() else originalsDir()
        if (!root.exists()) {
            tvStatus.text = if (useEditedSource) "Папка редакций отсутствует" else "Папка оригиналов отсутствует"
            return
        }
        browseDirectory(
            title = "Выберите JSON",
            rootDir = root,
            currentDir = root,
            allowedExtensions = setOf("json")
        ) { file ->
            try {
                val json = file.readText()
                val pretty = try { gson.toJson(com.google.gson.JsonParser.parseString(json)) } catch (_: Exception) { json }
                lastOpenedJson = pretty
                lastOpenedJsonUri = Uri.fromFile(file)
                jsonEditor.setText(pretty)
                jsonScroll.visibility = View.VISIBLE
                jsonEditor.visibility = View.VISIBLE
                pdfZoomView.visibility = View.GONE
                editorOverlay.visibility = View.GONE
                btnPrevPage.visibility = View.GONE
                btnNextPage.visibility = View.GONE
                tvPageInfo.visibility = View.GONE
                tvScale.visibility = View.GONE
                tvStatus.text = "Загружен ${file.name} (${json.length} симв.)"
            } catch (e: Exception) {
                Log.e("EditorFragment", "read json error", e)
                tvStatus.text = "Ошибка чтения: ${e.message}"
            }
        }
    }

    private fun editedDir(): java.io.File = storage.editedDir()
    private fun originalsDir(): java.io.File = storage.originalsDir()

    private fun showFilePicker(title: String, files: List<java.io.File>, onPick: (java.io.File) -> Unit) {
        val names = files.map { it.name }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setItems(names) { d, which ->
                onPick(files[which])
                d.dismiss()
            }
            .setNegativeButton("Назад", null)
            .show()
    }

    private fun browseDirectory(
        title: String,
        rootDir: java.io.File,
        currentDir: java.io.File,
        allowedExtensions: Set<String>,
        onPick: (java.io.File) -> Unit
    ) {
        val canGoUp = currentDir.absolutePath != rootDir.absolutePath
        val dirs = (currentDir.listFiles { f -> f.isDirectory } ?: emptyArray()).sortedBy { it.name.lowercase() }
        val files = (currentDir.listFiles { f -> f.isFile && allowedExtensions.any { ext -> f.name.endsWith(".$ext", true) } } ?: emptyArray()).sortedBy { it.name.lowercase() }

        val items = mutableListOf<String>()
        val map = mutableListOf<java.io.File?>()
        if (canGoUp) {
            items.add("⟵ Вверх")
            map.add(currentDir.parentFile)
        }
        dirs.forEach { d ->
            items.add("${d.name}/")
            map.add(d)
        }
        files.forEach { f ->
            items.add(f.name)
            map.add(f)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("$title\n${currentDir.absolutePath}")
            .setItems(items.toTypedArray()) { dialog, which ->
                val picked = map[which]
                if (picked == null) {
                    dialog.dismiss()
                    return@setItems
                }
                if (picked.isDirectory) {
                    dialog.dismiss()
                    browseDirectory(title, rootDir, picked, allowedExtensions, onPick)
                } else {
                    dialog.dismiss()
                    onPick(picked)
                }
            }
            .setNegativeButton("Назад", null)
            .show()
    }

    // ===== Встроенный Excel редактор =====
    private fun openExcelInsideEditor(file: java.io.File) {
        try {
            // Скрываем PDF и JSON, показываем Excel UI
            pdfZoomView.visibility = View.GONE
            editorOverlay.visibility = View.GONE
            jsonScroll.visibility = View.GONE
            logScroll.visibility = View.GONE
            excelContainer.visibility = View.VISIBLE
            tvStatus.text = "Открыт Excel: ${file.name}"

            // Загружаем Excel в фоне, чтобы избежать ANR/блокировок UI
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val provider: com.example.vkbookandroid.IFileProvider = object : com.example.vkbookandroid.IFileProvider {
                        override fun open(relativePath: String): java.io.InputStream = file.inputStream()
                    }
                    val repo = com.example.vkbookandroid.ExcelRepository(requireContext(), provider)
                    // Определяем, какой файл открыт, чтобы выбрать правильный лист
                    val isArmatures = file.name.equals("Armatures.xlsx", ignoreCase = true)
                    val session = if (isArmatures) repo.openPagingSessionArmatures() else repo.openPagingSessionBschu()

                    val headersLocal = session.getHeaders()
                    val widthsLocal = session.getColumnWidths().toMutableMap()
                    val dataLocal = session.readRange(0, 2000) // читаем побольше, но разумно
                    session.close()

                    withContext(Dispatchers.Main) {
                        excelHeaders = headersLocal
                        excelColumnWidths = widthsLocal
                        excelData.clear()
                        excelData.addAll(dataLocal)

                        if (excelAdapter == null) {
                            excelAdapter = SignalsAdapter(
                                data = excelData,
                                _isResizingMode = excelIsResizingMode,
                                onColumnResize = { col, newW, _ ->
                                    val header = excelHeaders.getOrNull(col) ?: return@SignalsAdapter
                                    excelColumnWidths[header] = newW
                                },
                                onRowClick = null,
                                onArmatureCellClick = null,
                                hidePdfSchemeColumn = false,
                                onCellClick = { rowIndex, columnIndex, headerName, currentValue, rowData ->
                                    showCellEditDialog(rowIndex, columnIndex, headerName, currentValue, rowData)
                                }
                            )
                            excelRecyclerView.adapter = excelAdapter
                            excelRecyclerView.setHasFixedSize(false)
                            excelRecyclerView.itemAnimator = null
                            excelRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
                        }
                        excelAdapter?.updateData(excelData, excelHeaders, excelColumnWidths, excelIsResizingMode, updateOriginal = true)

                        // Поиск (на UI потоке)
                        excelSearchView.queryHint = "Поиск"
                        excelSearchView.isIconified = false
                        // Сохраняем русскую раскладку для поля поиска
                        try {
                            val id = androidx.appcompat.R.id.search_src_text
                            val searchSrc: View? = excelSearchView.findViewById(id)
                            if (searchSrc is EditText) {
                                searchSrc.setTextLocale(java.util.Locale("ru"))
                                if (Build.VERSION.SDK_INT >= 24) {
                                    searchSrc.imeHintLocales = LocaleList(java.util.Locale("ru", "RU"))
                                }
                                searchSrc.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                            }
                        } catch (_: Throwable) {}

                        excelSearchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
                            override fun onQueryTextSubmit(query: String?): Boolean { applyExcelSearch(query ?: ""); return true }
                            override fun onQueryTextChange(newText: String?): Boolean { applyExcelSearch(newText ?: ""); return true }
                        })

                        // Переключатель режима изменения ширины
                        excelToggleResizeButton.setOnClickListener {
                            excelIsResizingMode = !excelIsResizingMode
                            excelToggleResizeButton.text = if (excelIsResizingMode) "Сохранить" else "Редактировать"
                            excelAdapter?.setResizingMode(excelIsResizingMode)
                        }
                        // Сохранение Excel
                        excelSaveButton.setOnClickListener { showExcelSaveDialog(file) }
                    }
                } catch (e: Exception) {
                    Log.e("EditorFragment", "openExcelInsideEditor bg load error", e)
                    withContext(Dispatchers.Main) { tvStatus.text = "Ошибка открытия Excel: ${e.message}" }
                }
            }
        } catch (e: Exception) {
            Log.e("EditorFragment", "openExcelInsideEditor error", e)
            tvStatus.text = "Ошибка открытия Excel: ${e.message}"
        }
    }

    private var excelSearchJob: kotlinx.coroutines.Job? = null
    private fun applyExcelSearch(query: String) {
        // ИСПРАВЛЕНО: Проверяем состояние фрагмента перед запуском корутины
        if (!isAdded || view == null) {
            Log.w("EditorFragment", "applyExcelSearch: fragment not ready")
            return
        }
        
        val normalized = query.trim()
        excelSearchJob?.cancel()
        
        try {
        excelSearchJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
                try {
            // Небольшая задержка для дебаунса
            kotlinx.coroutines.delay(220)
                    
                    // Проверяем что фрагмент еще жив после задержки
                    if (!isAdded) return@launch
                    
            if (normalized.isEmpty()) {
                        withContext(Dispatchers.Main) { 
                            if (isAdded) excelAdapter?.clearSearchResults() 
                        }
                return@launch
            }
                    
            // Фильтр на фоне с учётом поиска по конкретной колонке
            val selectedHeader = try { excelAdapter?.getSelectedColumnName() } catch (_: Throwable) { null }
            val filtered = if (!selectedHeader.isNullOrBlank()) {
                val colIndex = excelHeaders.indexOfFirst { it.equals(selectedHeader, ignoreCase = true) }
                if (colIndex >= 0) {
                    excelData.filter { row ->
                        val vals = row.getAllProperties()
                        vals.getOrNull(colIndex)?.toString()?.contains(normalized, ignoreCase = true) == true
                    }
                } else {
                    excelData.filter { row ->
                        val vals = row.getAllProperties()
                        vals.any { it?.toString()?.contains(normalized, ignoreCase = true) == true }
                    }
                }
            } else {
                excelData.filter { row ->
                    val vals = row.getAllProperties()
                    vals.any { it?.toString()?.contains(normalized, ignoreCase = true) == true }
                }
            }
                    
            withContext(Dispatchers.Main) {
                        if (isAdded) excelAdapter?.setSearchResults(filtered, normalized)
                    }
                } catch (e: Exception) {
                    Log.e("EditorFragment", "Excel search error", e)
                }
            }
        } catch (e: Exception) {
            Log.e("EditorFragment", "Failed to start Excel search", e)
        }
    }

    private fun showCellEditDialog(rowIndex: Int, columnIndex: Int, headerName: String, currentValue: String, rowData: org.example.pult.RowDataDynamic) {
        val ctx = requireContext()
        val input = EditText(ctx).apply {
            setText(currentValue)
            // Сохранить русскую раскладку/локаль
            try { setTextLocale(java.util.Locale("ru")) } catch (_: Throwable) {}
            if (Build.VERSION.SDK_INT >= 24) {
                try { imeHintLocales = LocaleList(java.util.Locale("ru", "RU")) } catch (_: Throwable) {}
            }
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            isSingleLine = false
            setLines(1)
        }
        AlertDialog.Builder(ctx)
            .setTitle("Редактировать: $headerName")
            .setView(input)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Сохранить") { d, _ ->
                d.dismiss()
                val newValue = input.text.toString()
                updateExcelCell(rowData, columnIndex, newValue)
            }
            .show()
    }

    private fun updateExcelCell(rowData: org.example.pult.RowDataDynamic, columnIndex: Int, newValue: String) {
        try {
            excelPushUndo()
            
            // Находим реальный индекс строки в полных данных excelData
            val realRowIndex = excelData.indexOf(rowData)
            if (realRowIndex < 0) {
                Log.e("EditorFragment", "updateExcelCell: строка не найдена в данных")
                return
            }
            
            val headers = excelHeaders
            if (columnIndex < 0 || columnIndex >= headers.size) return
            val map = rowData.getAllProperties().toMutableList()
            if (columnIndex < map.size) {
                map[columnIndex] = newValue
                // Обновляем объект RowDataDynamic — создадим новый
                val newMap = linkedMapOf<String, String>()
                headers.forEachIndexed { i, h -> newMap[h] = (map.getOrNull(i)?.toString() ?: "") }
                val newRow = org.example.pult.RowDataDynamic(newMap)
                excelData[realRowIndex] = newRow
                excelAdapter?.updateData(excelData, headers, excelColumnWidths, excelIsResizingMode)
            }
        } catch (e: Exception) {
            Log.e("EditorFragment", "updateExcelCell error", e)
        }
    }

    private fun showExcelSaveDialog(openedFile: java.io.File) {
        val choices = arrayOf("Сохранить в Оригиналы", "Сохранить в Редактируемые")
        AlertDialog.Builder(requireContext())
            .setTitle("Куда сохранить изменения?")
            .setItems(choices) { d, which ->
                d.dismiss()
                when (which) {
                    0 -> {
                        // Показываем предупреждение перед сохранением в Оригиналы
                        AlertDialog.Builder(requireContext())
                            .setTitle("⚠️ Внимание!")
                            .setMessage("После загрузки обновлений все файлы в папке \"Оригиналы\" будут перезаписаны данными с сервера.\n\nВы уверены, что хотите сохранить изменения в Оригиналы?")
                            .setNegativeButton("Отмена", null)
                            .setPositiveButton("Сохранить") { _, _ ->
                                persistExcelChanges(toOriginals = true, openedFile)
                            }
                            .show()
                    }
                    1 -> persistExcelChanges(toOriginals = false, openedFile)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun persistExcelChanges(toOriginals: Boolean, openedFile: java.io.File) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Записываем excelData в файл openedFile (Armatures.xlsx или Oborudovanie_BSCHU.xlsx) — обновляем построчно по headers
                val openedName = openedFile.name
                val sheetName = when {
                    openedName.equals("Armatures.xlsx", ignoreCase = true) -> "Арматура"
                    openedName.equals("Oborudovanie_BSCHU.xlsx", ignoreCase = true) -> "Сигналы БЩУ"
                    else -> "Арматура"
                }
                FileInputStream(openedFile).use { fis ->
                    val wb = XSSFWorkbook(fis)
                    val sheet = wb.getSheet(sheetName) ?: wb.createSheet(sheetName)
                    // Заголовок
                    var headerRow = sheet.getRow(0)
                    if (headerRow == null) headerRow = sheet.createRow(0)
                    excelHeaders.forEachIndexed { i, h ->
                        val c = headerRow.getCell(i) ?: headerRow.createCell(i)
                        c.setCellValue(h)
                    }
                    // Данные
                    for (i in excelData.indices) {
                        val row = sheet.getRow(i + 1) ?: sheet.createRow(i + 1)
                        val values = excelData[i].getAllProperties()
                        excelHeaders.forEachIndexed { ci, _ ->
                            val cell = row.getCell(ci) ?: row.createCell(ci)
                            val v = values.getOrNull(ci)?.toString() ?: ""
                            cell.setCellValue(v)
                        }
                    }
                    // Сохраняем в нужную папку
                    val targetDir = if (toOriginals) originalsDir() else editedDir()
                    if (!targetDir.exists()) targetDir.mkdirs()
                    val out = java.io.File(targetDir, openedName)
                    java.io.FileOutputStream(out).use { fos -> wb.write(fos) }
                    wb.close()
                }
                withContext(Dispatchers.Main) {
                    tvStatus.text = if (toOriginals) "Excel сохранён в оригиналы" else "Excel сохранён в редакции"
                    
                    // Если сохранили в Оригиналы, обновляем соответствующую вкладку через 5 секунд
                    if (toOriginals) {
                        viewLifecycleOwner.lifecycleScope.launch {
                            kotlinx.coroutines.delay(5000)
                            try {
                                when {
                                    openedName.equals("Armatures.xlsx", ignoreCase = true) -> {
                                        (activity as? MainActivity)?.refreshArmatureFragmentData()
                                        Log.d("EditorFragment", "Triggered ArmatureFragment refresh after saving to originals")
                                    }
                                    openedName.equals("Oborudovanie_BSCHU.xlsx", ignoreCase = true) -> {
                                        (activity as? MainActivity)?.refreshDataFragmentData()
                                        Log.d("EditorFragment", "Triggered DataFragment refresh after saving to originals")
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("EditorFragment", "Failed to trigger fragment refresh", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("EditorFragment", "persistExcelChanges error", e)
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Ошибка сохранения Excel: ${e.message}"
                }
            }
        }
    }

    // ===== PDF Renderer =====
    private fun openPdf(uri: Uri) {
        try {
            closePdf()
            pdfUri = uri
            currentPdfName = uri.lastPathSegment?.substringAfterLast('/')?.substringAfter(":")
            if (uri.scheme == "file") {
                val file = java.io.File(uri.path!!)
                pdfFd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            } else {
                val cr = requireContext().contentResolver
                pdfFd = cr.openFileDescriptor(uri, "r")
            }
            pdfRenderer = PdfRenderer(pdfFd!!)
            pdfPageIndex = 0
            pdfZoomView.visibility = View.VISIBLE
            editorOverlay.visibility = View.VISIBLE
            jsonScroll.visibility = View.GONE
            excelContainer.visibility = View.GONE
            logScroll.visibility = View.VISIBLE
            // Пагинация не нужна в режиме редактора
            btnPrevPage.visibility = View.GONE
            btnNextPage.visibility = View.GONE
            tvPageInfo.visibility = View.GONE
            showPdfPage(0)
            // Редактирование по умолчанию выкл, чтобы панорамирование сразу работало
            toggleEditMode.isChecked = false
            editorOverlay.setEditMode(false)
            editorOverlay.isClickable = false
            editorOverlay.isFocusable = false
            editorOverlay.isFocusableInTouchMode = false
            applyToggleStyle(toggleEditMode, false)
            clearOverlayMarkers()
            if (toggleShowAll.isChecked) loadAndShowAllMarkersForCurrentPdf()
        } catch (e: Exception) {
            Log.e("EditorFragment", "openPdf error", e)
            tvStatus.text = "Ошибка открытия PDF: ${e.message}"
        }
    }

    private fun openPdfFromFile(file: java.io.File) {
        try {
            closePdf()
            pdfUri = Uri.fromFile(file)
            currentPdfName = file.name
            pdfFd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(pdfFd!!)
            pdfPageIndex = 0
            pdfZoomView.visibility = View.VISIBLE
            editorOverlay.visibility = View.VISIBLE
            jsonScroll.visibility = View.GONE
            excelContainer.visibility = View.GONE
            logScroll.visibility = View.VISIBLE
            // Пагинация не нужна в режиме редактора
            btnPrevPage.visibility = View.GONE
            btnNextPage.visibility = View.GONE
            tvPageInfo.visibility = View.GONE
            showPdfPage(0)
            // Редактирование по умолчанию выкл, чтобы панорамирование сразу работало
            toggleEditMode.isChecked = false
            editorOverlay.setEditMode(false)
            editorOverlay.isClickable = false
            editorOverlay.isFocusable = false
            editorOverlay.isFocusableInTouchMode = false
            applyToggleStyle(toggleEditMode, false)
            clearOverlayMarkers()
            if (toggleShowAll.isChecked) loadAndShowAllMarkersForCurrentPdf()
        } catch (e: Exception) {
            Log.e("EditorFragment", "openPdfFromFile error", e)
            tvStatus.text = "Ошибка открытия PDF: ${e.message}"
        }
    }

    private fun showPdfPage(index: Int) {
        val renderer = pdfRenderer ?: return
        if (renderer.pageCount == 0) return
        val newIndex = index.coerceIn(0, renderer.pageCount - 1)
        pdfPageIndex = newIndex
        val page = renderer.openPage(pdfPageIndex)
        val pageW = page.width
        val pageH = page.height
        val dm = resources.displayMetrics
        val sideCap = 8192
        val isSmallPage = pageW <= dm.widthPixels && pageH <= dm.heightPixels
        val screenMultiplier = if (isSmallPage) 2 else 3
        val maxW = kotlin.math.min(dm.widthPixels * screenMultiplier, sideCap)
        val maxH = kotlin.math.min(dm.heightPixels * screenMultiplier, sideCap)
        var scale = kotlin.math.min(maxW / pageW.toFloat(), maxH / pageH.toFloat())
        if (scale <= 0f || !scale.isFinite()) scale = 1f
        val bw = kotlin.math.max(1, (pageW * scale).toInt())
        val bh = kotlin.math.max(1, (pageH * scale).toInt())
        val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        pdfZoomView.setImageBitmap(bitmap)
        pdfToBitmapScale = bitmap.width / pageW.toFloat()
        // Центрируем схему в окне и показываем масштаб 1.0
        pdfZoomView.setScaleAndCenter(1f, bitmap.width / 2f, bitmap.height / 2f)
        editorOverlay.setCurrentPage(pdfPageIndex + 1)
        editorOverlay.setPdfMapping(pdfToBitmapScale, pdfZoomView.getImageMatrixCopy())
        // Прячем элементы пагинации в редакторе
        btnPrevPage.visibility = View.GONE
        btnNextPage.visibility = View.GONE
        tvPageInfo.visibility = View.GONE
        jsonScroll.visibility = View.GONE
        jsonEditor.visibility = View.GONE
        logScroll.visibility = View.VISIBLE
        tvPageInfo.text = "${pdfPageIndex + 1}/${renderer.pageCount}"
    }

    private fun closePdf() {
        try { pdfRenderer?.close() } catch (_: Exception) {}
        try { pdfFd?.close() } catch (_: Exception) {}
        pdfRenderer = null
        pdfFd = null
        currentPdfName = null
        pdfToBitmapScale = 1f
        clearOverlayMarkers()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        closePdf()
        
        // Очистка ресурсов для предотвращения утечек памяти
        excelSearchJob?.cancel()
        excelAdapter?.cleanup()
    }

    // ===== Маркеры / Undo-Redo =====
    private fun pushUndo() {
        undoStack.addLast(editorOverlay.getMarkers())
        if (undoStack.size > 50) undoStack.removeFirst()
        redoStack.clear()
    }
    private fun undoAction() {
        if (undoStack.isEmpty()) return
        val current = editorOverlay.getMarkers()
        val prev = undoStack.removeLast()
        redoStack.addLast(current)
        editorOverlay.setMarkers(prev)
    }
    private fun redoAction() {
        if (redoStack.isEmpty()) return
        val current = editorOverlay.getMarkers()
        val next = redoStack.removeLast()
        undoStack.addLast(current)
        editorOverlay.setMarkers(next)
    }

    private fun clearOverlayMarkers() {
        editorOverlay.clearMarkers()
    }

    // Нормализуем имя PDF для ключа в JSON (убираем пути и префиксы)
    private fun normalizePdfName(name: String?): String? {
        return name?.substringAfterLast('/')?.substringAfter(":")
    }

    // Сохраняем изменения меток в JSON в выбранный источник
    private fun saveChangesToEditorOut() {
        val pdfName = normalizePdfName(currentPdfName)
        if (pdfName.isNullOrBlank()) { tvStatus.text = "Нет открытого PDF"; return }
        val baseEdited = storage.loadJson(true)
        val base = if (baseEdited.isNotEmpty()) baseEdited else storage.loadJson(false)
        val perPdf = (base[pdfName] as? MutableMap<String, Any?>) ?: mutableMapOf<String, Any?>().also { base[pdfName] = it }
        val list = editorOverlay.getMarkers()
        var added = 0
        var updated = 0
        var deleted = 0
        // Удаления
        if (deletedIds.isNotEmpty()) {
            for (id in deletedIds) if (perPdf.remove(id) != null) deleted++
            deletedIds.clear()
        }
        // Добавление/обновление
        for (m in list) {
            val obj = mutableMapOf<String, Any?>(
                "page" to m.page,
                "x" to m.xPdf,
                "y" to m.yPdf,
                "width" to m.wPdf,
                "height" to m.hPdf,
                "zoom" to 3.0,
                "label" to (m.label ?: m.id),
                "comment" to m.comment,
                "marker_type" to "square:${String.format("#%06X", m.color and 0xFFFFFF)}:${m.wPdf.toInt()}"
            )
            val existed = perPdf.containsKey(m.id)
            perPdf[m.id] = obj
            if (existed) updated++ else added++
        }
        val outFile = storage.saveJson(base, true)
        logMsg("Сохранено (редакции): добавлено=$added, обновлено=$updated, удалено=$deleted")
        Log.d("EditorFragment", "Changes saved to: ${outFile.absolutePath}")
    }

    private fun loadAndShowAllMarkersForCurrentPdf() {
        val pdfNameRaw = currentPdfName ?: return
        val pdfName = normalizePdfName(pdfNameRaw) ?: return
        try {
            val base = loadJsonFromCurrentSource()
            val perPdf = (base[pdfName] as? Map<*, *>) ?: emptyMap<String, Any>()
            val items = perPdf.mapNotNull { (idAny, coordsAny) ->
                val id = idAny as? String ?: return@mapNotNull null
                val coords = coordsAny as? Map<*, *> ?: return@mapNotNull null
                val page = (coords["page"] as? Number)?.toInt() ?: 1
                val x = (coords["x"] as? Number)?.toFloat() ?: 0f
                val y = (coords["y"] as? Number)?.toFloat() ?: 0f
                val w = (coords["width"] as? Number)?.toFloat() ?: 16f
                val h = (coords["height"] as? Number)?.toFloat() ?: 16f
                val label = (coords["label"] as? String) ?: id
                val comment = coords["comment"] as? String
                val markerType = coords["marker_type"] as? String
                val color = parseColorFromMarkerType(markerType)
                EditorMarkerOverlayView.EditorMarkerItem(
                    id = id,
                    page = page,
                    xPdf = x,
                    yPdf = y,
                    wPdf = w,
                    hPdf = h,
                    color = color,
                    label = label,
                    comment = comment
                )
            }
            editorOverlay.setMarkers(items)
            pushUndo()
            logMsg("Загружено меток: ${items.size}")
        } catch (e: Exception) {
            Log.e("EditorFragment", "load markers error", e)
            tvStatus.text = "Ошибка загрузки меток: ${e.message}"
        }
    }

    private fun showAddOrEditDialog(existing: EditorMarkerOverlayView.EditorMarkerItem?, xPdf: Float, yPdf: Float, page: Int) {
        val ctx = requireContext()
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 12, 40, 0)
        }
        val inputId = EditText(ctx).apply { hint = "Обозначение"; setText(existing?.id ?: "") }
        val inputSize = EditText(ctx).apply { hint = "Размер"; inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText(((existing?.wPdf ?: 5f).toInt()).toString()) }
        val inputZoom = EditText(ctx).apply { hint = "Zoom (при переходе)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL; setText("3.0") }
        val inputColor = EditText(ctx).apply { hint = "Цвет #RRGGBB"; setText("#%06X".format((existing?.color ?: Color.RED) and 0xFFFFFF)) }
        val inputComment = EditText(ctx).apply { hint = "Комментарий"; setText(existing?.comment ?: "") }
        container.addView(inputId)
        container.addView(inputSize)
        container.addView(inputZoom)
        container.addView(inputColor)
        container.addView(inputComment)

        val builder = AlertDialog.Builder(ctx)
            .setTitle(if (existing == null) "Добавить отметку" else "Редактировать отметку")
            .setView(container)
            .setNegativeButton("Отмена", null)
        if (existing != null) {
            builder.setNeutralButton("Удалить", null)
        }
        builder.setPositiveButton("Сохранить", null)
        val dialog = builder.show()

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
            if (existing != null) {
                deletedIds.add(existing.id)
                val remain = editorOverlay.getMarkers().filter { it.id != existing.id }
                pushUndo()
                editorOverlay.setMarkers(remain)
                logMsg("Удалено: ${existing.id}")
                dialog.dismiss()
            }
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val id = inputId.text.toString().trim()
            val size = inputSize.text.toString().toFloatOrNull()?.coerceAtLeast(1f) ?: 5f
            val zoomVal = inputZoom.text.toString().toFloatOrNull()?.coerceIn(0.2f, 20f) ?: 3f
            val color = try { Color.parseColor(inputColor.text.toString().trim()) } catch (_: Exception) { Color.RED }
            val comment = inputComment.text.toString().takeIf { it.isNotBlank() }
            if (id.isBlank()) { Toast.makeText(ctx, "ID не может быть пустым", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            val list = editorOverlay.getMarkers().toMutableList()
            if (existing == null && list.any { it.id == id }) { Toast.makeText(ctx, "ID уже существует", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (!armatureExistsInExcel(id)) {
                AlertDialog.Builder(ctx)
                    .setTitle("Предупреждение")
                    .setMessage("Такой арматуры нет в списке. Добавить в список?")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Добавить") { d, _ ->
                        d.dismiss()
                        proceedApplyMarker(existing, id, page, xPdf, yPdf, size, color, comment)
                        logMsg("Арматура '$id' будет добавлена в Excel при сохранении")
                        dialog.dismiss()
                    }
                    .show()
                return@setOnClickListener
            }
            proceedApplyMarker(existing, id, page, xPdf, yPdf, size, color, comment)
            logMsg(if (existing == null) "Добавлено: $id" else "Изменено: $id; zoom=${zoomVal}")
            dialog.dismiss()
        }
    }

    private fun proceedApplyMarker(
        existing: EditorMarkerOverlayView.EditorMarkerItem?,
        id: String,
        page: Int,
        xPdf: Float,
        yPdf: Float,
        size: Float,
        color: Int,
        comment: String?
    ) {
        val list = editorOverlay.getMarkers().toMutableList()
            pushUndo()
            if (existing == null) {
                list.add(EditorMarkerOverlayView.EditorMarkerItem(id, page, xPdf, yPdf, size, size, color, id, comment))
            } else {
                val idx = list.indexOfFirst { it.id == existing.id }
                if (idx >= 0) {
                    list[idx] = list[idx].copy(
                        id = id,
                        xPdf = xPdf,
                        yPdf = yPdf,
                        wPdf = size,
                        hPdf = size,
                        color = color,
                        label = id,
                        comment = comment
                    )
                }
            }
            editorOverlay.setMarkers(list)
    }

    private fun parseColorFromMarkerType(markerType: String?): Int {
        return try {
            if (markerType != null && markerType.contains("#")) {
                val hex = markerType.substringAfter('#').substringBefore(':')
                Color.parseColor("#" + hex)
            } else Color.RED
        } catch (_: Exception) { Color.RED }
    }

    private fun parseColorFromInput(input: String): Int {
        if (input.startsWith("#")) {
            return Color.parseColor(input)
        }
        return Color.BLACK // Default color
    }

    private fun loadJsonFromCurrentSource(): MutableMap<String, Any?> {
        val useEdited = useEditedSource || toggleEditMode.isChecked
        val root = if (useEdited) editedDir() else originalsDir()
        val file = java.io.File(root, "armature_coords.json")
        if (!file.exists()) return mutableMapOf()
        val text = file.readText()
        return try {
            @Suppress("UNCHECKED_CAST")
            gson.fromJson(text, Map::class.java) as? MutableMap<String, Any?> ?: mutableMapOf()
        } catch (_: Exception) { mutableMapOf() }
    }

    private suspend fun saveToSelectedSource() {
        if (!useEditedSource) {
            withContext(Dispatchers.Main) {
                if (isAdded) Toast.makeText(requireContext(), "Недоступно в режиме оригиналов", Toast.LENGTH_SHORT).show()
            }
            return
        }
        
        withContext(Dispatchers.IO) {
            try {
                var savedItems = mutableListOf<String>()
                
                // НОВОЕ: Проверяем что открыто и сохраняем соответственно
                val jsonVisible = withContext(Dispatchers.Main) { jsonScroll.visibility == View.VISIBLE }
                val pdfVisible = withContext(Dispatchers.Main) { pdfZoomView.visibility == View.VISIBLE }
                
                // Сохраняем JSON из текстового редактора
                if (jsonVisible) {
                    val editedJson = withContext(Dispatchers.Main) { jsonEditor.text.toString() }
                    if (editedJson.isNotBlank()) {
                        // ИСПРАВЛЕНО: Всегда сохраняем в editor_out для последующей отправки
                        val targetFile = java.io.File(editedDir(), "armature_coords.json")
                        
                        // Валидация JSON
                        try {
                            gson.fromJson(editedJson, Map::class.java)
                            if (!targetFile.parentFile.exists()) targetFile.parentFile.mkdirs()
                            targetFile.writeText(editedJson)
                            savedItems.add("JSON в editor_out")
                            Log.d("EditorFragment", "JSON saved to editor_out: ${targetFile.absolutePath}")
                        } catch (e: Exception) {
                            Log.e("EditorFragment", "Invalid JSON", e)
                            withContext(Dispatchers.Main) {
                                if (isAdded) Toast.makeText(requireContext(), "Ошибка: невалидный JSON", Toast.LENGTH_LONG).show()
                            }
                            return@withContext
                        }
                    }
                }
                
                // Сохраняем метки с PDF
                if (pdfVisible && currentPdfName != null) {
            saveChangesToEditorOut()
            saveArmaturesExcelToEditorOut()
                    savedItems.add("Метки PDF")
                }
                
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        if (savedItems.isNotEmpty()) {
                            tvStatus.text = "Сохранено: ${savedItems.joinToString(", ")}"
                            Toast.makeText(requireContext(), "Сохранено! Нажмите \"Отправить\" для загрузки на сервер", Toast.LENGTH_LONG).show()
                        } else {
                            tvStatus.text = "Нечего сохранять"
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("EditorFragment", "Save error", e)
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        tvStatus.text = "Ошибка: ${e.message}"
                        Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun showSaveToOriginalsDialog() {
        if (!isAdded) return
        AlertDialog.Builder(requireContext())
            .setTitle("Вы точно хотите сохранить данные в оригиналы?")
            .setMessage("Данные будут записаны в папку оригиналов и будут использоваться офлайн без отправки на сервер (внесенные изменения будут утеряны при получении обновления с сервера).")
            .setNegativeButton("Отмена", null)
            .setNeutralButton("Сохранить в Редактируемые") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                saveChangesToEditorOut()
                saveArmaturesExcelToEditorOut()
                        withContext(Dispatchers.Main) {
                            if (isAdded) {
                                tvStatus.text = "Сохранено в редакции"
                                Toast.makeText(requireContext(), "Сохранено", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("EditorFragment", "Error saving", e)
                        withContext(Dispatchers.Main) {
                            if (isAdded) Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setPositiveButton("Сохранить") { _, _ ->
                // Явная запись в оригиналы - запускаем в корутине
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                saveChangesToOriginals()
                        withContext(Dispatchers.Main) {
                            if (isAdded) {
                                tvStatus.text = "Сохранено в оригиналы"
                                Toast.makeText(requireContext(), "Сохранено в оригиналы", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("EditorFragment", "Error saving to originals", e)
                        withContext(Dispatchers.Main) {
                            if (isAdded) {
                                tvStatus.text = "Ошибка: ${e.message}"
                                Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
            .show()
    }

    private fun saveChangesToOriginals() {
            // JSON
            val pdfName = normalizePdfName(currentPdfName)
        if (pdfName.isNullOrBlank()) {
            Log.w("EditorFragment", "No PDF opened")
            return
        }
            val baseEdited = storage.loadJson(true)
            val baseOriginal = storage.loadJson(false)
            val base = if (baseEdited.isNotEmpty()) baseEdited else baseOriginal
            val perPdf = (base[pdfName] as? MutableMap<String, Any?>) ?: mutableMapOf<String, Any?>().also { base[pdfName] = it }
            val list = editorOverlay.getMarkers()
            // Удаления
            if (deletedIds.isNotEmpty()) {
                for (id in deletedIds) perPdf.remove(id)
                deletedIds.clear()
            }
            // Upsert
            for (m in list) {
                perPdf[m.id] = mutableMapOf<String, Any?>(
                    "page" to m.page,
                    "x" to m.xPdf,
                    "y" to m.yPdf,
                    "width" to m.wPdf,
                    "height" to m.hPdf,
                    "zoom" to 3.0,
                    "label" to (m.label ?: m.id),
                    "comment" to m.comment,
                    "marker_type" to "square:${String.format("#%06X", m.color and 0xFFFFFF)}:${m.wPdf.toInt()}"
                )
            }
            storage.saveJson(base, false)

            // Excel
            val origDir = originalsDir()
            if (!origDir.exists()) origDir.mkdirs()
            val file = java.io.File(origDir, "Armatures.xlsx")
            if (!file.exists()) {
                val edited = java.io.File(editedDir(), "Armatures.xlsx")
                if (edited.exists()) {
                    edited.copyTo(file, overwrite = false)
                }
            }
            excelWriter.write(
                file,
                normalizePdfName(currentPdfName),
                editorOverlay.getMarkers(),
                deletedIds.toSet()
            )
                logMsg("Изменения сохранены в оригиналы")
            Log.d("EditorFragment", "Saved to originals successfully")
    }

    private fun saveArmaturesExcelToEditorOut() {
            val dir = editedDir()
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, "Armatures.xlsx")
            if (!file.exists()) {
                val original = java.io.File(originalsDir(), "Armatures.xlsx")
                if (original.exists()) {
                    original.copyTo(file, overwrite = false)
                }
            }
            excelWriter.write(
                file,
                normalizePdfName(currentPdfName),
                editorOverlay.getMarkers(),
                deletedIds.toSet()
            )
            logMsg("Armatures.xlsx сохранён: ${file.absolutePath}")
        Log.d("EditorFragment", "Excel saved successfully")
    }

    // Проверка наличия арматуры в Excel (колонка "Арматура").
    // Используем файл из editor_out, если он существует; иначе — из оригиналов.
    private fun armatureExistsInExcel(armatureId: String): Boolean {
        return try {
            val editedFile = java.io.File(editedDir(), "Armatures.xlsx")
            val source = if (editedFile.exists()) editedFile else java.io.File(originalsDir(), "Armatures.xlsx")
            if (!source.exists()) return false
            FileInputStream(source).use { fis ->
                val wb = XSSFWorkbook(fis)
                val sheet = wb.getSheet("Арматура") ?: run { wb.close(); return false }
                val header = sheet.getRow(0) ?: run { wb.close(); return false }
                // Ищем подходящую колонку: "Арматура" или альтернативные
                var armCol = -1
                val lastCell = header.lastCellNum.toInt().coerceAtLeast(0)
                for (i in 0..lastCell) {
                    val name = header.getCell(i)?.stringCellValue?.trim() ?: continue
                    if (name.equals("Арматура", ignoreCase = true) ||
                        name.equals("ID", ignoreCase = true) ||
                        name.equals("Обозначение", ignoreCase = true)
                    ) { armCol = i; break }
                }
                if (armCol < 0) { wb.close(); return false }
                val lastRow = sheet.lastRowNum
                var found = false
                val formatter = DataFormatter(Locale.getDefault())
                val target = normalizeArmatureId(armatureId)
                for (r in 1..lastRow) {
                    val row = sheet.getRow(r) ?: continue
                    val raw = formatter.formatCellValue(row.getCell(armCol)).trim()
                    if (raw.isNotEmpty()) {
                        val candidate = normalizeArmatureId(raw)
                        if (candidate == target) { found = true; break }
                    }
                }
                wb.close()
                found
            }
        } catch (_: Exception) { false }
    }

    private fun normalizeArmatureId(input: String): String {
        var s = input.trim()
        // Унифицируем дефисы
        s = s.replace("\u2010", "-") // hyphen
            .replace("\u2011", "-") // non-breaking hyphen
            .replace("\u2012", "-")
            .replace("\u2013", "-") // en dash
            .replace("\u2014", "-") // em dash
            .replace("\u2212", "-") // minus
        // Уберём пробелы вокруг дефиса
        s = s.replace(Regex("\\s*-\\s*"), "-")
        // Сжать множественные пробелы
        s = s.replace(Regex("\\s+"), " ")
        // Нижний регистр для сравнения
        s = s.lowercase(Locale.getDefault())
        // Приведём латинские look-alike к кириллице (частые)
        s = s
            .replace('a', 'а') // Latin a -> Cyrillic а
            .replace('e', 'е') // Latin e -> Cyrillic е
            .replace('o', 'о') // Latin o -> Cyrillic о
            .replace('p', 'р') // Latin p -> Cyrillic р
            .replace('c', 'с') // Latin c -> Cyrillic с
            .replace('x', 'х') // Latin x -> Cyrillic х
            .replace('k', 'к') // Latin k -> Cyrillic к
            .replace('m', 'м') // Latin m -> Cyrillic м
            .replace('t', 'т') // Latin t -> Cyrillic т
            .replace('y', 'у') // Latin y -> Cyrillic у
            .replace('h', 'н') // Latin h -> Cyrillic н
            .replace('b', 'в') // Latin b -> Cyrillic в
        return s
    }

    private fun logMsg(msg: String) {
        Log.d("EditorFragment", msg)
        // КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Безопасное обновление UI из любого потока
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
        tvLog.append(msg + "\n")
        tvLog.scrollTo(tvLog.text.length, tvLog.lineCount * tvLog.lineHeight)
        } else {
            tvLog.post {
                try {
                    tvLog.append(msg + "\n")
                    tvLog.scrollTo(tvLog.text.length, tvLog.lineCount * tvLog.lineHeight)
                } catch (_: Exception) {}
            }
        }
    }

    private fun applyToggleStyle(button: ToggleButton, isChecked: Boolean) {
        val bg = if (isChecked) Color.parseColor("#1976d2") else Color.parseColor("#78909C")
        button.backgroundTintList = ColorStateList.valueOf(bg)
        button.setTextColor(Color.WHITE)
    }

    private fun confirmAndUploadArmatureCoords() {
        val ctx = requireContext()
        val jsonFile = java.io.File(editedDir(), "armature_coords.json")
        val excelFile = java.io.File(editedDir(), "Armatures.xlsx")
        if (!jsonFile.exists() && !excelFile.exists()) {
            tvStatus.text = "Нет файлов для отправки в editor_out"
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            // 1) Проверяем статус admin auth
            val api = com.example.vkbookandroid.network.NetworkModule.getArmatureApiService()
            val authEnabled = try {
                val resp = api.checkAdminAuthStatus()
                resp.isSuccessful && (resp.body()?.enabled == true)
            } catch (_: Exception) { false }

            if (authEnabled) {
                // Если есть сохранённые креды и флажок — используем их молча
                val savedLogin = prefs.getString("admin_login", null)
                val savedPass = prefs.getString("admin_password", null)
                val remember = prefs.getBoolean("admin_remember", false)
                if (remember && !savedLogin.isNullOrEmpty() && !savedPass.isNullOrEmpty()) {
                    com.example.vkbookandroid.editor.EditorUploadState.adminLogin = savedLogin
                    com.example.vkbookandroid.editor.EditorUploadState.adminPassword = savedPass
                    uploadEditedFiles(jsonFile.takeIf { it.exists() }, excelFile.takeIf { it.exists() })
                    return@launch
                }
                // Иначе — показать диалог
                showAdminCredentialsDialog(
                    onSubmit = { login, pass, rememberMe ->
                        com.example.vkbookandroid.editor.EditorUploadState.adminLogin = login
                        com.example.vkbookandroid.editor.EditorUploadState.adminPassword = pass
                        if (rememberMe) {
                            prefs.edit()
                                .putString("admin_login", login)
                                .putString("admin_password", pass)
                                .putBoolean("admin_remember", true)
                                .apply()
                        } else {
                            prefs.edit()
                                .remove("admin_login")
                                .remove("admin_password")
                                .putBoolean("admin_remember", false)
                                .apply()
                        }
                        uploadEditedFiles(jsonFile.takeIf { it.exists() }, excelFile.takeIf { it.exists() })
                    }
                )
            } else {
                // 3) Без пароля — просто отправляем
                com.example.vkbookandroid.editor.EditorUploadState.adminLogin = null
                com.example.vkbookandroid.editor.EditorUploadState.adminPassword = null
        AlertDialog.Builder(ctx)
            .setTitle("Отправка обновлений")
                    .setMessage("Будут отправлены armature_coords.json и Armatures.xlsx (если есть). Продолжить?")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Отправить") { _, _ ->
                        uploadEditedFiles(jsonFile.takeIf { it.exists() }, excelFile.takeIf { it.exists() })
            }
            .show()
            }
        }
    }

    private fun showAdminCredentialsDialog(onSubmit: (String, String, Boolean) -> Unit) {
        val ctx = requireContext()
        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 12, 40, 0)
        }
        val loginInput = EditText(ctx).apply { hint = "Логин" }
        val passInput = EditText(ctx).apply { hint = "Пароль"; inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
        val rememberCb = CheckBox(ctx).apply { text = "Запомнить"; isChecked = prefs.getBoolean("admin_remember", false) }
        val errorView = TextView(ctx).apply { setTextColor(Color.RED); text = "" }
        // Префилл, если ранее сохраняли (но флажок может быть снят)
        prefs.getString("admin_login", null)?.let { loginInput.setText(it) }
        prefs.getString("admin_password", null)?.let { passInput.setText(it) }
        container.addView(loginInput)
        container.addView(passInput)
        container.addView(rememberCb)
        container.addView(errorView)

        val dialog = AlertDialog.Builder(ctx)
            .setTitle("Требуется авторизация администратора")
            .setView(container)
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Отправить", null)
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val login = loginInput.text.toString().trim()
            val pass = passInput.text.toString().trim()
            val remember = rememberCb.isChecked
            if (login.isEmpty() || pass.isEmpty()) {
                errorView.text = "Укажите логин и пароль"
                return@setOnClickListener
            }
            // Валидация произойдёт при upload: если сервер вернёт 401/403 — покажем ошибку
            dialog.dismiss()
            onSubmit(login, pass, remember)
        }
    }

    private fun uploadEditedFiles(jsonFile: java.io.File?, excelFile: java.io.File?) {
        btnUpload.isEnabled = false
        tvStatus.text = "Отправка на сервер..."
        viewLifecycleOwner.lifecycleScope.launch {
            val report = uploader.uploadAll(jsonFile, excelFile, parallel = true)
            btnUpload.isEnabled = true
            tvStatus.text = "Отправка завершена: ${report.toSummary()}"
            logMsg(tvStatus.text.toString())
        }
    }
}



