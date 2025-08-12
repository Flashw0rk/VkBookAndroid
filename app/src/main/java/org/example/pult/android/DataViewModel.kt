package org.example.pult.android

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.example.pult.ExcelDataManager
import org.example.pult.RowDataDynamic
import java.io.InputStream

/**
 * ViewModel для DataFragment.
 * Отвечает за логику загрузки данных из Excel и предоставление их во Fragment.
 */
class DataViewModel : ViewModel() {

    private val excelDataManager = ExcelDataManager(AndroidExcelDataService())

    // LiveData для хранения данных таблицы.
    private val _excelData = MutableLiveData<List<RowDataDynamic>>()
    val excelData: LiveData<List<RowDataDynamic>> = _excelData

    // LiveData для хранения заголовков таблицы.
    private val _headers = MutableLiveData<List<String>>()
    val headers: LiveData<List<String>> = _headers

    /**
     * Загружает данные из Excel-файла.
     * Запускается в фоновом потоке, чтобы не блокировать UI.
     * @param inputStream Поток данных из выбранного пользователем файла.
     * @param sheetName Имя листа Excel.
     */
    fun loadExcelData(inputStream: InputStream, sheetName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Читаем заголовки и данные с помощью нашего ExcelDataManager.
                val headersList = excelDataManager.readHeaders(inputStream, sheetName)
                val dataList = excelDataManager.readAllRows(inputStream, sheetName)

                // Обновляем LiveData в главном потоке.
                _headers.postValue(headersList)
                _excelData.postValue(dataList)

                // После чтения потока его необходимо закрыть
                inputStream.close()
            } catch (e: Exception) {
                // В случае ошибки, вы можете отправить ее в LiveData или Logcat.
                e.printStackTrace()
            }
        }
    }
}