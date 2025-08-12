package org.example.pult.android

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.example.pult.ExcelDataManager
import org.example.pult.RowDataDynamic

/**
 * A simple [Fragment] subclass that displays data from an Excel file in a RecyclerView.
 */
class DataFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var dataAdapter: DataAdapter
    private val excelDataManager = ExcelDataManager(AndroidExcelDataService())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_data, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)

        // For this example, we'll use dummy data.
        // In a real app, you would get this from a file selected by the user.
        val dummyHeaders = listOf("Column 1", "Column 2", "Column 3")
        val dummyData = listOf(
            RowDataDynamic(mapOf("Column 1" to "Value A", "Column 2" to "Value B", "Column 3" to "Value C")),
            RowDataDynamic(mapOf("Column 1" to "Value D", "Column 2" to "Value E", "Column 3" to "Value F"))
        )

        dataAdapter = DataAdapter(dummyHeaders, dummyData)
        recyclerView.adapter = dataAdapter
    }
}