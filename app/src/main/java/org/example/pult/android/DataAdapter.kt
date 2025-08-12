package org.example.pult.android

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.example.pult.RowDataDynamic

class DataAdapter(
    private val headers: List<String>,
    private val data: List<RowDataDynamic>
) : RecyclerView.Adapter<DataAdapter.RowViewHolder>() {

    class RowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val rowLayout: LinearLayout = itemView.findViewById(R.id.rowLayout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_row, parent, false)
        return RowViewHolder(view)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        val rowData = data[position]
        holder.rowLayout.removeAllViews() // Очищаем старые View

        for (header in headers) {
            val textView = TextView(holder.itemView.context)
            textView.text = rowData.getProperty(header) ?: ""
            holder.rowLayout.addView(textView)
        }
    }

    override fun getItemCount(): Int = data.size
}
