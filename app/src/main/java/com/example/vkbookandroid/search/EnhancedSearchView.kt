package com.example.vkbookandroid.search

import android.content.Context
import android.speech.RecognizerIntent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.vkbookandroid.R

/**
 * Улучшенный SearchView с индикаторами загрузки, счетчиком результатов,
 * историей поиска и поддержкой голосового поиска
 */
class EnhancedSearchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    
    // UI компоненты
    private val searchView: SearchView
    private val progressBar: ProgressBar
    private val resultsCounter: TextView
    private val voiceSearchButton: ImageButton
    private val historyContainer: LinearLayout
    private val historyRecyclerView: RecyclerView
    private val clearHistoryButton: Button
    
    // Слушатели событий
    private var onVoiceSearchListener: (() -> Unit)? = null
    private var onHistoryItemClickListener: ((String) -> Unit)? = null
    private var onClearHistoryListener: (() -> Unit)? = null
    
    // Адаптер истории поиска
    private var historyAdapter: SearchHistoryAdapter? = null
    
    init {
        // Загружаем layout
        LayoutInflater.from(context).inflate(R.layout.enhanced_search_view, this, true)
        
        // Инициализируем компоненты
        searchView = findViewById(R.id.search_view)
        progressBar = findViewById(R.id.progress_bar)
        resultsCounter = findViewById(R.id.results_counter)
        voiceSearchButton = findViewById(R.id.voice_search_button)
        historyContainer = findViewById(R.id.history_container)
        historyRecyclerView = findViewById(R.id.history_recycler_view)
        clearHistoryButton = findViewById(R.id.clear_history_button)
        
        setupViews()
    }
    
    private fun setupViews() {
        // Настройка кнопки голосового поиска
        voiceSearchButton.setOnClickListener {
            onVoiceSearchListener?.invoke()
        }
        
        // Настройка SearchView
        searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
            historyContainer.visibility = if (hasFocus && hasHistory()) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
        
        // Настройка RecyclerView для истории
        historyRecyclerView.layoutManager = LinearLayoutManager(context)
        
        // Настройка кнопки очистки истории
        clearHistoryButton.setOnClickListener {
            onClearHistoryListener?.invoke()
            historyContainer.visibility = View.GONE
        }
        
        // Скрываем индикаторы по умолчанию
        progressBar.visibility = View.GONE
        resultsCounter.visibility = View.GONE
        historyContainer.visibility = View.GONE
    }
    
    /**
     * Показать индикатор поиска
     */
    fun showSearching() {
        progressBar.visibility = View.VISIBLE
        resultsCounter.visibility = View.VISIBLE
        resultsCounter.text = "Поиск..."
        historyContainer.visibility = View.GONE
    }
    
    /**
     * Показать результаты поиска
     */
    fun showResults(count: Int, searchTime: Long, fromCache: Boolean = false) {
        progressBar.visibility = View.GONE
        resultsCounter.visibility = View.VISIBLE
        
        val cacheIndicator = if (fromCache) " (кэш)" else ""
        resultsCounter.text = when {
            count == 0 -> "Ничего не найдено"
            searchTime < 10 -> "Найдено: $count$cacheIndicator"
            else -> "Найдено: $count (${searchTime}мс)$cacheIndicator"
        }
        
        historyContainer.visibility = View.GONE
    }
    
    /**
     * Скрыть все индикаторы
     */
    fun hideIndicators() {
        progressBar.visibility = View.GONE
        resultsCounter.visibility = View.GONE
        historyContainer.visibility = View.GONE
    }
    
    /**
     * Установить историю поиска
     */
    fun setSearchHistory(history: List<String>) {
        if (history.isEmpty()) {
            historyContainer.visibility = View.GONE
            return
        }
        
        historyAdapter = SearchHistoryAdapter(history) { query ->
            searchView.setQuery(query, false)
            onHistoryItemClickListener?.invoke(query)
            historyContainer.visibility = View.GONE
        }
        historyRecyclerView.adapter = historyAdapter
        
        // Показываем историю если SearchView в фокусе
        if (searchView.hasFocus()) {
            historyContainer.visibility = View.VISIBLE
        }
    }
    
    /**
     * Проверка наличия истории
     */
    private fun hasHistory(): Boolean {
        return historyAdapter?.itemCount ?: 0 > 0
    }
    
    /**
     * Установить слушатель голосового поиска
     */
    fun setOnVoiceSearchListener(listener: () -> Unit) {
        onVoiceSearchListener = listener
    }
    
    /**
     * Установить слушатель клика по элементу истории
     */
    fun setOnHistoryItemClickListener(listener: (String) -> Unit) {
        onHistoryItemClickListener = listener
    }
    
    /**
     * Установить слушатель очистки истории
     */
    fun setOnClearHistoryListener(listener: () -> Unit) {
        onClearHistoryListener = listener
    }
    
    /**
     * Установить слушатель изменения текста поиска
     */
    fun setOnQueryTextListener(listener: SearchView.OnQueryTextListener) {
        searchView.setOnQueryTextListener(listener)
    }
    
    /**
     * Установить слушатель закрытия поиска
     */
    fun setOnCloseListener(listener: SearchView.OnCloseListener) {
        searchView.setOnCloseListener(listener)
    }
    
    /**
     * Получить текущий запрос
     */
    fun getQuery(): CharSequence? = searchView.query
    
    /**
     * Установить запрос
     */
    fun setQuery(query: CharSequence?, submit: Boolean) {
        searchView.setQuery(query, submit)
    }
    
    /**
     * Установить подсказку
     */
    fun setQueryHint(hint: CharSequence?) {
        searchView.queryHint = hint
    }
    
    /**
     * Показать ошибку поиска
     */
    fun showError(message: String) {
        progressBar.visibility = View.GONE
        resultsCounter.visibility = View.VISIBLE
        resultsCounter.text = "Ошибка: $message"
        historyContainer.visibility = View.GONE
    }
    
    /**
     * Включить/выключить голосовой поиск
     */
    fun setVoiceSearchEnabled(enabled: Boolean) {
        voiceSearchButton.visibility = if (enabled) View.VISIBLE else View.GONE
    }
}

/**
 * Адаптер для истории поиска
 */
class SearchHistoryAdapter(
    private val history: List<String>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<SearchHistoryAdapter.ViewHolder>() {
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(android.R.id.text1)
        val deleteButton: ImageButton = view.findViewById(R.id.delete_button)
    }
    
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.search_history_item, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val query = history[position]
        holder.textView.text = query
        
        holder.itemView.setOnClickListener {
            onItemClick(query)
        }
        
        holder.deleteButton.setOnClickListener {
            // TODO: Реализовать удаление отдельного элемента истории
        }
    }
    
    override fun getItemCount(): Int = history.size
}


