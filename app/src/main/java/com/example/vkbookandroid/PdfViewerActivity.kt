package com.example.vkbookandroid

import android.os.Bundle
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.widget.ImageView
import android.widget.Toast
import android.graphics.Matrix
import android.opengl.GLES10
import android.util.Log
import com.google.gson.Gson
import android.os.SystemClock
import com.google.gson.reflect.TypeToken
import org.example.pult.model.ArmatureCoords
import java.io.InputStream
import android.net.Uri
// Удалены неиспользуемые импорты: DocumentsContract, BufferedReader, InputStreamReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.vkbookandroid.model.ArmatureMarker
import com.example.vkbookandroid.model.ArmatureCoordsData
import com.example.vkbookandroid.repository.ArmatureRepository
import com.example.vkbookandroid.network.NetworkModule

class PdfViewerActivity : AppCompatActivity() {
    
    private lateinit var imageView: ImageView
    private var renderedBitmap: android.graphics.Bitmap? = null
    private var markers: Map<String, Map<String, ArmatureCoords>> = emptyMap()
    private var newFormatMarkers: List<ArmatureMarker> = emptyList()
    private val gson = Gson()
    private var currentPdfName: String? = null
    private lateinit var armatureRepository: ArmatureRepository
    private var pdfToBitmapScale: Float = 1f
    private var cachePdfFile: java.io.File? = null
    private var lastPageIndex: Int = 0
    private var lastPageW: Int = 0
    private var lastPageH: Int = 0
    private var isRerendering: Boolean = false
    private var pendingReRender: Runnable? = null
    private var maxQualityButton: android.widget.ImageButton? = null
    private var lastReRenderAtMs: Long = 0L
    private var isViewportPinned: Boolean = false
    private var restoreFullButton: android.widget.ImageButton? = null
    private val activityJob = SupervisorJob()
    private val uiScope = CoroutineScope(Dispatchers.Main + activityJob)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)
        // Отключаем звуковые эффекты кликов в активити
        findViewById<android.view.View>(android.R.id.content)?.isSoundEffectsEnabled = false
        
        // Инициализируем репозиторий
        armatureRepository = ArmatureRepository(this, NetworkModule.getArmatureApiService())
        
        imageView = findViewById(R.id.pdfImageView)
        findViewById<android.widget.ImageButton>(R.id.closeButton)?.setOnClickListener { finish() }
        
        
        maxQualityButton = findViewById(R.id.buttonMaxQuality)
        maxQualityButton?.setOnClickListener { renderAtMaxQuality() }
        maxQualityButton?.visibility = android.view.View.GONE
        // Пересчёт видимости после layout, чтобы избежать раннего скрытия на некоторых устройствах
        imageView.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (imageView.width > 0 && imageView.height > 0) {
                    updateMaxQualityButtonVisibility()
                    imageView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            }
        })
        restoreFullButton = findViewById(R.id.buttonRestoreFull)
        restoreFullButton?.setOnClickListener { restoreFullScheme() }
        restoreFullButton?.visibility = android.view.View.GONE

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.buttonZoomIn)?.setOnClickListener { adjustZoom(1.25f) }
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.buttonZoomOut)?.setOnClickListener { adjustZoom(0.8f) }
        
        val pdfPath = intent.getStringExtra("pdf_path")
        val pdfUri = intent.getStringExtra("pdf_uri")
        val designation = intent.getStringExtra("designation")
        
        loadMarkers()
        (imageView as? ZoomableImageView)?.let { iv ->
            iv.onScaleChanged = { scale ->
                if (!isViewportPinned) {
                    maybeScheduleQualityRerender(scale)
                }
            }
            iv.onMatrixChanged = { m ->
                // обновляем маркер при любой трансформации
                (findViewById<MarkerOverlayView>(R.id.markerOverlay))?.onImageMatrixChanged(m)
            }
        }
        when {
            pdfUri != null -> loadSchemeFromUri(Uri.parse(pdfUri), designation)
            pdfPath != null -> loadSchemeFromAssets(pdfPath, designation)
            else -> Unit
        }
    }

    override fun onDestroy() {
        // Освобождаем память от bitmap'ов
        renderedBitmap?.recycle()
        renderedBitmap = null
        
        // Очищаем кэш PDF файла
        cachePdfFile?.delete()
        cachePdfFile = null
        
        super.onDestroy()
        activityJob.cancel()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // После смены ориентации перерисуем текущую страницу
        (imageView as? ZoomableImageView)?.resetMatrix()
        // Отложенно пересчитаем видимость, когда вью примет новые размеры
        imageView.post { updateMaxQualityButtonVisibility() }
    }
    
    private fun loadMarkers() {
        uiScope.launch {
            try {
                Log.d("PdfViewerActivity", "=== LOADING MARKERS ===")
                // Загружаем только из filesDir (без автоматической синхронизации с сервером)
                markers = armatureRepository.loadMarkersFromFilesDir()
                Log.d("PdfViewerActivity", "Loaded markers from filesDir: ${markers.size} PDF files")
                markers.forEach { (pdfName, pdfMarkers) ->
                    Log.d("PdfViewerActivity", "  PDF: $pdfName, markers: ${pdfMarkers.size}")
                    pdfMarkers.forEach { (markerId, coords) ->
                        Log.d("PdfViewerActivity", "    Marker: $markerId at (${coords.x}, ${coords.y})")
                    }
                }
                newFormatMarkers = armatureRepository.convertOldFormatToNew(markers)
                Log.d("PdfViewerActivity", "Converted to new format: ${newFormatMarkers.size} markers")
                
                Log.d("PdfViewerActivity", "Markers loaded from local files only (no server sync)")
            } catch (e: Exception) {
                Log.e("PdfViewerActivity", "Error loading markers from filesDir", e)
                // Если даже filesDir не работает - пустые маркеры
                markers = emptyMap()
                newFormatMarkers = emptyList()
            }
        }
    }
    
    
    private fun loadSchemeFromAssets(pdfPath: String, designation: String?) {
        uiScope.launch {
            try {
                val fileName = pdfPath.substringAfterLast('/')
                currentPdfName = fileName
                Log.d("PdfViewerActivity", "=== LOADING SCHEME ===")
                Log.d("PdfViewerActivity", "Requested path: '$pdfPath'")
                Log.d("PdfViewerActivity", "Extracted filename: '$fileName'")
                Log.d("PdfViewerActivity", "Designation: '$designation'")
                
                val inputStream: InputStream = withContext(Dispatchers.IO) {
                    // Используем FileProvider для доступа к файлам
                    val fileProvider = com.example.vkbookandroid.FileProvider(this@PdfViewerActivity)
                    Log.d("PdfViewerActivity", "FileProvider created, attempting to open: '$pdfPath'")
                    fileProvider.open(pdfPath)
                }
                Log.d("PdfViewerActivity", "File opened successfully, rendering...")
                renderFirstPageToImage(inputStream, designation)
            } catch (e: Exception) {
                Log.e("PdfViewerActivity", "Error loading scheme: '$pdfPath'", e)
                e.printStackTrace()
                Toast.makeText(this@PdfViewerActivity, "Ошибка загрузки схемы: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadSchemeFromUri(uri: Uri, designation: String?) {
        uiScope.launch {
            try {
                currentPdfName = uri.lastPathSegment?.substringAfterLast('/')?.substringAfter(":")
                val input = withContext(Dispatchers.IO) { contentResolver.openInputStream(uri) }
                if (input != null) {
                    renderFirstPageToImage(input, designation)
                } else {
                    Toast.makeText(this@PdfViewerActivity, "Не удалось открыть файл", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun renderFirstPageToImage(input: InputStream, designation: String?) {
        uiScope.launch {
            val cacheFile = java.io.File(cacheDir, "tmp_scheme.pdf")
            var bmp: android.graphics.Bitmap? = null
            var pageW = 0
            var pageH = 0
            var pageIndex = 0
            withContext(Dispatchers.IO) {
                try {
                    cacheFile.outputStream().use { out -> input.use { it.copyTo(out) } }
                    cachePdfFile = cacheFile
                    val pfd = android.os.ParcelFileDescriptor.open(cacheFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = android.graphics.pdf.PdfRenderer(pfd)
                    try {
                        if (renderer.pageCount <= 0) return@withContext
                        pageIndex = run {
                            val pdfName = currentPdfName
                            val coords = if (designation != null && pdfName != null) {
                                markers[pdfName]?.get(designation)
                            } else null
                            ((coords?.page ?: 1) - 1).coerceIn(0, renderer.pageCount - 1)
                        }
                        val page = renderer.openPage(pageIndex)
                        pageW = page.width
                        pageH = page.height
                        val dm = resources.displayMetrics
                        val sideCap = 8192
                        val isSmallPage = pageW <= dm.widthPixels && pageH <= dm.heightPixels
                        val screenMultiplier = if (isSmallPage) 2 else 3
                        val maxW = kotlin.math.min(dm.widthPixels * screenMultiplier, sideCap)
                        val maxH = kotlin.math.min(dm.heightPixels * screenMultiplier, sideCap)
                        var scale = kotlin.math.min(maxW / pageW.toFloat(), maxH / pageH.toFloat())
                        if (scale <= 0f || !scale.isFinite()) scale = 1f
                        var localBmp: android.graphics.Bitmap? = null
                        var attempts = 0
                        while (attempts < 5 && localBmp == null) {
                            val desiredW = kotlin.math.max(1, (pageW * scale).toInt())
                            val desiredH = kotlin.math.max(1, (pageH * scale).toInt())
                            val dims = clampDimsByMemoryBudget(desiredW, desiredH, 1, 1)
                            val bw = dims.first
                            val bh = dims.second
                            try {
                                localBmp = android.graphics.Bitmap.createBitmap(bw, bh, android.graphics.Bitmap.Config.ARGB_8888)
                            } catch (_: Throwable) {
                                localBmp = null
                            }
                            if (localBmp == null) {
                                scale *= 0.75f
                                attempts++
                            }
                        }
                        if (localBmp != null) {
                            page.render(localBmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                        page.close()
                        bmp = localBmp
                    } finally {
                        try { renderer.close() } catch (_: Throwable) {}
                        try { pfd.close() } catch (_: Throwable) {}
                    }
        } catch (e: Exception) {
            e.printStackTrace()
                }
            }
            if (bmp == null) {
                Toast.makeText(this@PdfViewerActivity, "Не удалось отрисовать страницу: недостаточно памяти", Toast.LENGTH_LONG).show()
                return@launch
            }
            lastPageIndex = pageIndex
            lastPageW = pageW
            lastPageH = pageH
            pdfToBitmapScale = bmp!!.width / pageW.toFloat()
            
            // Освобождаем старый bitmap перед назначением нового
            renderedBitmap?.recycle()
            renderedBitmap = bmp
            imageView.setImageBitmap(bmp)
            // Сначала центрируемся и рисуем маркер синхронно, затем UI-пересчёты
            val ivZoom = imageView as? ZoomableImageView
            if (designation != null && ivZoom != null) {
                val pdfName = currentPdfName
                Log.d("PdfViewerActivity", "Looking for marker: designation='$designation', pdfName='$pdfName'")
                Log.d("PdfViewerActivity", "Available new format markers: ${newFormatMarkers.map { "id='${it.id}', pdf='${it.pdf}', label='${it.label}'" }}")
                Log.d("PdfViewerActivity", "Available old format markers: ${markers.keys}")
                
                // Пытаемся найти маркер в новом формате
                val newMarker = newFormatMarkers.find { it.id == designation && it.pdf == pdfName }
                if (newMarker != null) {
                    Log.d("PdfViewerActivity", "Found new format marker: $newMarker")
                    // Используем новый формат с цветами и комментариями
                    focusOnNewMarker(newMarker)
                    val overlay = findViewById<MarkerOverlayView>(R.id.markerOverlay)
                    overlay?.setMarkerInPdfUnits(
                        newMarker.x.toFloat(),
                        newMarker.y.toFloat(),
                        newMarker.size.toFloat(),
                        newMarker.size.toFloat(),
                        pdfToBitmapScale,
                        ivZoom.getImageMatrixCopy(),
                        label = newMarker.label,
                        color = newMarker.getColorInt(),
                        comment = newMarker.comment
                    )
                } else {
                    // Fallback: используем старый формат
                    Log.d("PdfViewerActivity", "Trying old format: pdfName=$pdfName, designation=$designation")
                    Log.d("PdfViewerActivity", "Available old markers: ${markers.keys}")
                    val coords = if (pdfName != null) markers[pdfName]?.get(designation) else null
                    if (coords != null) {
                        Log.d("PdfViewerActivity", "Found old format marker: $coords")
                        focusOn(coords)
                        val overlay = findViewById<MarkerOverlayView>(R.id.markerOverlay)
                        overlay?.setMarkerInPdfUnits(
                            coords.x.toFloat(),
                            coords.y.toFloat(),
                            coords.width.toFloat(),
                            coords.height.toFloat(),
                            pdfToBitmapScale,
                            ivZoom.getImageMatrixCopy(),
                            label = designation,
                            color = parseMarkerColor(coords),
                            comment = coords.label
                        )
                    } else {
                        findViewById<MarkerOverlayView>(R.id.markerOverlay)?.clearMarker()
                    }
                }
            } else {
                findViewById<MarkerOverlayView>(R.id.markerOverlay)?.clearMarker()
            }
            imageView.post { updateMaxQualityButtonVisibility() }
        }
    }

    private fun updateMaxQualityButtonVisibility() {
        val btn = maxQualityButton ?: return
        val bmp = renderedBitmap ?: run {
            // Если изображения ещё нет, отложим пересчёт на конец очереди
            if (imageView.width == 0 || imageView.height == 0) {
                imageView.post { updateMaxQualityButtonVisibility() }
            }
            btn.visibility = android.view.View.GONE
            return
        }
        if (lastPageW <= 0 || lastPageH <= 0) {
            if (imageView.width == 0 || imageView.height == 0) {
                imageView.post { updateMaxQualityButtonVisibility() }
            }
            btn.visibility = android.view.View.GONE
            return
        }
        if (isViewportPinned) { btn.visibility = android.view.View.GONE; return }
        // Скрываем кнопку для маленьких схем (нет смысла повышать качество)
        if (isSmallCurrentPage()) { btn.visibility = android.view.View.GONE; return }

        // Оценим реальный апгрейд с учетом всех ограничений (память/Canvas/GL)
        val theoretical = computeTheoreticalMaxDims(lastPageW, lastPageH)
        val boost = if (isVeryLargeCurrentPage()) 2.2f else if (isLargeCurrentPage()) 1.8f else 1.6f
        val boostedW = (theoretical.first * boost).toInt()
        val boostedH = (theoretical.second * boost).toInt()
        val clamped = clampDimsByMemoryBudget(
            boostedW,
            boostedH,
            bmp.width,
            bmp.height
        )
        val widthGain = clamped.first - bmp.width
        val heightGain = clamped.second - bmp.height
        // Смягчённые пороги: проценты + dp (зависят от плотности)
        val density = resources.displayMetrics.density
        val minGainDp = if (isLargeCurrentPage() || isVeryLargeCurrentPage()) 24 else 32
        val minGainPxDpAware = (minGainDp * density).toInt()
        val minGainRatio = if (isLargeCurrentPage() || isVeryLargeCurrentPage()) 0.10 else 0.14
        val needW = widthGain >= kotlin.math.max(minGainPxDpAware, (bmp.width * minGainRatio).toInt())
        val needH = heightGain >= kotlin.math.max(minGainPxDpAware, (bmp.height * minGainRatio).toInt())
        val canUpgrade = needW || needH
        btn.visibility = if (canUpgrade) android.view.View.VISIBLE else android.view.View.GONE
    }

    // Теоретический максимум по стороне без учёта памяти (для принятия решения о видимости кнопки)
    private fun computeTheoreticalMaxDims(pageW: Int, pageH: Int): Pair<Int, Int> {
        val capSide = 8192
        var targetW = capSide
        var targetH = (pageH * (targetW / pageW.toFloat())).toInt()
        if (targetH > capSide) {
            targetH = capSide
            targetW = (pageW * (targetH / pageH.toFloat())).toInt()
        }
        return Pair(targetW, targetH)
    }

    // Подгон размеров под реальные бюджеты: память процесса, безопасный лимит Canvas по байтам и GL_MAX_TEXTURE_SIZE
    private fun clampDimsByMemoryBudget(targetWIn: Int, targetHIn: Int, minW: Int, minH: Int): Pair<Int, Int> {
        var targetW = targetWIn
        var targetH = targetHIn

        // 1) Ограничение по максимальной стороне (GL_MAX_TEXTURE_SIZE и общий здравый предел)
        val glMax = getMaxTextureSize().coerceAtLeast(8192)
        val sideCap = 8192.coerceAtMost(glMax)
        val scaleSide = kotlin.math.min(1.0, kotlin.math.min(sideCap / targetW.toDouble(), sideCap / targetH.toDouble()))
        if (scaleSide < 1.0) {
            targetW = kotlin.math.max(1, (targetW * scaleSide).toInt())
            targetH = kotlin.math.max(1, (targetH * scaleSide).toInt())
        }

        // 2) Ограничение по байтам: берём минимум из доступной памяти процесса и безопасной планки Canvas
        val allowedMemBytes = computeMemoryBudgetBytes()
        val canvasSafeCapBytes = 32.0 * 1024 * 1024 // ~32MB, чтобы не падать с "Canvas: too large bitmap"
        val allowedBytes = kotlin.math.min(allowedMemBytes, canvasSafeCapBytes)
        val maxPixels = allowedBytes / 4.0
        val curPixels = targetW.toDouble() * targetH.toDouble()
        if (curPixels > maxPixels && curPixels > 0) {
            val factor = kotlin.math.sqrt(maxPixels / curPixels)
            targetW = (targetW * factor).toInt().coerceAtLeast(1)
            targetH = (targetH * factor).toInt().coerceAtLeast(1)
        }

        // 3) Не опускаемся ниже минимально запрошенных размеров
        targetW = kotlin.math.max(minW, targetW)
        targetH = kotlin.math.max(minH, targetH)
        return Pair(targetW, targetH)
    }

    private fun isSmallCurrentPage(): Boolean {
        val dm = resources.displayMetrics
        return lastPageW <= dm.widthPixels && lastPageH <= dm.heightPixels
    }

    private fun isLargeCurrentPage(): Boolean {
        val dm = resources.displayMetrics
        val screenArea = dm.widthPixels.toLong() * dm.heightPixels.toLong()
        val pageArea = lastPageW.toLong() * lastPageH.toLong()
        return pageArea >= 4L * screenArea || lastPageW >= dm.widthPixels * 2 || lastPageH >= dm.heightPixels * 2
    }

    private fun isVeryLargeCurrentPage(): Boolean {
        val dm = resources.displayMetrics
        val screenArea = dm.widthPixels.toLong() * dm.heightPixels.toLong()
        val pageArea = lastPageW.toLong() * lastPageH.toLong()
        return pageArea >= 8L * screenArea || lastPageW >= dm.widthPixels * 3 || lastPageH >= dm.heightPixels * 3
    }

    private fun getMaxTextureSize(): Int {
        return try {
            val maxSize = IntArray(1)
            GLES10.glGetIntegerv(GLES10.GL_MAX_TEXTURE_SIZE, maxSize, 0)
            maxSize[0].takeIf { it > 0 } ?: 8192
        } catch (_: Throwable) {
            8192
        }
    }

    private fun computeMemoryBudgetBytes(): Double {
        val rt = Runtime.getRuntime()
        val used = rt.totalMemory() - rt.freeMemory()
        val available = rt.maxMemory() - used
        // Берём до 40% доступного сейчас, с жёсткими границами
        val cap = 128.0 * 1024 * 1024 // 128MB
        val floor = 24.0 * 1024 * 1024 // не меньше 24MB
        val proposed = available.toDouble() * 0.4
        return kotlin.math.max(floor, kotlin.math.min(cap, proposed))
    }

    private fun maybeScheduleQualityRerender(currentScale: Float) {
        val iv = imageView as? ZoomableImageView ?: return
        val bmp = renderedBitmap ?: return
        if (isRerendering) return
        if (isSmallCurrentPage()) return
        // Не чаще, чем раз в 600мс
        val now = SystemClock.uptimeMillis()
        if (now - lastReRenderAtMs < 600) return

        val ratio = (bmp.width * currentScale) / iv.width.toFloat().coerceAtLeast(1f)
        // Перерисовывать есть смысл только при достаточно сильном зуме и недостаточной плотности
        if (currentScale < 2f || ratio >= 2.5f) return

        // Оценим потенциальный апгрейд: если после клампа прирост <15% и <256px — не рендерим
        if (lastPageW > 0 && lastPageH > 0) {
            val desiredW = kotlin.math.max(bmp.width + 1, (3f * iv.width / currentScale).toInt())
            val desiredH = (lastPageH * (desiredW / lastPageW.toFloat())).toInt()
            val theo = computeTheoreticalMaxDims(lastPageW, lastPageH)
            val dims = clampDimsByMemoryBudget(
                theo.first,
                (lastPageH * (theo.first / lastPageW.toFloat())).toInt().coerceAtMost(theo.second),
                desiredW,
                desiredH
            )
            val gainW = dims.first - bmp.width
            val gainH = dims.second - bmp.height
            val enoughGain = gainW >= kotlin.math.max(256, (bmp.width * 0.15f).toInt()) ||
                    gainH >= kotlin.math.max(256, (bmp.height * 0.15f).toInt())
            if (!enoughGain) return
        }

        pendingReRender?.let { iv.removeCallbacks(it) }
        val r = Runnable {
            try { reRenderForQuality(currentScale) } catch (e: Throwable) { e.printStackTrace() }
        }
        pendingReRender = r
        iv.postDelayed(r, 300)
    }

    private fun reRenderForQuality(currentScale: Float) {
        val iv = imageView as? ZoomableImageView ?: return
        val oldBmp = renderedBitmap ?: return
        val cache = cachePdfFile ?: return
        if (lastPageW <= 0 || lastPageH <= 0) return
        isRerendering = true
        val desiredBmpWidth = kotlin.math.max(oldBmp.width + 1, (3f * iv.width / currentScale).toInt())
        // Требуем хотя бы ~20% прироста, чтобы избежать частых малополезных перерисовок
        if (desiredBmpWidth <= oldBmp.width * 12 / 10) {
            isRerendering = false
            return
        }
        val pfd = android.os.ParcelFileDescriptor.open(cache, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = android.graphics.pdf.PdfRenderer(pfd)
        try {
            val page = renderer.openPage(lastPageIndex)
            val pageW = page.width
            val pageH = page.height
            val theo = computeTheoreticalMaxDims(pageW, pageH)
            val desiredH = (pageH * (desiredBmpWidth / pageW.toFloat())).toInt()
            val dims = clampDimsByMemoryBudget(
                theo.first,
                (pageH * (theo.first / pageW.toFloat())).toInt().coerceAtMost(theo.second),
                desiredBmpWidth,
                desiredH
            )
            var targetW = dims.first
            var targetH = dims.second
            var bmp: android.graphics.Bitmap? = null
            var attempts = 0
            val centerOld = getBitmapCenterFromView(iv)
            while (attempts < 5 && bmp == null) {
                try {
                    bmp = android.graphics.Bitmap.createBitmap(targetW, targetH, android.graphics.Bitmap.Config.ARGB_8888)
                } catch (_: Throwable) {
                    bmp = null
                }
                if (bmp == null) {
                    targetW = kotlin.math.max(oldBmp.width, (targetW * 0.85f).toInt())
                    targetH = kotlin.math.max(oldBmp.height, (targetH * 0.85f).toInt())
                    attempts++
                }
            }
            if (bmp == null) return
            page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            page.close()
            val ratioNew = bmp.width / oldBmp.width.toFloat()
            renderedBitmap = bmp
            imageView.setImageBitmap(bmp)
            pdfToBitmapScale = bmp.width / pageW.toFloat()
            val centerXNew = centerOld.first * ratioNew
            val centerYNew = centerOld.second * ratioNew
            iv.setScaleAndCenter(currentScale, centerXNew, centerYNew)
            try { oldBmp.recycle() } catch (_: Throwable) {}
            updateMaxQualityButtonVisibility()
            lastReRenderAtMs = SystemClock.uptimeMillis()
            // Переустановим маркер с новым масштабом битмапа
            val designation = intent.getStringExtra("designation")
            val ivZoom = imageView as? ZoomableImageView
            if (designation != null && ivZoom != null) {
                val coords = markers[currentPdfName]?.get(designation)
                if (coords != null) {
                    val overlay = findViewById<MarkerOverlayView>(R.id.markerOverlay)
                    overlay?.setMarkerInPdfUnits(
                        coords.x.toFloat(),
                        coords.y.toFloat(),
                        coords.width.toFloat(),
                        coords.height.toFloat(),
                        pdfToBitmapScale,
                        ivZoom.getImageMatrixCopy(),
                        label = designation,
                        color = parseMarkerColor(coords),
                        comment = coords.label
                    )
                }
            }
        } finally {
            try { renderer.close() } catch (_: Throwable) {}
            try { pfd.close() } catch (_: Throwable) {}
            isRerendering = false
        }
    }

    private fun getBitmapCenterFromView(iv: ZoomableImageView): Pair<Float, Float> {
        val m: Matrix = (iv.getImageMatrixCopy())
        val inv = Matrix()
        m.invert(inv)
        val pts = floatArrayOf(iv.width / 2f, iv.height / 2f)
        inv.mapPoints(pts)
        return Pair(pts[0], pts[1])
    }

    private fun renderAtMaxQuality() {
        val iv = imageView as? ZoomableImageView ?: return
        val oldBmp = renderedBitmap ?: return
        val cache = cachePdfFile ?: return
        if (lastPageW <= 0 || lastPageH <= 0) return
        if (isSmallCurrentPage()) { Toast.makeText(this, "Уже максимальное качество", Toast.LENGTH_SHORT).show(); return }
        if (isViewportPinned) { Toast.makeText(this, "Уже максимальное качество", Toast.LENGTH_SHORT).show(); return }
        if (isRerendering) return
        isRerendering = true
        val centerOld = getBitmapCenterFromView(iv)
        val pfd = android.os.ParcelFileDescriptor.open(cache, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = android.graphics.pdf.PdfRenderer(pfd)
        try {
            val page = renderer.openPage(lastPageIndex)
            val pageW = page.width
            val pageH = page.height
            val theoretical = computeTheoreticalMaxDims(pageW, pageH)
            val boost = if (isLargeCurrentPage()) 1.8f else 1.6f
            // Усиливаем рендер для больших схем
            var targetW = (theoretical.first * boost).toInt()
            var targetH = (pageH * (targetW / pageW.toFloat())).toInt().coerceAtMost((theoretical.second * boost).toInt())
            // Минимально требуемый прирост: ~10% от текущего, но не менее 256px
            val minW = kotlin.math.max((oldBmp.width * 11) / 10, oldBmp.width + 256)
            val minH = (pageH * (minW / pageW.toFloat())).toInt()
            val clamped = clampDimsByMemoryBudget(targetW, targetH, minW, minH)
            targetW = clamped.first
            targetH = clamped.second
            val minGainPx = 256
            val minGainRatio = 0.10
            val enoughGain = (targetW - oldBmp.width) >= kotlin.math.max(minGainPx, (oldBmp.width * minGainRatio).toInt()) ||
                    (targetH - oldBmp.height) >= kotlin.math.max(minGainPx, (oldBmp.height * minGainRatio).toInt())
            if (!enoughGain) {
                Toast.makeText(this, "Уже максимальное качество", Toast.LENGTH_SHORT).show()
                return
            }
            var bmp: android.graphics.Bitmap? = null
            var attempts = 0
            while (attempts < 5 && bmp == null) {
                try {
                    bmp = android.graphics.Bitmap.createBitmap(targetW, targetH, android.graphics.Bitmap.Config.ARGB_8888)
                } catch (_: Throwable) {
                    bmp = null
                }
                if (bmp == null) {
                    targetW = (targetW * 0.85f).toInt().coerceAtLeast(oldBmp.width)
                    targetH = (targetH * 0.85f).toInt().coerceAtLeast(oldBmp.height)
                    attempts++
                }
            }
            if (bmp == null) {
                Toast.makeText(this, "Ограничено памятью устройства", Toast.LENGTH_SHORT).show()
                return
            }
            page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            page.close()
            val ratioNew = bmp.width / oldBmp.width.toFloat()
            renderedBitmap = bmp
            imageView.setImageBitmap(bmp)
            pdfToBitmapScale = bmp.width / pageW.toFloat()
            val centerXNew = centerOld.first * ratioNew
            val centerYNew = centerOld.second * ratioNew
            iv.setScaleAndCenter(iv.getCurrentScale(), centerXNew, centerYNew)
            Toast.makeText(this, "Рендеринг максимального качества выполнен", Toast.LENGTH_SHORT).show()
            try { oldBmp.recycle() } catch (_: Throwable) {}
            updateMaxQualityButtonVisibility()
            // Вместо наложения: заменим изображение на высококачественный кроп видимой области
            try {
                if (renderViewportStandaloneHighQuality()) {
                    restoreFullButton?.visibility = android.view.View.VISIBLE
                    // В режиме pinned маркер скрываем, чтобы он не отставал от тайла
                    findViewById<MarkerOverlayView>(R.id.markerOverlay)?.clearMarker()
                }
            } catch (_: Throwable) {}
        } finally {
            try { renderer.close() } catch (_: Throwable) {}
            try { pfd.close() } catch (_: Throwable) {}
            isRerendering = false
        }
    }

    // Локальное усиление читаемости: перерисовываем видимую область с большим суперсэмплингом и
    // подменяем соответствующий участок текущего битмапа отмасштабированным тайлом.
    private fun enhanceViewportWithSupersampling(): Boolean {
        val iv = imageView as? ZoomableImageView ?: return false
        val baseBmp = renderedBitmap ?: return false
        val cache = cachePdfFile ?: return false
        if (!isLargeCurrentPage() && !isVeryLargeCurrentPage()) return false

        // Определяем видимую область в координатах битмапа
        val m: Matrix = iv.getImageMatrixCopy()
        val inv = Matrix()
        if (!m.invert(inv)) return false
        val pts = floatArrayOf(0f, 0f, iv.width.toFloat(), iv.height.toFloat())
        inv.mapPoints(pts)
        var leftF = kotlin.math.min(pts[0], pts[2])
        var topF = kotlin.math.min(pts[1], pts[3])
        var rightF = kotlin.math.max(pts[0], pts[2])
        var bottomF = kotlin.math.max(pts[1], pts[3])
        // Клип по границам битмапа, с плавающей точкой для исключения смещения при рисовании
        leftF = leftF.coerceAtLeast(0f)
        topF = topF.coerceAtLeast(0f)
        rightF = rightF.coerceAtMost(baseBmp.width.toFloat())
        bottomF = bottomF.coerceAtMost(baseBmp.height.toFloat())
        if (rightF - leftF < 32f || bottomF - topF < 32f) return false

        // Немного расширим область (по 10%), сохранив float-точность
        val padXf = (rightF - leftF) * 0.10f
        val padYf = (bottomF - topF) * 0.10f
        leftF = (leftF - padXf).coerceAtLeast(0f)
        topF = (topF - padYf).coerceAtLeast(0f)
        rightF = (rightF + padXf).coerceAtMost(baseBmp.width.toFloat())
        bottomF = (bottomF + padYf).coerceAtMost(baseBmp.height.toFloat())

        val regionW = kotlin.math.max(1f, rightF - leftF)
        val regionH = kotlin.math.max(1f, bottomF - topF)
        val superFactorDesired = if (isVeryLargeCurrentPage()) 3.0f else 2.0f

        // Кламп размеров тайла по бюджетам
        val desiredW = kotlin.math.max(64, (regionW * superFactorDesired).toInt())
        val desiredH = kotlin.math.max(64, (regionH * superFactorDesired).toInt())
        val dims = clampDimsByMemoryBudget(desiredW, desiredH, 64, 64)
        val superFactor = kotlin.math.min(dims.first / regionW, dims.second / regionH).coerceAtLeast(1f)
        if (superFactor <= 1.01f) return false

        val pfd = android.os.ParcelFileDescriptor.open(cache, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = android.graphics.pdf.PdfRenderer(pfd)
        try {
            val page = renderer.openPage(lastPageIndex)
            val scale = pdfToBitmapScale * superFactor
            val tileW = (regionW * superFactor).toInt().coerceAtLeast(1)
            val tileH = (regionH * superFactor).toInt().coerceAtLeast(1)
            var tile: android.graphics.Bitmap? = null
            var attempts = 0
            while (attempts < 3 && tile == null) {
                try {
                    tile = android.graphics.Bitmap.createBitmap(tileW, tileH, android.graphics.Bitmap.Config.ARGB_8888)
                } catch (_: Throwable) {
                    tile = null
                }
                if (tile == null) {
                    // Понижаем суперфактор
                    val sf = superFactor * 0.85f
                    if (sf <= 1.01f) return false
                    // Обновим локальные размеры для следующей попытки
                    // (без пересчёта clampDims для простоты)
                    attempts++
                    continue
                }
            }
            if (tile == null) return false

            val matrix = Matrix()
            val clipLeftPage = leftF / pdfToBitmapScale
            val clipTopPage = topF / pdfToBitmapScale
            matrix.setScale(scale, scale)
            matrix.postTranslate(-clipLeftPage * scale, -clipTopPage * scale)
            page.render(tile, null, matrix, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            page.close()

            // Подменяем участок напрямую с ресемплингом по RectF для идеального совмещения
            val composed = baseBmp.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(composed)
            val dst = android.graphics.RectF(leftF, topF, rightF, bottomF)
            val paint = Paint().apply {
                isFilterBitmap = true
                isDither = true
                isAntiAlias = true
            }
            canvas.drawBitmap(tile, null, dst, paint)
            try { tile.recycle() } catch (_: Throwable) {}

            renderedBitmap = composed
            imageView.setImageBitmap(composed)
            // Размер битмапа не менялся — зум/центр сохраняются автоматически
            try { baseBmp.recycle() } catch (_: Throwable) {}
            return true
        } finally {
            try { renderer.close() } catch (_: Throwable) {}
            try { pfd.close() } catch (_: Throwable) {}
        }
    }

    // Рендерит только видимую область страницы в точном размере вью без последующего рескейла.
    // Итог: отсутствует смещение и размытость, так как нет повторного масштабирования на Canvas.
    private fun renderViewportStandaloneHighQuality(): Boolean {
        val iv = imageView as? ZoomableImageView ?: return false
        val baseBmp = renderedBitmap ?: return false
        val cache = cachePdfFile ?: return false

        val m: Matrix = iv.getImageMatrixCopy()
        val inv = Matrix()
        if (!m.invert(inv)) return false
        val pts = floatArrayOf(0f, 0f, iv.width.toFloat(), iv.height.toFloat())
        inv.mapPoints(pts)
        val leftBmp = kotlin.math.min(pts[0], pts[2]).coerceAtLeast(0f)
        val topBmp = kotlin.math.min(pts[1], pts[3]).coerceAtLeast(0f)
        val rightBmp = kotlin.math.max(pts[0], pts[2]).coerceAtMost(baseBmp.width.toFloat())
        val bottomBmp = kotlin.math.max(pts[1], pts[3]).coerceAtMost(baseBmp.height.toFloat())
        val regionWbmp = (rightBmp - leftBmp).coerceAtLeast(1f)
        val regionHbmp = (bottomBmp - topBmp).coerceAtLeast(1f)
        if (regionWbmp < 16f || regionHbmp < 16f) return false

        // Переведём в координаты страницы (PDF unit в пикселях PdfRenderer)
        val leftPage = leftBmp / pdfToBitmapScale
        val topPage = topBmp / pdfToBitmapScale
        val rightPage = rightBmp / pdfToBitmapScale
        val bottomPage = bottomBmp / pdfToBitmapScale
        val pageW = (rightPage - leftPage).toFloat().coerceAtLeast(1f)
        val pageH = (bottomPage - topPage).toFloat().coerceAtLeast(1f)

        // Итоговый тайл равен размеру вью — без последующего масштабирования
        val outW = iv.width.coerceAtLeast(1)
        val outH = iv.height.coerceAtLeast(1)
        val pfd = android.os.ParcelFileDescriptor.open(cache, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = android.graphics.pdf.PdfRenderer(pfd)
        try {
            val page = renderer.openPage(lastPageIndex)
            val tile = android.graphics.Bitmap.createBitmap(outW, outH, android.graphics.Bitmap.Config.ARGB_8888)
            val matrix = Matrix()
            val scaleX = outW / pageW
            val scaleY = outH / pageH
            matrix.setScale(scaleX, scaleY)
            matrix.postTranslate(-leftPage * scaleX, -topPage * scaleY)
            page.render(tile, null, matrix, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            page.close()

            // Подменяем изображение полностью. Масштаб/центр сбрасываем на 1:1
            renderedBitmap = tile
            imageView.setImageBitmap(tile)
            (imageView as? ZoomableImageView)?.resetMatrix()
            isViewportPinned = true
            maxQualityButton?.visibility = android.view.View.GONE
            restoreFullButton?.visibility = android.view.View.VISIBLE
            // Установим маркер и комментарий в pinned-режиме (по координатам страницы)
            val designation = intent.getStringExtra("designation")
            val ivZoom = imageView as? ZoomableImageView
            if (designation != null && ivZoom != null) {
                val coords = markers[currentPdfName]?.get(designation)
                if (coords != null) {
                    val overlay = findViewById<MarkerOverlayView>(R.id.markerOverlay)
                    overlay?.setMarkerInPageUnitsWithViewport(
                        pageX = coords.x.toFloat(),
                        pageY = coords.y.toFloat(),
                        pageW = coords.width.toFloat(),
                        pageH = coords.height.toFloat(),
                        leftPage = leftPage,
                        topPage = topPage,
                        scaleX = scaleX,
                        scaleY = scaleY,
                        currentImageMatrix = ivZoom.getImageMatrixCopy(),
                        label = designation,
                        color = parseMarkerColor(coords),
                        comment = coords.label
                    )
                }
            }
            return true
        } finally {
            try { renderer.close() } catch (_: Throwable) {}
            try { pfd.close() } catch (_: Throwable) {}
        }
    }

    private fun restoreFullScheme() {
        val cache = cachePdfFile ?: return
        val iv = imageView as? ZoomableImageView ?: return
        val pfd = android.os.ParcelFileDescriptor.open(cache, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = android.graphics.pdf.PdfRenderer(pfd)
        try {
            val page = renderer.openPage(lastPageIndex)
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
            var bmp: android.graphics.Bitmap? = null
            var attempts = 0
            while (attempts < 5 && bmp == null) {
                val desiredW = kotlin.math.max(1, (pageW * scale).toInt())
                val desiredH = kotlin.math.max(1, (pageH * scale).toInt())
                val dims = clampDimsByMemoryBudget(desiredW, desiredH, 1, 1)
                val bw = dims.first
                val bh = dims.second
                try {
                    bmp = android.graphics.Bitmap.createBitmap(bw, bh, android.graphics.Bitmap.Config.ARGB_8888)
                } catch (_: Throwable) { bmp = null }
                if (bmp == null) { scale *= 0.75f; attempts++ }
            }
            if (bmp == null) return
            page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderedBitmap = bmp
            imageView.setImageBitmap(bmp)
            imageView.post { updateMaxQualityButtonVisibility() }
            (imageView as? ZoomableImageView)?.resetMatrix()
            pdfToBitmapScale = bmp.width / pageW.toFloat()
            isViewportPinned = false
            restoreFullButton?.visibility = android.view.View.GONE
            // Сбросим маркер при возврате к полной схеме (или перерисуем, если есть designation)
            val ivZoom = imageView as? ZoomableImageView
            val designation = intent.getStringExtra("designation")
            if (designation != null && ivZoom != null) {
                val coords = markers[currentPdfName]?.get(designation)
                if (coords != null) {
                    val overlay = findViewById<MarkerOverlayView>(R.id.markerOverlay)
                    overlay?.setMarkerInPdfUnits(
                        coords.x.toFloat(),
                        coords.y.toFloat(),
                        coords.width.toFloat(),
                        coords.height.toFloat(),
                        pdfToBitmapScale,
                        ivZoom.getImageMatrixCopy(),
                        label = designation,
                        color = parseMarkerColor(coords),
                        comment = coords.label
                    )
                } else {
                    findViewById<MarkerOverlayView>(R.id.markerOverlay)?.clearMarker()
                }
            } else {
                findViewById<MarkerOverlayView>(R.id.markerOverlay)?.clearMarker()
            }
        } finally {
            try { renderer.close() } catch (_: Throwable) {}
            try { pfd.close() } catch (_: Throwable) {}
        }
    }

    // Центрирование и масштабирование по ArmatureCoords (старый формат)
    private fun focusOn(coords: ArmatureCoords) {
        val iv = imageView as? ZoomableImageView ?: return
        val bmp = renderedBitmap ?: return
        // Масштаб строго из JSON (с ограничением)
        val scale = coords.zoom.toFloat().coerceIn(0.2f, 20f)
        // Интерпретируем coords.x, coords.y как центр маркера
        val centerXbmp = (coords.x * pdfToBitmapScale).toFloat()
        val centerYbmp = (coords.y * pdfToBitmapScale).toFloat()
        iv.setScaleAndCenter(scale, centerXbmp, centerYbmp)
    }
    
    // Центрирование и масштабирование по ArmatureMarker (новый формат)
    private fun focusOnNewMarker(marker: ArmatureMarker) {
        val iv = imageView as? ZoomableImageView ?: return
        val bmp = renderedBitmap ?: return
        // Масштаб строго из JSON (с ограничением)
        val scale = marker.zoom.toFloat().coerceIn(0.2f, 20f)
        // Интерпретируем marker.x, marker.y как центр маркера
        val centerXbmp = (marker.x * pdfToBitmapScale).toFloat()
        val centerYbmp = (marker.y * pdfToBitmapScale).toFloat()
        iv.setScaleAndCenter(scale, centerXbmp, centerYbmp)
    }

    private fun parseMarkerColor(coords: ArmatureCoords): Int {
        return try {
            val mt = coords.marker_type
            if (mt != null && mt.contains("#")) {
                val hex = mt.substringAfter('#').substringBefore(':')
                android.graphics.Color.parseColor("#" + hex)
            } else {
                android.graphics.Color.RED
            }
        } catch (_: Throwable) {
            android.graphics.Color.RED
        }
    }

    private fun adjustZoom(factor: Float) {
        if (imageView is ZoomableImageView) {
            val iv = imageView as ZoomableImageView
            iv.post { iv.applyScale(factor) }
        }
    }
    
    private fun buildMarkersInfo(designation: String?): String {
        // Используем текущий открытый PDF, а не жёстко заданное имя.
        val pdfName = currentPdfName ?: return "Метки не найдены"
        val pdfMarkers = markers[pdfName] ?: return "Метки не найдены"
        
        val info = StringBuilder("=== МЕТКИ АРМАТУРЫ ===\n")
        
        for ((markerDesignation, coords) in pdfMarkers) {
            val isSelected = designation == markerDesignation
            val prefix = if (isSelected) ">>> " else "    "
            info.append("$prefix$markerDesignation: x=${coords.x}, y=${coords.y}, страница=${coords.page}\n")
        }
        
        if (designation != null) {
            info.append("\n>>> ВЫБРАНА АРМАТУРА: $designation <<<\n")
        }
        
        return info.toString()
    }
} 