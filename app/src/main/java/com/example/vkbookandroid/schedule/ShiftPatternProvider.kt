package com.example.vkbookandroid.schedule

/**
 * Класс для предоставления паттернов смен
 * Инкапсулирует данные графиков смен и логику их сдвига
 */
class ShiftPatternProvider {
    
    // БАЗОВЫЙ паттерн смены 1 (10 элементов) - основа для всех смен
    private val basePattern = arrayOf("3", "2", "4", "1", "Вх", "4", "1", "3", "2", "Вх")
    
    // Начальные позиции для каждой смены в базовом паттерне
    // Базовый паттерн: 3, 2, 4, 1, Вх, 4, 1, 3, 2, Вх (индексы 0-9)
    private val shiftStartPositions = arrayOf(
        0,  // Смена 1: начинается с позиции 0 → "3,2,4,1,Вх,4,1,3,2,Вх,..."
        2,  // Смена 2: начинается с позиции 2 → "4,1,Вх,4,1,3,2,Вх,3,2,..."
        1,  // Смена 3: начинается с позиции 1 → "2,Вх,3,2,4,1,Вх,4,1,3,..."
        6,  // Смена 4: начинается с позиции 6 → "1,3,2,Вх,3,2,4,1,Вх,4,..." (с позиции "1" на индексе 6)
        4   // Смена 5: начинается с позиции 4 → "Вх,4,1,3,2,Вх,3,2,4,1,..."
    )
    
    private val shiftNames = arrayOf("Смена 1", "Смена 2", "Смена 3", "Смена 4", "Смена 5")
    
    companion object {
        const val PATTERN_SIZE = 36
        const val SHIFT_COUNT = 5
    }
    
    /**
     * Возвращает название смены по индексу
     * @param shiftIndex Индекс смены (0-4)
     */
    fun getShiftName(shiftIndex: Int): String {
        require(shiftIndex in 0 until SHIFT_COUNT) { "Invalid shift index: $shiftIndex" }
        return shiftNames[shiftIndex]
    }
    
    /**
     * Возвращает все названия смен
     */
    fun getAllShiftNames(): Array<String> = shiftNames
    
    /**
     * Возвращает паттерн смены с учетом сдвига года
     * @param shiftIndex Индекс смены (0-4)
     * @param yearShiftOffset Сдвиг года (0 = без сдвига)
     * @return Массив значений смены (36 элементов)
     */
    fun getShiftPattern(shiftIndex: Int, yearShiftOffset: Int): Array<String> {
        require(shiftIndex in 0 until SHIFT_COUNT) { "Invalid shift index: $shiftIndex" }
        
        // Вычисляем начальную позицию смены с учетом сдвига года
        val shiftStartPos = shiftStartPositions[shiftIndex]
        val effectiveStartPos = (shiftStartPos + yearShiftOffset) % basePattern.size
        
        // ПРАВИЛЬНАЯ ЛОГИКА: Генерируем 36 элементов из базового 10-элементного паттерна
        // Паттерн циклически повторяется (период = 10)
        val result = Array(PATTERN_SIZE) { index ->
            val positionInBase = (effectiveStartPos + index) % basePattern.size
            basePattern[positionInBase]
        }
        
        return result
    }
    
    /**
     * Возвращает исходный (несдвинутый) паттерн смены
     * @param shiftIndex Индекс смены (0-4)
     */
    fun getOriginalPattern(shiftIndex: Int): Array<String> {
        require(shiftIndex in 0 until SHIFT_COUNT) { "Invalid shift index: $shiftIndex" }
        return getShiftPattern(shiftIndex, 0)  // Паттерн без сдвига
    }
}


